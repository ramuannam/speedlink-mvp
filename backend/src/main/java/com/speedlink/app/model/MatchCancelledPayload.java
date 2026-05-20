package com.speedlink.app.model;

public record MatchCancelledPayload(
        String matchId,
        String reason
) {
}
