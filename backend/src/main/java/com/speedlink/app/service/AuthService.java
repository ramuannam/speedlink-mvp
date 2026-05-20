package com.speedlink.app.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.speedlink.app.dto.AuthResponse;
import com.speedlink.app.dto.LoginRequest;
import com.speedlink.app.dto.ProfileUpdateRequest;
import com.speedlink.app.dto.SignupRequest;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.model.Profile;
import com.speedlink.app.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTVerifier jwtVerifier;
    private final Algorithm jwtAlgorithm;
    private final long tokenTtlMinutes;

    public AuthService(
            UserAccountRepository userAccountRepository,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${speedlink.auth.jwt-secret}") String jwtSecret,
            @Value("${speedlink.auth.token-ttl-minutes:720}") long tokenTtlMinutes
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtAlgorithm = Algorithm.HMAC256(jwtSecret);
        this.jwtVerifier = JWT.require(jwtAlgorithm).build();
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userAccountRepository.existsByEmail(email)) {
            throw new AuthException("An account with this email already exists.");
        }

        Profile profile = request.toProfile();
        if (!profile.isReadyForMatching()) {
            throw new AuthException("Name, role, and looking-for fields are required.");
        }

        UserAccount account = new UserAccount(email, passwordEncoder.encode(request.password()), profile);
        UserAccount saved = userAccountRepository.save(account);
        return new AuthResponse(issueToken(saved.getId()), saved.toProfile());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount account = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AuthException("Invalid email or password.");
        }

        return new AuthResponse(issueToken(account.getId()), account.toProfile());
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String rawToken) {
        String token = normalizeBearerToken(rawToken);
        if (token.isBlank()) {
            return Optional.empty();
        }

        try {
            String userId = jwtVerifier.verify(token).getSubject();
            return userAccountRepository.findById(userId);
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Profile getProfile(String userId) {
        return userAccountRepository.findById(userId)
                .map(UserAccount::toProfile)
                .orElseThrow(() -> new AuthException("Account was not found."));
    }

    @Transactional
    public Profile updateProfile(String userId, ProfileUpdateRequest request) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Account was not found."));
        account.applyProfile(request.toProfile(userId));
        return userAccountRepository.save(account).toProfile();
    }

    @Transactional
    public Profile updateProfile(String userId, Profile profile) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Account was not found."));
        Profile cleaned = profile.withUserId(userId);
        if (!cleaned.isReadyForMatching()) {
            throw new AuthException("Name, role, and looking-for fields are required.");
        }
        account.applyProfile(cleaned);
        return userAccountRepository.save(account).toProfile();
    }

    private String issueToken(String userId) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plus(tokenTtlMinutes, ChronoUnit.MINUTES));
        return JWT.create()
                .withSubject(userId)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(jwtAlgorithm);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBearerToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }

        String trimmed = rawToken.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
