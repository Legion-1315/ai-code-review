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

/**
 * A source repository connected to the system. Named {@code GitRepository} to avoid
 * clashing with Spring Data's {@code Repository} marker interface.
 */
@Entity
@Table(name = "repositories")
public class GitRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Full name as on GitHub, e.g. "octocat/hello-world". */
    @Column(nullable = false)
    private String fullName;

    /** Score threshold below which a PR is flagged as "needs fixes". */
    @Column(nullable = false)
    private int minScoreThreshold = 60;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public void setMinScoreThreshold(int minScoreThreshold) {
        this.minScoreThreshold = minScoreThreshold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
