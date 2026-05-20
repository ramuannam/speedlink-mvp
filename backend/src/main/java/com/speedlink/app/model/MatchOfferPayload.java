package com.speedlink.app.model;

public record MatchOfferPayload(
        String matchId,
        Profile candidate,
        long expiresAtEpochMillis
) {
}
