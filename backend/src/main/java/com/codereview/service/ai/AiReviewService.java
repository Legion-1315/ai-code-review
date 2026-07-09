package com.codereview.service.ai;

/** Produces a structured code review for a unified diff. */
public interface AiReviewService {

    AiReviewResult review(String prTitle, String diff);
}
