package com.codereview.web;

import com.codereview.dto.ReviewDtos.ReviewDetailResponse;
import com.codereview.dto.ReviewDtos.ReviewSummaryResponse;
import com.codereview.dto.ReviewDtos.SubmitReviewRequest;
import com.codereview.security.AuthenticatedUser;
import com.codereview.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewDetailResponse> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitReviewRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(reviewService.submit(user.getId(), request));
    }

    @GetMapping
    public List<ReviewSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.listForOwner(user.getId());
    }

    @GetMapping("/{id}")
    public ReviewDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        return reviewService.getForOwner(user.getId(), id);
    }
}
