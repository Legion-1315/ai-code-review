package com.codereview.service.github;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies the {@code X-Hub-Signature-256} header GitHub sends with each webhook delivery
 * (HMAC-SHA256 of the raw request body keyed with the configured webhook secret).
 */
@Component
public class GitHubSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final String secret;

    public GitHubSignatureVerifier(@Value("${app.github.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    /** Returns true when checks are disabled (no secret configured) or the signature matches. */
    public boolean isValid(String signatureHeader, byte[] payload) {
        if (!StringUtils.hasText(secret)) {
            return true; // dev mode: signature verification disabled
        }
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        String expected = PREFIX + hmacSha256(payload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC signature", e);
        }
    }
}
