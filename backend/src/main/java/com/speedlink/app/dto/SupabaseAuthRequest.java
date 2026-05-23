package com.speedlink.app.dto;

import jakarta.validation.constraints.NotBlank;

public record SupabaseAuthRequest(
        @NotBlank String accessToken
) {
}
