package com.codereview.service.ai;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Parses the model's JSON response into an {@link AiReviewResult}, defensively. */
@Component
public class ReviewJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public AiReviewResult parse(String raw, boolean usedRealAi) {
        String json = extractJson(raw);
        try {
            JsonNode root = mapper.readTree(json);

            int score = clampScore(root.path("overallScore").asInt(0));
            String summary = root.path("summary").asText("No summary provided.");

            List<AiReviewResult.Finding> findings = new ArrayList<>();
            JsonNode findingsNode = root.path("findings");
            if (findingsNode.isArray()) {
                for (JsonNode f : findingsNode) {
                    findings.add(toFinding(f));
                }
            }
            return new AiReviewResult(score, summary, usedRealAi, findings);
        } catch (Exception e) {
            throw new AiResponseParseException("Failed to parse AI review JSON: " + e.getMessage(), e);
        }
    }

    private AiReviewResult.Finding toFinding(JsonNode f) {
        Integer line = f.hasNonNull("lineNumber") && f.get("lineNumber").isInt()
                ? f.get("lineNumber").asInt()
                : null;
        return new AiReviewResult.Finding(
                f.path("filePath").asText("unknown"),
                line,
                parseEnum(f.path("category").asText(), IssueCategory.class, IssueCategory.CODE_QUALITY),
                parseEnum(f.path("severity").asText(), Severity.class, Severity.INFO),
                f.path("message").asText(""),
                f.path("suggestion").asText(null));
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    /** Strips markdown fences / surrounding prose by isolating the outermost JSON object. */
    private String extractJson(String raw) {
        if (raw == null) {
            throw new AiResponseParseException("AI returned an empty response", null);
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new AiResponseParseException("No JSON object found in AI response", null);
        }
        return raw.substring(start, end + 1);
    }

    public static class AiResponseParseException extends RuntimeException {
        public AiResponseParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
