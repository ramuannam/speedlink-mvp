package com.speedlink.app.dto;

import com.speedlink.app.model.Profile;

public record AuthResponse(
        String token,
        Profile profile
) {
}
