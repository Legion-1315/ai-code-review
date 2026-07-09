package com.codereview.web;

import com.codereview.service.github.GitHubSignatureVerifier;
import com.codereview.service.github.GitHubWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final GitHubSignatureVerifier signatureVerifier;
    private final GitHubWebhookService webhookService;

    public WebhookController(GitHubSignatureVerifier signatureVerifier,
                             GitHubWebhookService webhookService) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
    }

    /**
     * GitHub webhook receiver. Consumes the raw body (as bytes) so the HMAC signature can be
     * verified against the exact payload GitHub signed.
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> github(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] payload) {

        if (!signatureVerifier.isValid(signature, payload)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "invalid signature"));
        }

        GitHubWebhookService.Outcome outcome = webhookService.handle(event, payload);
        HttpStatus status = outcome == GitHubWebhookService.Outcome.REVIEW_SCHEDULED
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(Map.of("status", outcome.name()));
    }
}
