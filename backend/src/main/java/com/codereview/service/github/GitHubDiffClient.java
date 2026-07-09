package com.codereview.service.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Fetches the unified diff for a pull request from its GitHub {@code diff_url} (public repos). */
@Component
public class GitHubDiffClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubDiffClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Optional<String> fetchDiff(String diffUrl) {
        if (diffUrl == null || diffUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(diffUrl))
                    .header("Accept", "application/vnd.github.v3.diff")
                    .header("User-Agent", "ai-code-review")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            log.warn("Diff fetch from {} returned HTTP {}", diffUrl, response.statusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch diff from {}: {}", diffUrl, e.getMessage());
            return Optional.empty();
        }
    }
}
