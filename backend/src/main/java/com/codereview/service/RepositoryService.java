package com.codereview.service;

import com.codereview.domain.GitRepository;
import com.codereview.domain.User;
import com.codereview.dto.RepositoryDtos.CreateRepositoryRequest;
import com.codereview.exception.BadRequestException;
import com.codereview.exception.NotFoundException;
import com.codereview.repository.GitRepositoryRepository;
import com.codereview.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RepositoryService {

    private static final String MANUAL_REPO_SUFFIX = "/manual-reviews";

    private final GitRepositoryRepository repositories;
    private final UserRepository users;

    public RepositoryService(GitRepositoryRepository repositories, UserRepository users) {
        this.repositories = repositories;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<GitRepository> listForOwner(Long ownerId) {
        return repositories.findByOwnerId(ownerId);
    }

    @Transactional
    public GitRepository create(Long ownerId, CreateRepositoryRequest request) {
        if (repositories.findByFullName(request.fullName()).isPresent()) {
            throw new BadRequestException("Repository '" + request.fullName() + "' is already connected.");
        }
        User owner = loadUser(ownerId);
        GitRepository repo = new GitRepository();
        repo.setOwner(owner);
        repo.setFullName(request.fullName());
        if (request.minScoreThreshold() != null) {
            repo.setMinScoreThreshold(request.minScoreThreshold());
        }
        return repositories.save(repo);
    }

    @Transactional
    public GitRepository getOwned(Long ownerId, Long repoId) {
        return repositories.findByIdAndOwnerId(repoId, ownerId)
                .orElseThrow(() -> new NotFoundException("Repository not found: " + repoId));
    }

    @Transactional
    public void delete(Long ownerId, Long repoId) {
        GitRepository repo = getOwned(ownerId, repoId);
        repositories.delete(repo);
    }

    /** Resolves (or lazily creates) a catch-all repository for ad-hoc manual reviews. */
    @Transactional
    public GitRepository getOrCreateManualRepo(Long ownerId) {
        String fullName = "user-" + ownerId + MANUAL_REPO_SUFFIX;
        return repositories.findByFullName(fullName).orElseGet(() -> {
            GitRepository repo = new GitRepository();
            repo.setOwner(loadUser(ownerId));
            repo.setFullName(fullName);
            return repositories.save(repo);
        });
    }

    private User loadUser(Long ownerId) {
        return users.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found: " + ownerId));
    }
}
