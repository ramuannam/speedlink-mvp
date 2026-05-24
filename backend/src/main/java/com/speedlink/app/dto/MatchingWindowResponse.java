package com.speedlink.app.dto;

public record MatchingWindowResponse(
        boolean enabled,
        String startTime,
        String endTime,
        String zoneId,
        boolean openNow,
        long serverNowEpochMillis,
        long nextOpenEpochMillis,
        long nextCloseEpochMillis,
        String displayLabel,
        String updatedAt
) {
}
