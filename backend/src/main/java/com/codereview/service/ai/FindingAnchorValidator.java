package com.codereview.service.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Validates AI finding anchors against the diff that was reviewed.
 *
 * LLMs frequently hallucinate line numbers (and occasionally file paths). An inline
 * finding pinned to the wrong line is worse than a file-level finding, so every
 * finding is checked against the set of line numbers that actually appear in the
 * diff's hunks (added and context lines, new-file numbering):
 *
 * <ul>
 *   <li><b>Anchored</b> — file and line both exist in the diff: kept as-is.</li>
 *   <li><b>Snapped</b> — line is within {@value #SNAP_TOLERANCE} of a real diff line
 *       (off-by-one/two is the most common hallucination): moved to the nearest
 *       real line.</li>
 *   <li><b>Demoted</b> — file not in the diff, or line nowhere near a hunk: the
 *       line anchor is dropped so the finding renders at file level instead of
 *       pointing at the wrong code.</li>
 * </ul>
 *
 * The demoted count is persisted per review — a direct, queryable measure of the
 * model's anchor-hallucination rate.
 */
@Component
public class FindingAnchorValidator {

    /** Max distance (in new-file lines) a finding may be moved to the nearest real diff line. */
    private static final int SNAP_TOLERANCE = 3;

    public record Result(List<AiReviewResult.Finding> findings, int snapped, int demoted) {
    }

    public Result validate(String diff, List<AiReviewResult.Finding> findings) {
        Map<String, NavigableSet<Integer>> anchors = parseAnchors(diff);

        List<AiReviewResult.Finding> validated = new ArrayList<>(findings.size());
        int snapped = 0;
        int demoted = 0;

        for (AiReviewResult.Finding finding : findings) {
            NavigableSet<Integer> lines = resolveFile(anchors, finding.filePath());
            if (lines == null) {
                // File not part of the diff at all — drop the line anchor.
                if (finding.lineNumber() != null) {
                    demoted++;
                }
                validated.add(withLine(finding, null));
                continue;
            }
            Integer line = finding.lineNumber();
            if (line == null || lines.contains(line)) {
                validated.add(finding);
                continue;
            }
            Integer nearest = nearest(lines, line);
            if (nearest != null && Math.abs(nearest - line) <= SNAP_TOLERANCE) {
                snapped++;
                validated.add(withLine(finding, nearest));
            } else {
                demoted++;
                validated.add(withLine(finding, null));
            }
        }
        return new Result(validated, snapped, demoted);
    }

    /** New-file line numbers present in the diff (added + context lines), per file. */
    private Map<String, NavigableSet<Integer>> parseAnchors(String diff) {
        Map<String, NavigableSet<Integer>> anchors = new HashMap<>();
        NavigableSet<Integer> current = null;
        int newLine = 0;

        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("+++ ")) {
                current = anchors.computeIfAbsent(normalize(line.substring(4).trim()),
                        k -> new TreeSet<>());
                newLine = 0;
            } else if (line.startsWith("@@")) {
                newLine = parseHunkStart(line);
            } else if (current != null && newLine > 0) {
                if (line.startsWith("+")) {
                    current.add(newLine++);
                } else if (line.startsWith("-") || line.startsWith("\\")) {
                    // old-file or "no newline" marker: does not advance new-file numbering
                } else {
                    current.add(newLine++); // context line — a valid anchor too
                }
            }
        }
        return anchors;
    }

    /** Exact match after normalization, then unique suffix match ("UserDao.java" → full path). */
    private NavigableSet<Integer> resolveFile(Map<String, NavigableSet<Integer>> anchors, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        String normalized = normalize(filePath);
        NavigableSet<Integer> exact = anchors.get(normalized);
        if (exact != null) {
            return exact;
        }
        NavigableSet<Integer> match = null;
        for (Map.Entry<String, NavigableSet<Integer>> e : anchors.entrySet()) {
            if (e.getKey().endsWith("/" + normalized) || normalized.endsWith("/" + e.getKey())) {
                if (match != null) {
                    return null; // ambiguous — refuse to guess
                }
                match = e.getValue();
            }
        }
        return match;
    }

    private static String normalize(String path) {
        String p = path.trim().replace('\\', '/');
        if (p.startsWith("a/") || p.startsWith("b/")) {
            p = p.substring(2);
        }
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p;
    }

    private static Integer nearest(NavigableSet<Integer> lines, int line) {
        Integer floor = lines.floor(line);
        Integer ceiling = lines.ceiling(line);
        if (floor == null) {
            return ceiling;
        }
        if (ceiling == null) {
            return floor;
        }
        return line - floor <= ceiling - line ? floor : ceiling;
    }

    private static AiReviewResult.Finding withLine(AiReviewResult.Finding f, Integer line) {
        return new AiReviewResult.Finding(f.filePath(), line, f.category(), f.severity(),
                f.message(), f.suggestion());
    }

    private static int parseHunkStart(String hunk) {
        try {
            int plus = hunk.indexOf('+');
            return Integer.parseInt(hunk.substring(plus + 1).split("[ ,]")[0]);
        } catch (Exception e) {
            return 0;
        }
    }
}
