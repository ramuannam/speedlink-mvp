package com.speedlink.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SupabaseUserResponse(
        String id,
        String email,
        String phone,
        @JsonProperty("user_metadata") Map<String, Object> userMetadata
) {
}
