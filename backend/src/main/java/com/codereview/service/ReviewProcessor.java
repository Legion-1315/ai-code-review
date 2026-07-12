package com.codereview.service;

import com.codereview.domain.Review;
import com.codereview.domain.ReviewIssue;
import com.codereview.domain.ReviewStatus;
import com.codereview.repository.ReviewRepository;
import com.codereview.service.ai.AiReviewResult;
import com.codereview.service.ai.AiReviewService;
import com.codereview.service.ai.FindingAnchorValidator;
import com.codereview.service.context.RepoContext;
import com.codereview.service.context.RepoContextService;
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
    private final FindingAnchorValidator anchorValidator;
    private final RepoContextService repoContextService;

    public ReviewProcessor(ReviewRepository reviews, AiReviewService aiReviewService,
                           FindingAnchorValidator anchorValidator,
                           RepoContextService repoContextService) {
        this.reviews = reviews;
        this.aiReviewService = aiReviewService;
        this.anchorValidator = anchorValidator;
        this.repoContextService = repoContextService;
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
            // Repo context only exists for webhook-originated reviews (known repo + head SHA).
            RepoContext context = review.getPullRequest().getPrNumber() != null
                    ? repoContextService.fetch(
                            review.getPullRequest().getRepository().getFullName(),
                            review.getPullRequest().getHeadRef(),
                            review.getPullRequest().getDiff())
                    : RepoContext.empty();

            AiReviewResult result = aiReviewService.review(
                    review.getPullRequest().getTitle(),
                    review.getPullRequest().getDiff(),
                    context);

            // Guard against hallucinated anchors before persisting anything inline.
            FindingAnchorValidator.Result anchored = anchorValidator.validate(
                    review.getPullRequest().getDiff(), result.findings());
            if (anchored.snapped() > 0 || anchored.demoted() > 0) {
                log.info("Review {}: {} finding anchor(s) snapped, {} demoted to file level.",
                        reviewId, anchored.snapped(), anchored.demoted());
            }

            review.getIssues().clear();
            for (AiReviewResult.Finding finding : anchored.findings()) {
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
            review.setUnanchoredFindings(anchored.demoted());
            review.setContextFiles(context.files().size());
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
