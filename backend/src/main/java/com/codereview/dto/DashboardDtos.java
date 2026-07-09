package com.codereview.dto;

import com.codereview.domain.IssueCategory;

import java.util.List;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardStats(
            long totalReviews,
            long completedReviews,
            long inProgressReviews,
            double averageScore,
            int repositoryCount,
            Map<IssueCategory, Long> issuesByCategory,
            List<ScorePoint> scoreTrend) {
    }

    /** A single point on the score-over-time chart. */
    public record ScorePoint(Long reviewId, String label, int score) {
    }
}
