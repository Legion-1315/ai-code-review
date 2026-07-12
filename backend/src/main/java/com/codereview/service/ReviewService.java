package com.codereview.service;

import com.codereview.domain.GitRepository;
import com.codereview.domain.PullRequest;
import com.codereview.domain.Review;
import com.codereview.dto.ReviewDtos.ReviewDetailResponse;
import com.codereview.dto.ReviewDtos.ReviewSummaryResponse;
import com.codereview.dto.ReviewDtos.SubmitReviewRequest;
import com.codereview.exception.NotFoundException;
import com.codereview.repository.PullRequestRepository;
import com.codereview.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final PullRequestRepository pullRequests;
    private final RepositoryService repositoryService;
    private final ReviewProcessor reviewProcessor;

    public ReviewService(ReviewRepository reviews,
                         PullRequestRepository pullRequests,
                         RepositoryService repositoryService,
                         ReviewProcessor reviewProcessor) {
        this.reviews = reviews;
        this.pullRequests = pullRequests;
        this.repositoryService = repositoryService;
        this.reviewProcessor = reviewProcessor;
    }

    /**
     * Creates a pending review and schedules async processing. The async job is only fired
     * after the creating transaction commits, so the worker thread is guaranteed to see the row.
     */
    @Transactional
    public ReviewDetailResponse submit(Long ownerId, SubmitReviewRequest request) {
        GitRepository repo = request.repositoryId() != null
                ? repositoryService.getOwned(ownerId, request.repositoryId())
                : repositoryService.getOrCreateManualRepo(ownerId);

        Review review = createAndSchedule(repo, request.title(),
                StringUtils.hasText(request.author()) ? request.author() : "unknown",
                request.diff(), null, null);
        return ReviewDetailResponse.from(review);
    }

    /** Entry point used by the GitHub webhook once a repository and diff have been resolved. */
    @Transactional
    public Review submitForRepository(GitRepository repo, String title, String author,
                                      String diff, Integer prNumber, String headRef) {
        return createAndSchedule(repo, title,
                StringUtils.hasText(author) ? author : "unknown", diff, prNumber, headRef);
    }

    private Review createAndSchedule(GitRepository repo, String title, String author,
                                     String diff, Integer prNumber, String headRef) {
        PullRequest pr = new PullRequest();
        pr.setRepository(repo);
        pr.setTitle(title);
        pr.setAuthor(author);
        pr.setDiff(diff);
        pr.setPrNumber(prNumber);
        pr.setHeadRef(headRef);
        pullRequests.save(pr);

        Review review = new Review();
        review.setPullRequest(pr);
        reviews.save(review);

        scheduleProcessingAfterCommit(review.getId());
        return review;
    }

    @Transactional(readOnly = true)
    public List<ReviewSummaryResponse> listForOwner(Long ownerId) {
        return reviews.findAllForOwner(ownerId).stream()
                .map(ReviewSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewDetailResponse getForOwner(Long ownerId, Long reviewId) {
        Review review = reviews.findByIdForOwner(reviewId, ownerId)
                .orElseThrow(() -> new NotFoundException("Review not found: " + reviewId));
        return ReviewDetailResponse.from(review);
    }

    private void scheduleProcessingAfterCommit(Long reviewId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reviewProcessor.process(reviewId);
                }
            });
        } else {
            reviewProcessor.process(reviewId);
        }
    }
}
