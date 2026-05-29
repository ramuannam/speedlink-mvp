package com.speedlink.app.service;

import com.speedlink.app.dto.SupabaseUserResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SupabaseAuthClient {
    private final RestClient restClient;
    private final String supabaseUrl;
    private final String apiKey;
    private final String serviceRoleKey;

    public SupabaseAuthClient(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.publishable-key:${supabase.anon-key:}}") String apiKey,
            @Value("${supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.trim().replaceAll("/+$", "");
        this.restClient = this.supabaseUrl.isBlank()
                ? null
                : RestClient.builder().baseUrl(this.supabaseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.serviceRoleKey = serviceRoleKey == null ? "" : serviceRoleKey.trim();
    }

    public String projectRef() {
        if (supabaseUrl.isBlank()) {
            return "";
        }
        try {
            String host = java.net.URI.create(supabaseUrl).getHost();
            if (host == null || host.isBlank()) {
                return "";
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.endsWith(".supabase.co")
                    ? normalizedHost.substring(0, normalizedHost.indexOf(".supabase.co"))
                    : normalizedHost;
        } catch (Exception exception) {
            return "";
        }
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

    public boolean emailExists(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || restClient == null || serviceRoleKey.isBlank()) {
            return false;
        }
        try {
            SupabaseUsersResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/auth/v1/admin/users")
                            .queryParam("page", 1)
                            .queryParam("per_page", 1000)
                            .queryParam("filter", normalizedEmail)
                            .build())
                    .header("apikey", serviceRoleKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                        throw new AuthService.AuthException("Could not check whether this email already has an account.");
                    })
                    .body(SupabaseUsersResponse.class);
            return response != null
                    && response.users() != null
                    && response.users().stream().anyMatch(user -> normalizedEmail.equals(normalizeEmail(user.email())));
        } catch (AuthService.AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthService.AuthException("Could not check whether this email already has an account.");
        }
    }

    public boolean whatsappPhoneExists(String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isBlank() || restClient == null || serviceRoleKey.isBlank()) {
            return false;
        }
        try {
            for (int page = 1; page <= 10; page += 1) {
                int currentPage = page;
                SupabaseUsersResponse response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/auth/v1/admin/users")
                                .queryParam("page", currentPage)
                                .queryParam("per_page", 1000)
                                .build())
                        .header("apikey", serviceRoleKey)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, responseBody) -> {
                            throw new AuthService.AuthException("Could not check whether this phone number already has an account.");
                        })
                        .body(SupabaseUsersResponse.class);
                List<SupabaseUserSummary> users = response == null ? List.of() : response.users();
                if (users == null || users.isEmpty()) {
                    return false;
                }
                if (users.stream().anyMatch(user -> normalizedPhone.equals(normalizePhone(metadataValue(user.userMetadata(), "whatsapp_phone"))))) {
                    return true;
                }
            }
            return false;
        } catch (AuthService.AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthService.AuthException("Could not check whether this phone number already has an account.");
        }
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

    private String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            return "+" + trimmed.substring(1).replaceAll("\\D", "");
        }
        return trimmed.replaceAll("\\D", "");
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseUsersResponse(List<SupabaseUserSummary> users) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SupabaseUserSummary(
            String email,
            @com.fasterxml.jackson.annotation.JsonProperty("user_metadata") Map<String, Object> userMetadata
    ) {
    }
}
