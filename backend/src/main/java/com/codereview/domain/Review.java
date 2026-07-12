package com.codereview.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "pull_request_id")
    private PullRequest pullRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING;

    /** Overall quality score 0-100, populated once the review completes. */
    private Integer overallScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Whether the model (or mock) was the real Claude API or the deterministic fallback. */
    @Column(nullable = false)
    private boolean usedRealAi = false;

    /** Findings whose file/line anchor did not exist in the diff and were demoted to file level. */
    @Column(nullable = false)
    private int unanchoredFindings = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("filePath ASC, lineNumber ASC")
    private List<ReviewIssue> issues = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public void addIssue(ReviewIssue issue) {
        issue.setReview(this);
        this.issues.add(issue);
    }

    public Long getId() {
        return id;
    }

    public PullRequest getPullRequest() {
        return pullRequest;
    }

    public void setPullRequest(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isUsedRealAi() {
        return usedRealAi;
    }

    public void setUsedRealAi(boolean usedRealAi) {
        this.usedRealAi = usedRealAi;
    }

    public int getUnanchoredFindings() {
        return unanchoredFindings;
    }

    public void setUnanchoredFindings(int unanchoredFindings) {
        this.unanchoredFindings = unanchoredFindings;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ReviewIssue> getIssues() {
        return issues;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
