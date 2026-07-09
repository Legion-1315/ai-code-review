package com.codereview.web;

import com.codereview.dto.RepositoryDtos.CreateRepositoryRequest;
import com.codereview.dto.RepositoryDtos.RepositoryResponse;
import com.codereview.security.AuthenticatedUser;
import com.codereview.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping
    public List<RepositoryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return repositoryService.listForOwner(user.getId()).stream()
                .map(RepositoryResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<RepositoryResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateRepositoryRequest request) {
        var repo = repositoryService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RepositoryResponse.from(repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        repositoryService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
