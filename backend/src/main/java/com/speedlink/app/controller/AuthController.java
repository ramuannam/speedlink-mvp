package com.speedlink.app.controller;

import com.speedlink.app.dto.ApiError;
import com.speedlink.app.dto.AuthResponse;
import com.speedlink.app.dto.EmailSessionConfirmRequest;
import com.speedlink.app.dto.PasswordResetConfirmRequest;
import com.speedlink.app.dto.ProfileResponse;
import com.speedlink.app.dto.ProfileUpdateRequest;
import com.speedlink.app.dto.SignupRequest;
import com.speedlink.app.dto.SupabaseConfigResponse;
import com.speedlink.app.dto.SupabaseAuthRequest;
import com.speedlink.app.dto.VerificationLinkRequest;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.model.Profile;
import com.speedlink.app.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/supabase")
    public AuthResponse supabase(@Valid @RequestBody SupabaseAuthRequest request) {
        return authService.exchangeSupabaseToken(request.accessToken());
    }

    @GetMapping("/supabase-config")
    public SupabaseConfigResponse supabaseConfig() {
        return new SupabaseConfigResponse(authService.supabaseProjectRef());
    }

    @PostMapping("/verification-link")
    public ResponseEntity<Void> verificationLink(@Valid @RequestBody VerificationLinkRequest request) {
        authService.requestVerificationLink(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-session")
    public ResponseEntity<Void> confirmEmailSession(@Valid @RequestBody EmailSessionConfirmRequest request) {
        authService.confirmEmailSession(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Optional<UserAccount> account = authService.authenticate(authorization);
        return account.map(userAccount -> ResponseEntity.ok(new ProfileResponse(userAccount.toProfile())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        Optional<UserAccount> account = authService.authenticate(authorization);
        if (account.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Profile profile = authService.updateProfile(account.get().getId(), request);
        return ResponseEntity.ok(new ProfileResponse(profile));
    }

    @ExceptionHandler(AuthService.AuthException.class)
    public ResponseEntity<ApiError> handleAuthException(AuthService.AuthException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "Invalid request." : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityException(DataIntegrityViolationException exception) {
        String detail = exception.getMostSpecificCause() == null
                ? exception.getMessage()
                : exception.getMostSpecificCause().getMessage();
        String normalizedDetail = detail == null ? "" : detail.toLowerCase();
        if (normalizedDetail.contains("supabase") || normalizedDetail.contains("email")) {
            return ResponseEntity.badRequest().body(new ApiError("An account with this email already exists. Please sign in instead."));
        }
        if (normalizedDetail.contains("phone")) {
            return ResponseEntity.badRequest().body(new ApiError("An account with this phone number already exists. Please sign in instead."));
        }
        return ResponseEntity.badRequest().body(new ApiError("Account could not be completed because one of those details is already in use."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        log.error("Unexpected auth failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Authentication service failed. Please try again."));
    }
}
