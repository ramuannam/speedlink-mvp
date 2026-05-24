package com.speedlink.app.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Component
public class AppTokenService {
    private final JWTVerifier jwtVerifier;
    private final Algorithm jwtAlgorithm;
    private final long tokenTtlMinutes;

    public AppTokenService(
            @Value("${speedlink.auth.jwt-secret}") String jwtSecret,
            @Value("${speedlink.auth.token-ttl-minutes:720}") long tokenTtlMinutes
    ) {
        this.jwtAlgorithm = Algorithm.HMAC256(jwtSecret);
        this.jwtVerifier = JWT.require(jwtAlgorithm).build();
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    public String issueToken(String userId) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plus(tokenTtlMinutes, ChronoUnit.MINUTES));
        return JWT.create()
                .withSubject(userId)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(jwtAlgorithm);
    }

    public Optional<String> verifySubject(String rawToken) {
        String token = normalizeBearerToken(rawToken);
        if (token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(jwtVerifier.verify(token).getSubject());
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
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
}
