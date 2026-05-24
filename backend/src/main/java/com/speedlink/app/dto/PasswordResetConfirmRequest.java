package com.speedlink.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @Email String email,
        @Size(max = 32) String phone,
        @NotBlank String supabaseAccessToken
) {
}
