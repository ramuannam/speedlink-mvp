package com.speedlink.app.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String email,
        String phone,
        String identifier,
        @NotBlank String password
) {
}
