package com.speedlink.app.service;

import com.speedlink.app.dto.SupabaseUserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Component
public class SupabaseAuthClient {
    private final RestClient restClient;
    private final String apiKey;

    public SupabaseAuthClient(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.publishable-key:${supabase.anon-key:}}") String apiKey
    ) {
        this.restClient = supabaseUrl == null || supabaseUrl.isBlank()
                ? null
                : RestClient.builder().baseUrl(supabaseUrl.replaceAll("/+$", "")).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public SupabaseUserResponse fetchUser(String accessToken) {
        if (restClient == null || apiKey.isBlank()) {
            throw new AuthService.AuthException("Supabase auth is not configured on the backend.");
        }
        try {
            return restClient.get()
                    .uri("/auth/v1/user")
                    .header("apikey", apiKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalizeBearerToken(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new AuthService.AuthException("Supabase session is invalid or expired.");
                    })
                    .body(SupabaseUserResponse.class);
        } catch (AuthService.AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthService.AuthException("Could not verify Supabase session.");
        }
    }

    public SupabaseUserResponse requireVerifiedEmail(String expectedEmail, String accessToken) {
        String normalizedExpectedEmail = normalizeEmail(expectedEmail);
        if (normalizedExpectedEmail.isBlank()) {
            throw new AuthService.AuthException("Email is required for verification.");
        }

        SupabaseUserResponse supabaseUser = fetchUser(accessToken);
        String verifiedEmail = normalizeEmail(supabaseUser.email());
        if (verifiedEmail.isBlank() || !verifiedEmail.equals(normalizedExpectedEmail)) {
            throw new AuthService.AuthException("Verified email does not match this request.");
        }
        if (supabaseUser.id() == null || supabaseUser.id().isBlank()) {
            throw new AuthService.AuthException("Supabase session did not include a user id.");
        }
        return supabaseUser;
    }

    private String normalizeBearerToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        String trimmed = rawToken.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
