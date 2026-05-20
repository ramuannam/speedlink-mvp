package com.speedlink.app.controller;

import com.speedlink.app.service.MatchingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AppController {
    private final MatchingService matchingService;

    public AppController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return matchingService.snapshot();
    }
}
