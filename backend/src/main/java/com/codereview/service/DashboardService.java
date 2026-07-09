package com.codereview.service;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.Review;
import com.codereview.domain.ReviewStatus;
import com.codereview.dto.DashboardDtos.DashboardStats;
import com.codereview.dto.DashboardDtos.ScorePoint;
import com.codereview.repository.GitRepositoryRepository;
import com.codereview.repository.ReviewIssueRepository;
import com.codereview.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final int TREND_LIMIT = 15;

    private final ReviewRepository reviews;
    private final ReviewIssueRepository issues;
    private final GitRepositoryRepository repositories;

    public DashboardService(ReviewRepository reviews,
                            ReviewIssueRepository issues,
                            GitRepositoryRepository repositories) {
        this.reviews = reviews;
        this.issues = issues;
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public DashboardStats stats(Long ownerId) {
        long completed = reviews.countForOwnerByStatus(ownerId, ReviewStatus.COMPLETED);
        long inProgress = reviews.countForOwnerByStatus(ownerId, ReviewStatus.IN_PROGRESS)
                + reviews.countForOwnerByStatus(ownerId, ReviewStatus.PENDING);
        long failed = reviews.countForOwnerByStatus(ownerId, ReviewStatus.FAILED);
        long total = completed + inProgress + failed;

        Double avg = reviews.averageScoreForOwner(ownerId);
        double averageScore = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;

        Map<IssueCategory, Long> byCategory = new EnumMap<>(IssueCategory.class);
        for (IssueCategory category : IssueCategory.values()) {
            byCategory.put(category, 0L);
        }
        issues.countByCategoryForOwner(ownerId)
                .forEach(c -> byCategory.put(c.getCategory(), c.getCount()));

        int repoCount = repositories.findByOwnerId(ownerId).size();

        return new DashboardStats(
                total, completed, inProgress, averageScore, repoCount,
                byCategory, buildScoreTrend(ownerId));
    }

    private List<ScorePoint> buildScoreTrend(Long ownerId) {
        List<Review> all = reviews.findAllForOwner(ownerId); // newest-first
        List<ScorePoint> points = new ArrayList<>();
        for (Review review : all) {
            if (review.getStatus() == ReviewStatus.COMPLETED && review.getOverallScore() != null) {
                String label = "#" + review.getId();
                points.add(new ScorePoint(review.getId(), label, review.getOverallScore()));
            }
            if (points.size() >= TREND_LIMIT) {
                break;
            }
        }
        // Reverse so the chart reads oldest -> newest left to right.
        java.util.Collections.reverse(points);
        return points;
    }
}
