package com.speedlink.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestionRequest(
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 140) String title,
        @NotBlank @Size(max = 2000) String details
) {
}
