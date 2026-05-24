package com.speedlink.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MatchingWindowRequest(
        boolean enabled,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String startTime,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String endTime,
        @NotBlank String zoneId,
        @NotNull Boolean clearQueueOnClose
) {
}
