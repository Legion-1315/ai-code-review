package com.codereview.service.ai;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.Severity;

import java.util.List;

/** Structured output of an AI review, independent of persistence concerns. */
public record AiReviewResult(
        int overallScore,
        String summary,
        boolean usedRealAi,
        List<Finding> findings) {

    public record Finding(
            String filePath,
            Integer lineNumber,
            IssueCategory category,
            Severity severity,
            String message,
            String suggestion) {
    }
}
