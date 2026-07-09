package com.codereview.service.ai;

import org.springframework.stereotype.Component;

/** Builds the system and user prompts for the review model. */
@Component
public class ReviewPromptFactory {

    /** Hard cap on diff size sent to the model to bound token usage. */
    private static final int MAX_DIFF_CHARS = 24_000;

    public String systemPrompt() {
        return """
                You are a senior staff software engineer performing a thorough, constructive code review.
                You review a unified diff and return findings as STRICT JSON only — no prose, no markdown fences.

                Evaluate the change across these categories:
                - SECURITY: injection, hardcoded secrets, auth flaws, unsafe deserialization, XSS, SSRF.
                - CODE_QUALITY: dead code, poor naming, duplication, overly long methods, missing null checks.
                - PERFORMANCE: N+1 queries, needless allocations, inefficient loops, missing indexes.
                - BEST_PRACTICE: SOLID violations, error handling, design-pattern misuse, missing logging.
                - TEST_COVERAGE: new logic that lacks corresponding tests.

                Reporting rules:
                - Report every issue you find, including low-confidence and low-severity ones. Coverage matters more than precision here.
                - Prefer specific, actionable findings tied to a file and (when determinable) a line number from the diff.
                - severity is one of: INFO, LOW, MEDIUM, HIGH, CRITICAL.
                - category is one of: SECURITY, CODE_QUALITY, PERFORMANCE, BEST_PRACTICE, TEST_COVERAGE.
                - overallScore is an integer 0-100 reflecting overall health (100 = excellent, 0 = severe problems).

                Respond with exactly this JSON shape and nothing else:
                {
                  "overallScore": <int 0-100>,
                  "summary": "<2-4 sentence summary>",
                  "findings": [
                    {
                      "filePath": "<path>",
                      "lineNumber": <int or null>,
                      "category": "<category>",
                      "severity": "<severity>",
                      "message": "<what is wrong and why>",
                      "suggestion": "<how to fix it>"
                    }
                  ]
                }
                """;
    }

    public String userPrompt(String prTitle, String diff) {
        String trimmed = diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n... [diff truncated]"
                : diff;
        return "Pull request title: " + prTitle + "\n\nUnified diff to review:\n\n" + trimmed;
    }
}
