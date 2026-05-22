package com.speedlink.app.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Profile(
        String userId,
        String displayName,
        String role,
        String lookingFor,
        String expertise,
        String goals,
        String intent,
        String bio,
        String interests,
        String companyType,
        String ageRange,
        String profilePhoto
) {
    public Profile withUserId(String assignedUserId) {
        return new Profile(
                clean(assignedUserId),
                clean(displayName),
                clean(role),
                clean(lookingFor),
                clean(expertise),
                clean(goals),
                clean(intent),
                clean(bio),
                clean(interests),
                clean(companyType),
                clean(ageRange),
                clean(profilePhoto)
        );
    }

    public boolean isReadyForMatching() {
        return hasText(displayName) && hasText(role) && hasText(lookingFor);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
