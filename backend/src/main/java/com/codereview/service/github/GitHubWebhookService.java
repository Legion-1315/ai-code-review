package com.codereview.service.github;

import com.codereview.domain.GitRepository;
import com.codereview.repository.GitRepositoryRepository;
import com.codereview.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * Handles incoming GitHub {@code pull_request} webhook deliveries: resolves the connected
 * repository, fetches the PR diff, and schedules an AI review.
 */
@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final Set<String> REVIEWABLE_ACTIONS = Set.of("opened", "reopened", "synchronize");

    private final ObjectMapper mapper = new ObjectMapper();
    private final GitRepositoryRepository repositories;
    private final GitHubDiffClient diffClient;
    private final ReviewService reviewService;

    public GitHubWebhookService(GitRepositoryRepository repositories,
                                GitHubDiffClient diffClient,
                                ReviewService reviewService) {
        this.repositories = repositories;
        this.diffClient = diffClient;
        this.reviewService = reviewService;
    }

    public enum Outcome {
        IGNORED_EVENT,
        IGNORED_ACTION,
        REPO_NOT_CONNECTED,
        NO_DIFF,
        REVIEW_SCHEDULED
    }

    public Outcome handle(String eventType, byte[] payload) {
        if (!"pull_request".equals(eventType)) {
            return Outcome.IGNORED_EVENT;
        }
        try {
            JsonNode root = mapper.readTree(payload);
            String action = root.path("action").asText("");
            if (!REVIEWABLE_ACTIONS.contains(action)) {
                return Outcome.IGNORED_ACTION;
            }

            String fullName = root.path("repository").path("full_name").asText("");
            Optional<GitRepository> repoOpt = repositories.findByFullName(fullName);
            if (repoOpt.isEmpty()) {
                log.info("Webhook for unconnected repository '{}' ignored.", fullName);
                return Outcome.REPO_NOT_CONNECTED;
            }

            JsonNode pr = root.path("pull_request");
            int number = pr.path("number").asInt();
            String title = pr.path("title").asText("Untitled PR");
            String author = pr.path("user").path("login").asText("unknown");
            String diffUrl = pr.path("diff_url").asText(null);

            Optional<String> diff = diffClient.fetchDiff(diffUrl);
            if (diff.isEmpty()) {
                return Outcome.NO_DIFF;
            }

            reviewService.submitForRepository(repoOpt.get(), title, author, diff.get(), number);
            log.info("Scheduled review for {} PR #{}", fullName, number);
            return Outcome.REVIEW_SCHEDULED;
        } catch (Exception e) {
            log.error("Failed to process webhook: {}", e.getMessage(), e);
            throw new IllegalStateException("Malformed webhook payload", e);
        }
    }
}
