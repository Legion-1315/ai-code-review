package com.codereview.repository;

import com.codereview.domain.GitRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitRepositoryRepository extends JpaRepository<GitRepository, Long> {

    List<GitRepository> findByOwnerId(Long ownerId);

    Optional<GitRepository> findByFullName(String fullName);

    Optional<GitRepository> findByIdAndOwnerId(Long id, Long ownerId);
}
