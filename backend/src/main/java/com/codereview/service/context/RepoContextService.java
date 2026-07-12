package com.codereview.service.context;

import com.codereview.service.github.GitHubContentFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gathers repository context for webhook-originated reviews, so the model reviews the
 * diff with the surrounding code in view instead of hunks alone:
 *
 * <ol>
 *   <li>Extract the file paths touched by the diff.</li>
 *   <li>Fetch each touched file's full content at the PR's head commit
 *       (raw.githubusercontent.com).</li>
 *   <li>Resolve one level of same-repo imports: parse {@code import} statements from the
 *       fetched Java sources, match them against the repository tree listing
 *       (one {@code git/trees?recursive=1} call), and fetch a bounded number of those
 *       files too.</li>
 * </ol>
 *
 * Everything is budgeted ({@code ai.context.max-chars} total, per-file cap) and
 * best-effort: any failure yields a smaller or empty context, never a failed review.
 */
@Service
public class RepoContextService {

    private static final Logger log = LoggerFactory.getLogger(RepoContextService.class);

    private static final Pattern DIFF_FILE = Pattern.compile("^\\+\\+\\+ b/(.+)$", Pattern.MULTILINE);
    private static final Pattern JAVA_IMPORT =
            Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Set<String> SKIP_FILE_NAMES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "gradle.lockfile");
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "ico", "pdf", "jar", "zip", "min.js", "svg", "lock");

    private final GitHubContentFetcher github;
    private final ObjectMapper mapper = new ObjectMapper();

    private final boolean enabled;
    private final int maxTotalChars;
    private final int maxFileChars;
    private final int maxImportFiles;

    public RepoContextService(GitHubContentFetcher github,
                              @Value("${ai.context.enabled:true}") boolean enabled,
                              @Value("${ai.context.max-chars:48000}") int maxTotalChars,
                              @Value("${ai.context.max-file-chars:12000}") int maxFileChars,
                              @Value("${ai.context.max-import-files:4}") int maxImportFiles) {
        this.github = github;
        this.enabled = enabled;
        this.maxTotalChars = maxTotalChars;
        this.maxFileChars = maxFileChars;
        this.maxImportFiles = maxImportFiles;
    }

    /**
     * @param repoFullName e.g. {@code owner/repo}
     * @param ref          commit SHA or branch of the PR head; falls back to {@code HEAD}
     * @param diff         the unified diff under review
     */
    public RepoContext fetch(String repoFullName, String ref, String diff) {
        if (!enabled || !StringUtils.hasText(repoFullName) || !repoFullName.contains("/")) {
            return RepoContext.empty();
        }
        String resolvedRef = StringUtils.hasText(ref) ? ref : "HEAD";
        try {
            List<String> touched = touchedFiles(diff);
            if (touched.isEmpty()) {
                return RepoContext.empty();
            }

            List<RepoContext.ContextFile> files = new ArrayList<>();
            boolean truncated = false;
            int budget = maxTotalChars;

            for (String path : touched) {
                if (budget <= 0) {
                    truncated = true;
                    break;
                }
                Optional<String> content = fetchFile(repoFullName, resolvedRef, path);
                if (content.isEmpty()) {
                    continue;
                }
                String trimmed = cap(content.get(), Math.min(maxFileChars, budget));
                truncated |= trimmed.length() < content.get().length();
                budget -= trimmed.length();
                files.add(new RepoContext.ContextFile(path, trimmed, false));
            }

            // One level of same-repo imports, resolved against the tree listing.
            if (budget > 0 && maxImportFiles > 0) {
                List<String> treePaths = treePaths(repoFullName, resolvedRef);
                Set<String> alreadyFetched = new LinkedHashSet<>(touched);
                int importsFetched = 0;
                for (String importPath : resolveImports(files, treePaths)) {
                    if (importsFetched >= maxImportFiles || budget <= 0) {
                        truncated = true;
                        break;
                    }
                    if (!alreadyFetched.add(importPath)) {
                        continue;
                    }
                    Optional<String> content = fetchFile(repoFullName, resolvedRef, importPath);
                    if (content.isEmpty()) {
                        continue;
                    }
                    String trimmed = cap(content.get(), Math.min(maxFileChars, budget));
                    truncated |= trimmed.length() < content.get().length();
                    budget -= trimmed.length();
                    files.add(new RepoContext.ContextFile(importPath, trimmed, true));
                    importsFetched++;
                }
            }

            log.info("Repo context for {}@{}: {} file(s), {} chars{}", repoFullName, resolvedRef,
                    files.size(), maxTotalChars - budget, truncated ? " (truncated)" : "");
            return new RepoContext(repoFullName, resolvedRef, List.copyOf(files), truncated);
        } catch (Exception e) {
            log.warn("Repo context fetch for {} failed: {}", repoFullName, e.getMessage());
            return RepoContext.empty();
        }
    }

    /** Paths touched by the diff (new-file side), excluding deletions and binary-ish files. */
    List<String> touchedFiles(String diff) {
        List<String> paths = new ArrayList<>();
        Matcher m = DIFF_FILE.matcher(diff == null ? "" : diff);
        while (m.find()) {
            String path = m.group(1).trim();
            if (!"/dev/null".equals(path) && !shouldSkip(path) && !paths.contains(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** Java import statements in the fetched files, mapped to matching repository paths. */
    List<String> resolveImports(List<RepoContext.ContextFile> files, List<String> treePaths) {
        if (treePaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (RepoContext.ContextFile file : files) {
            if (!file.path().endsWith(".java")) {
                continue;
            }
            Matcher m = JAVA_IMPORT.matcher(file.content());
            while (m.find()) {
                String suffix = m.group(1).replace('.', '/') + ".java";
                for (String candidate : treePaths) {
                    if (candidate.endsWith("/" + suffix) || candidate.equals(suffix)) {
                        resolved.add(candidate);
                        break;
                    }
                }
            }
        }
        return List.copyOf(resolved);
    }

    private List<String> treePaths(String repoFullName, String ref) {
        String url = "https://api.github.com/repos/" + repoFullName + "/git/trees/" + ref + "?recursive=1";
        Optional<String> body = github.fetchText(url);
        if (body.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(body.get());
            List<String> paths = new ArrayList<>();
            for (JsonNode node : root.path("tree")) {
                if ("blob".equals(node.path("type").asText())) {
                    paths.add(node.path("path").asText());
                }
            }
            return paths;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Optional<String> fetchFile(String repoFullName, String ref, String path) {
        return github.fetchText(
                "https://raw.githubusercontent.com/" + repoFullName + "/" + ref + "/" + path);
    }

    private static boolean shouldSkip(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        if (SKIP_FILE_NAMES.contains(name)) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SKIP_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static String cap(String content, int max) {
        return content.length() <= max
                ? content
                : content.substring(0, Math.max(0, max)) + "\n... [truncated]";
    }
}
