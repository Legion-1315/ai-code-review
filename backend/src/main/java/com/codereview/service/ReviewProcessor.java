package com.codereview.service;

import com.codereview.domain.Review;
import com.codereview.domain.ReviewIssue;
import com.codereview.domain.ReviewStatus;
import com.codereview.repository.ReviewRepository;
import com.codereview.service.ai.AiReviewResult;
import com.codereview.service.ai.AiReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Runs the AI review asynchronously on a dedicated thread pool so the HTTP request that
 * submitted the review (or the webhook intake) returns immediately.
 */
@Service
public class ReviewProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReviewProcessor.class);

    private final ReviewRepository reviews;
    private final AiReviewService aiReviewService;

    public ReviewProcessor(ReviewRepository reviews, AiReviewService aiReviewService) {
        this.reviews = reviews;
        this.aiReviewService = aiReviewService;
    }

    @Async("reviewExecutor")
    @Transactional
    public void process(Long reviewId) {
        Review review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            log.warn("Review {} no longer exists; skipping.", reviewId);
            return;
        }

        review.setStatus(ReviewStatus.IN_PROGRESS);
        reviews.saveAndFlush(review);

        try {
            AiReviewResult result = aiReviewService.review(
                    review.getPullRequest().getTitle(),
                    review.getPullRequest().getDiff());

            review.getIssues().clear();
            for (AiReviewResult.Finding finding : result.findings()) {
                ReviewIssue issue = new ReviewIssue();
                issue.setFilePath(finding.filePath());
                issue.setLineNumber(finding.lineNumber());
                issue.setCategory(finding.category());
                issue.setSeverity(finding.severity());
                issue.setMessage(finding.message());
                issue.setSuggestion(finding.suggestion());
                review.addIssue(issue);
            }

            review.setOverallScore(result.overallScore());
            review.setSummary(result.summary());
            review.setUsedRealAi(result.usedRealAi());
            review.setStatus(ReviewStatus.COMPLETED);
            review.setCompletedAt(Instant.now());
            reviews.save(review);
            log.info("Review {} completed with score {}.", reviewId, result.overallScore());
        } catch (Exception e) {
            log.error("Review {} failed: {}", reviewId, e.getMessage(), e);
            review.setStatus(ReviewStatus.FAILED);
            review.setErrorMessage(e.getMessage());
            review.setCompletedAt(Instant.now());
            reviews.save(review);
        }
    }
}
