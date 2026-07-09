package com.codereview.dto;

import com.codereview.domain.GitRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class RepositoryDtos {

    private RepositoryDtos() {
    }

    public record CreateRepositoryRequest(
            @NotBlank String fullName,
            @Min(0) @Max(100) Integer minScoreThreshold) {
    }

    public record RepositoryResponse(
            Long id,
            String fullName,
            int minScoreThreshold,
            Instant createdAt) {

        public static RepositoryResponse from(GitRepository repo) {
            return new RepositoryResponse(
                    repo.getId(),
                    repo.getFullName(),
                    repo.getMinScoreThreshold(),
                    repo.getCreatedAt());
        }
    }
}
