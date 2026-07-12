package com.codereview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pull_requests")
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "repository_id")
    private GitRepository repository;

    /** PR number on GitHub, or null for ad-hoc/manual reviews. */
    private Integer prNumber;

    /** Head commit SHA (or branch) of the PR — used to fetch repo context; null for manual reviews. */
    @Column(length = 100)
    private String headRef;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    /** The unified diff / patch under review. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String diff;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public GitRepository getRepository() {
        return repository;
    }

    public void setRepository(GitRepository repository) {
        this.repository = repository;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getHeadRef() {
        return headRef;
    }

    public void setHeadRef(String headRef) {
        this.headRef = headRef;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
