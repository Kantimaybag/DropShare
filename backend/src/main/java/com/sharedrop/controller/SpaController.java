package com.sharedrop.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Controller to route Single-Page Application (SPA) client-side URL paths
 * back to index.html so the frontend JavaScript router can handle the view state.
 */
@Controller
public class SpaController {

    @GetMapping({"/d/{token}", "/share/{token}"})
    public String forwardSpaRoutes(@PathVariable("token") String token) {
        return "forward:/index.html";
    }
}
