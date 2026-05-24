package com.speedlink.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationCodeConfirmRequest(
        @Email String email,
        @Size(max = 32) String phone,
        String purpose,
        @Size(min = 4, max = 12) String verificationCode,
        @NotBlank String supabaseAccessToken
) {
}
