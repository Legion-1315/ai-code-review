package com.codereview.domain;

/** Lifecycle of a review as it moves through the async pipeline. */
public enum ReviewStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
