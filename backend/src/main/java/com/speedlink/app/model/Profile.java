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
        String intent
) {
    public Profile withUserId(String assignedUserId) {
        return new Profile(
                clean(assignedUserId),
                clean(displayName),
                clean(role),
                clean(lookingFor),
                clean(expertise),
                clean(goals),
                clean(intent)
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
