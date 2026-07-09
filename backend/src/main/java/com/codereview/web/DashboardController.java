package com.codereview.web;

import com.codereview.dto.DashboardDtos.DashboardStats;
import com.codereview.security.AuthenticatedUser;
import com.codereview.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DashboardStats stats(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.stats(user.getId());
    }
}
