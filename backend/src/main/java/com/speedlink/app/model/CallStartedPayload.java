package com.speedlink.app.model;

public record CallStartedPayload(
        String matchId,
        String roomId,
        Profile peer,
        String initiatorUserId,
        long endsAtEpochMillis
) {
}
