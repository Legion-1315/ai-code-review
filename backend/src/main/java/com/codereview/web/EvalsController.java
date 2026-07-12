package com.codereview.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the committed evaluation report ({@code resources/evals/report.json}) produced by
 * {@code EvalHarnessTest}. Public — it is the project's measured-quality scoreboard.
 *
 * To refresh: run {@code mvn test} (optionally with ANTHROPIC_API_KEY exported so the real
 * Claude engine is scored too), then copy {@code target/evals/report.json} over the
 * committed copy.
 */
@RestController
@RequestMapping("/api/evals")
public class EvalsController {

    @GetMapping(value = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> report() throws IOException {
        ClassPathResource resource = new ClassPathResource("evals/report.json");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resource.getContentAsString(StandardCharsets.UTF_8));
    }
}
