package com.speedlink.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record VerificationCodeRequest(
        @Email String email,
        @Size(max = 32) String phone,
        String purpose
) {
}
