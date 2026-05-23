package com.speedlink.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @Email String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(min = 4, max = 12) String verificationCode,
        @NotBlank @Size(min = 8, max = 120) String password
) {
}
