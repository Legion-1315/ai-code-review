package com.codereview.service.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches text resources from GitHub (raw file contents, tree listings) for repo-context
 * enrichment. Works unauthenticated against public repositories, consistent with the
 * zero-credential design; set {@code GITHUB_TOKEN} to raise rate limits or reach
 * private repositories.
 */
@Component
public class GitHubContentClient implements GitHubContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubContentClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String token;

    public GitHubContentClient(@Value("${app.github.token:}") String token) {
        this.token = token;
    }

    @Override
    public Optional<String> fetchText(String url) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ai-code-review")
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (StringUtils.hasText(token)) {
                request.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            }
            log.debug("GitHub fetch {} returned HTTP {}", url, response.statusCode());
            return Optional.empty();
        } catch (Exception e) {
            log.debug("GitHub fetch {} failed: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
