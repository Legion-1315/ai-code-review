package com.codereview.repository;

import com.codereview.domain.Review;
import com.codereview.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("""
            select r from Review r
            join r.pullRequest pr
            join pr.repository repo
            where repo.owner.id = :ownerId
            order by r.createdAt desc
            """)
    List<Review> findAllForOwner(@Param("ownerId") Long ownerId);

    @Query("""
            select r from Review r
            join r.pullRequest pr
            join pr.repository repo
            where r.id = :id and repo.owner.id = :ownerId
            """)
    Optional<Review> findByIdForOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    @Query("""
            select count(r) from Review r
            join r.pullRequest pr
            join pr.repository repo
            where repo.owner.id = :ownerId and r.status = :status
            """)
    long countForOwnerByStatus(@Param("ownerId") Long ownerId, @Param("status") ReviewStatus status);

    @Query("""
            select avg(r.overallScore) from Review r
            join r.pullRequest pr
            join pr.repository repo
            where repo.owner.id = :ownerId and r.overallScore is not null
            """)
    Double averageScoreForOwner(@Param("ownerId") Long ownerId);
}
