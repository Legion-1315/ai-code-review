package com.codereview.dto;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.PullRequest;
import com.codereview.domain.Review;
import com.codereview.domain.ReviewIssue;
import com.codereview.domain.ReviewStatus;
import com.codereview.domain.Severity;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * Submit code for review. {@code repositoryId} is optional — when omitted an ad-hoc
     * repository ("manual-reviews") is used, so the feature is demoable without GitHub.
     */
    public record SubmitReviewRequest(
            Long repositoryId,
            @NotBlank String title,
            String author,
            @NotBlank String diff) {
    }

    public record IssueResponse(
            Long id,
            String filePath,
            Integer lineNumber,
            IssueCategory category,
            Severity severity,
            String message,
            String suggestion) {

        public static IssueResponse from(ReviewIssue issue) {
            return new IssueResponse(
                    issue.getId(),
                    issue.getFilePath(),
                    issue.getLineNumber(),
                    issue.getCategory(),
                    issue.getSeverity(),
                    issue.getMessage(),
                    issue.getSuggestion());
        }
    }

    /** Lightweight row for list views. */
    public record ReviewSummaryResponse(
            Long id,
            String prTitle,
            Integer prNumber,
            String author,
            String repositoryName,
            ReviewStatus status,
            Integer overallScore,
            int issueCount,
            Instant createdAt) {

        public static ReviewSummaryResponse from(Review review) {
            PullRequest pr = review.getPullRequest();
            return new ReviewSummaryResponse(
                    review.getId(),
                    pr.getTitle(),
                    pr.getPrNumber(),
                    pr.getAuthor(),
                    pr.getRepository().getFullName(),
                    review.getStatus(),
                    review.getOverallScore(),
                    review.getIssues().size(),
                    review.getCreatedAt());
        }
    }

    /** Full detail including diff and all issues. */
    public record ReviewDetailResponse(
            Long id,
            String prTitle,
            Integer prNumber,
            String author,
            String repositoryName,
            String diff,
            ReviewStatus status,
            Integer overallScore,
            String summary,
            boolean usedRealAi,
            int unanchoredFindings,
            String errorMessage,
            List<IssueResponse> issues,
            Instant createdAt,
            Instant completedAt) {

        public static ReviewDetailResponse from(Review review) {
            PullRequest pr = review.getPullRequest();
            return new ReviewDetailResponse(
                    review.getId(),
                    pr.getTitle(),
                    pr.getPrNumber(),
                    pr.getAuthor(),
                    pr.getRepository().getFullName(),
                    pr.getDiff(),
                    review.getStatus(),
                    review.getOverallScore(),
                    review.getSummary(),
                    review.isUsedRealAi(),
                    review.getUnanchoredFindings(),
                    review.getErrorMessage(),
                    review.getIssues().stream().map(IssueResponse::from).toList(),
                    review.getCreatedAt(),
                    review.getCompletedAt());
        }
    }
}
