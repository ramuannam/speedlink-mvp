package com.speedlink.app.dto;

public record VerificationCodeResponse(
        String channel,
        String destination,
        long expiresInSeconds,
        String developmentCode
) {
}
