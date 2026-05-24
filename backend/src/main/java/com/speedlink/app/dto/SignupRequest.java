package com.speedlink.app.dto;

import com.speedlink.app.model.Profile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email String email,
        @Size(max = 32) String phone,
        @NotBlank String supabaseAccessToken,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 240) String role,
        @NotBlank @Size(max = 240) String lookingFor,
        @Size(max = 500) String expertise,
        @Size(max = 500) String goals,
        @Size(max = 160) String intent,
        @Size(max = 700) String bio,
        @Size(max = 700) String interests,
        @Size(max = 240) String companyType,
        @Size(max = 80) String ageRange,
        @Size(max = 4000) String profilePhoto
) {
    public Profile toProfile() {
        return new Profile("", displayName, role, lookingFor, expertise, goals, intent, bio, interests, companyType, ageRange, profilePhoto);
    }
}
