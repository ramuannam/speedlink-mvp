package com.speedlink.app.controller;

import com.speedlink.app.dto.ApiError;
import com.speedlink.app.dto.MatchingWindowRequest;
import com.speedlink.app.dto.MatchingWindowResponse;
import com.speedlink.app.service.MatchingService;
import com.speedlink.app.service.MatchingWindowService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdminController {
    private final MatchingWindowService matchingWindowService;
    private final MatchingService matchingService;
    private final String adminKey;

    public AdminController(
            MatchingWindowService matchingWindowService,
            MatchingService matchingService,
            @Value("${speedlink.admin.key:}") String adminKey
    ) {
        this.matchingWindowService = matchingWindowService;
        this.matchingService = matchingService;
        this.adminKey = adminKey;
    }

    @GetMapping("/matching-window")
    public MatchingWindowResponse matchingWindow() {
        return matchingWindowService.current();
    }

    @PutMapping("/admin/matching-window")
    public ResponseEntity<MatchingWindowResponse> updateMatchingWindow(
            @RequestHeader(value = "X-SpeedLink-Admin-Key", required = false) String providedKey,
            @Valid @RequestBody MatchingWindowRequest request
    ) {
        if (!isAdmin(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MatchingWindowResponse response = matchingWindowService.update(request);
        if (request.clearQueueOnClose() && !response.openNow()) {
            matchingService.clearQueue("Search opens at " + response.displayLabel());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<Object> dashboard(
            @RequestHeader(value = "X-SpeedLink-Admin-Key", required = false) String providedKey
    ) {
        if (!isAdmin(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(matchingService.adminDashboard());
    }

    private boolean isAdmin(String providedKey) {
        return adminKey != null && !adminKey.isBlank() && adminKey.equals(providedKey);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }
}
