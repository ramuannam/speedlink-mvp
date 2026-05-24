package com.speedlink.app.controller;

import com.speedlink.app.dto.ApiError;
import com.speedlink.app.dto.AuthResponse;
import com.speedlink.app.dto.LoginRequest;
import com.speedlink.app.dto.PasswordResetConfirmRequest;
import com.speedlink.app.dto.ProfileResponse;
import com.speedlink.app.dto.ProfileUpdateRequest;
import com.speedlink.app.dto.SignupRequest;
import com.speedlink.app.dto.SupabaseAuthRequest;
import com.speedlink.app.dto.VerificationCodeConfirmRequest;
import com.speedlink.app.dto.VerificationCodeRequest;
import com.speedlink.app.dto.VerificationCodeResponse;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.model.Profile;
import com.speedlink.app.service.AuthService;
import jakarta.validation.Valid;
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
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/supabase")
    public AuthResponse supabase(@Valid @RequestBody SupabaseAuthRequest request) {
        return authService.exchangeSupabaseToken(request.accessToken());
    }

    @PostMapping("/verification-code")
    public VerificationCodeResponse requestVerificationCode(@Valid @RequestBody VerificationCodeRequest request) {
        return authService.requestVerificationCode(request);
    }

    @PostMapping("/verify-code")
    public ResponseEntity<Void> verifyCode(@Valid @RequestBody VerificationCodeConfirmRequest request) {
        authService.confirmVerificationCode(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
        return ResponseEntity.badRequest().body(new ApiError("An account with those details already exists. Please sign in instead."));
    }
}
