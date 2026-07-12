package com.codereview.service.ai;

import com.codereview.service.context.RepoContext;

/** Produces a structured code review for a unified diff. */
public interface AiReviewService {

    default AiReviewResult review(String prTitle, String diff) {
        return review(prTitle, diff, RepoContext.empty());
    }

    /**
     * @param context repository context (touched files + imports) for webhook reviews;
     *                {@link RepoContext#empty()} for manual submissions
     */
    AiReviewResult review(String prTitle, String diff, RepoContext context);
}
