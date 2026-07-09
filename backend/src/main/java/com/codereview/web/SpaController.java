package com.codereview.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards non-API, non-asset GET requests to index.html so React Router can handle
 * client-side routes (e.g. /login, /dashboard, /reviews/123). Static assets — files with
 * an extension — are served directly by Spring's default resource handler.
 */
@Controller
public class SpaController {

    // Match "/" and any single-segment path without a dot (e.g. /login, /dashboard, /reviews)
    // Also match nested paths without a dot (e.g. /reviews/123).
    @GetMapping(value = {"/{path:[^.]*}", "/{path:[^.]*}/**"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
