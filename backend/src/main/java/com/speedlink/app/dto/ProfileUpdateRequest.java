package com.speedlink.app.dto;

import com.speedlink.app.model.Profile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 240) String role,
        @NotBlank @Size(max = 240) String lookingFor,
        @Size(max = 500) String expertise,
        @Size(max = 500) String goals,
        @Size(max = 160) String intent
) {
    public Profile toProfile(String userId) {
        return new Profile(userId, displayName, role, lookingFor, expertise, goals, intent);
    }
}
