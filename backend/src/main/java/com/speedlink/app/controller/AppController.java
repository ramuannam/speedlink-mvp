package com.speedlink.app.controller;

import com.speedlink.app.dto.ApiError;
import com.speedlink.app.dto.SuggestionRequest;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.entity.UserSuggestion;
import com.speedlink.app.repository.UserSuggestionRepository;
import com.speedlink.app.service.AuthService;
import com.speedlink.app.service.MatchingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AppController {
    private final MatchingService matchingService;
    private final AuthService authService;
    private final UserSuggestionRepository userSuggestionRepository;

    public AppController(
            MatchingService matchingService,
            AuthService authService,
            UserSuggestionRepository userSuggestionRepository
    ) {
        this.matchingService = matchingService;
        this.authService = authService;
        this.userSuggestionRepository = userSuggestionRepository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return matchingService.snapshot();
    }

    @PostMapping("/suggestions")
    public ResponseEntity<Object> createSuggestion(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SuggestionRequest request
    ) {
        Optional<UserAccount> account = authService.authenticate(authorization);
        if (account.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("Login is required."));
        }

        String category = normalizeCategory(request.category());
        UserSuggestion suggestion = userSuggestionRepository.save(new UserSuggestion(
                account.get().getId(),
                category,
                request.title(),
                request.details()
        ));
        return ResponseEntity.ok(Map.of(
                "id", suggestion.getId(),
                "message", "Suggestion submitted."
        ));
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase();
        return switch (normalized) {
            case "feature" -> "Feature suggestion";
            case "modification" -> "Modification";
            default -> "Suggestion";
        };
    }
}
