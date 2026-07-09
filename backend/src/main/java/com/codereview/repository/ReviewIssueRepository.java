package com.codereview.repository;

import com.codereview.domain.IssueCategory;
import com.codereview.domain.ReviewIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, Long> {

    @Query("""
            select i.category as category, count(i) as count
            from ReviewIssue i
            join i.review r
            join r.pullRequest pr
            join pr.repository repo
            where repo.owner.id = :ownerId
            group by i.category
            """)
    List<CategoryCount> countByCategoryForOwner(@Param("ownerId") Long ownerId);

    /** Projection for category aggregation. */
    interface CategoryCount {
        IssueCategory getCategory();

        long getCount();
    }
}
