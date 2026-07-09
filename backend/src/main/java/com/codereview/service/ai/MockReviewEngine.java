package com.codereview.service.ai;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic, heuristic reviewer used when no Claude API key is configured (and as a
 * fallback if a live call fails). It scans added ('+') diff lines for a handful of common
 * smells so the application produces meaningful output end-to-end without external calls.
 */
@Component
public class MockReviewEngine {

    private record Rule(Pattern pattern, IssueCategory category, Severity severity,
                        String message, String suggestion) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(password|secret|api[_-]?key|token)\\s*=\\s*[\"'][^\"']+[\"']"),
                    IssueCategory.SECURITY, Severity.CRITICAL,
                    "Possible hardcoded secret or credential.",
                    "Move secrets to environment variables or a secrets manager; never commit them."),
            new Rule(Pattern.compile("(?i)(select|insert|update|delete)\\s+.*\\+\\s*\\w+"),
                    IssueCategory.SECURITY, Severity.HIGH,
                    "SQL appears to be built via string concatenation, risking SQL injection.",
                    "Use parameterized queries or a query builder/ORM."),
            new Rule(Pattern.compile("(?i)System\\.out\\.println|console\\.log|printStackTrace"),
                    IssueCategory.BEST_PRACTICE, Severity.LOW,
                    "Debug/console output left in code.",
                    "Use a structured logger at the appropriate level instead."),
            new Rule(Pattern.compile("(?i)\\bTODO\\b|\\bFIXME\\b"),
                    IssueCategory.CODE_QUALITY, Severity.INFO,
                    "Unresolved TODO/FIXME marker.",
                    "Resolve before merge or link a tracking issue."),
            new Rule(Pattern.compile("(?i)catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
                    IssueCategory.BEST_PRACTICE, Severity.MEDIUM,
                    "Empty catch block swallows exceptions.",
                    "Log or handle the exception, or document why it is safe to ignore."),
            new Rule(Pattern.compile("(?i)==\\s*null|!=\\s*null"),
                    IssueCategory.CODE_QUALITY, Severity.INFO,
                    "Manual null comparison; consider Optional or null-safety utilities.",
                    "Prefer Optional or Objects.requireNonNull where appropriate.")
    );

    public AiReviewResult review(String prTitle, String diff) {
        List<AiReviewResult.Finding> findings = new ArrayList<>();
        String currentFile = "unknown";
        int newLineNumber = 0;
        boolean sawAddedLine = false;
        boolean sawTest = false;

        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("+++ ")) {
                currentFile = stripFileMarker(line);
                newLineNumber = 0;
                continue;
            }
            if (line.startsWith("@@")) {
                newLineNumber = parseHunkStart(line);
                continue;
            }
            if (line.startsWith("+") && !line.startsWith("+++")) {
                sawAddedLine = true;
                String content = line.substring(1);
                if (currentFile.toLowerCase().contains("test")) {
                    sawTest = true;
                }
                for (Rule rule : RULES) {
                    if (rule.pattern().matcher(content).find()) {
                        findings.add(new AiReviewResult.Finding(
                                currentFile, newLineNumber, rule.category(),
                                rule.severity(), rule.message(), rule.suggestion()));
                    }
                }
                newLineNumber++;
            } else if (!line.startsWith("-")) {
                newLineNumber++;
            }
        }

        if (sawAddedLine && !sawTest) {
            findings.add(new AiReviewResult.Finding(
                    currentFile, null, IssueCategory.TEST_COVERAGE, Severity.MEDIUM,
                    "New or changed code does not appear to include corresponding tests.",
                    "Add unit tests covering the new behavior."));
        }

        int score = computeScore(findings);
        String summary = buildSummary(prTitle, findings, score);
        return new AiReviewResult(score, summary, false, findings);
    }

    private int computeScore(List<AiReviewResult.Finding> findings) {
        int penalty = 0;
        for (AiReviewResult.Finding f : findings) {
            penalty += switch (f.severity()) {
                case CRITICAL -> 25;
                case HIGH -> 15;
                case MEDIUM -> 8;
                case LOW -> 4;
                case INFO -> 1;
            };
        }
        return Math.max(0, 100 - penalty);
    }

    private String buildSummary(String prTitle, List<AiReviewResult.Finding> findings, int score) {
        if (findings.isEmpty()) {
            return "No issues detected by the heuristic reviewer for \"" + prTitle
                    + "\". Score: " + score + "/100.";
        }
        long critical = findings.stream().filter(f -> f.severity() == Severity.CRITICAL).count();
        long high = findings.stream().filter(f -> f.severity() == Severity.HIGH).count();
        return "Heuristic review of \"" + prTitle + "\" found " + findings.size()
                + " issue(s) (" + critical + " critical, " + high + " high). "
                + "Overall score: " + score + "/100. "
                + "Set ANTHROPIC_API_KEY to enable full AI review.";
    }

    private String stripFileMarker(String line) {
        String path = line.substring(4).trim();
        if (path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    private int parseHunkStart(String hunk) {
        // Format: @@ -a,b +c,d @@
        try {
            int plus = hunk.indexOf('+');
            String after = hunk.substring(plus + 1);
            String number = after.split("[ ,]")[0];
            return Integer.parseInt(number);
        } catch (Exception e) {
            return 0;
        }
    }
}
