package com.codereview.service.github;

import java.util.Optional;

/** Fetches a text resource from GitHub. Interface so tests can fake the network. */
@FunctionalInterface
public interface GitHubContentFetcher {

    Optional<String> fetchText(String url);
}
