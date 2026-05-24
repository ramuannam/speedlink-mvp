package com.speedlink.app.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.speedlink.app.dto.AuthResponse;
import com.speedlink.app.dto.LoginRequest;
import com.speedlink.app.dto.PasswordResetConfirmRequest;
import com.speedlink.app.dto.ProfileUpdateRequest;
import com.speedlink.app.dto.SignupRequest;
import com.speedlink.app.dto.SupabaseUserResponse;
import com.speedlink.app.dto.VerificationCodeConfirmRequest;
import com.speedlink.app.dto.VerificationCodeRequest;
import com.speedlink.app.dto.VerificationCodeResponse;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.model.Profile;
import com.speedlink.app.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTVerifier jwtVerifier;
    private final Algorithm jwtAlgorithm;
    private final long tokenTtlMinutes;
    private final RestClient supabaseRestClient;
    private final String supabaseApiKey;
    private final ConcurrentMap<String, CodeChallenge> codeChallenges = new ConcurrentHashMap<>();
    private static final long CODE_TTL_SECONDS = 600;

    public AuthService(
            UserAccountRepository userAccountRepository,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${speedlink.auth.jwt-secret}") String jwtSecret,
            @Value("${speedlink.auth.token-ttl-minutes:720}") long tokenTtlMinutes,
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.publishable-key:${supabase.anon-key:}}") String supabaseApiKey
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtAlgorithm = Algorithm.HMAC256(jwtSecret);
        this.jwtVerifier = JWT.require(jwtAlgorithm).build();
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.supabaseRestClient = supabaseUrl == null || supabaseUrl.isBlank()
                ? null
                : RestClient.builder().baseUrl(supabaseUrl.replaceAll("/+$", "")).build();
        this.supabaseApiKey = supabaseApiKey == null ? "" : supabaseApiKey.trim();
    }

    @Transactional
    public AuthResponse exchangeSupabaseToken(String accessToken) {
        SupabaseUserResponse supabaseUser = fetchSupabaseUser(accessToken);
        if (supabaseUser.id() == null || supabaseUser.id().isBlank()) {
            throw new AuthException("Supabase session did not include a user id.");
        }

        UserAccount account = userAccountRepository.findBySupabaseUserId(supabaseUser.id())
                .or(() -> findExistingSupabaseAccount(supabaseUser))
                .orElseThrow(() -> new AuthException("Please complete signup before signing in."));

        return new AuthResponse(issueToken(account.getId()), account.toProfile());
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (email.isBlank() && phone.isBlank()) {
            throw new AuthException("Email or phone number is required.");
        }

        SupabaseUserResponse supabaseUser = requireVerifiedSupabaseEmail(email, request.supabaseAccessToken());

        Profile profile = request.toProfile();
        if (!profile.isReadyForMatching()) {
            throw new AuthException("Name, role, and looking-for fields are required.");
        }

        Optional<UserAccount> existingAccount = userAccountRepository.findBySupabaseUserId(supabaseUser.id())
                .or(() -> userAccountRepository.findByEmail(email));
        if (existingAccount.isPresent()) {
            UserAccount account = existingAccount.get();
            if (account.getDisplayName() == null || account.getDisplayName().isBlank()
                    || "SpeedLink user".equalsIgnoreCase(account.getDisplayName())
                    || account.getDisplayName().equalsIgnoreCase(email)) {
                account.applyProfile(profile);
                return new AuthResponse(issueToken(userAccountRepository.save(account).getId()), account.toProfile());
            }
            throw new AuthException("An account with this email already exists. Please sign in instead.");
        }
        if (!phone.isBlank() && userAccountRepository.existsByPhone(phone)) {
            throw new AuthException("An account with this phone number already exists. Please sign in instead.");
        }

        UserAccount account = new UserAccount(
                supabaseUser.id(),
                email.isBlank() ? null : email,
                phone.isBlank() ? null : phone,
                profile
        );
        UserAccount saved = userAccountRepository.save(account);
        return new AuthResponse(issueToken(saved.getId()), saved.toProfile());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount account = findByLogin(request)
                .orElseThrow(() -> new AuthException("Invalid login details."));

        if (account.getPasswordHash() == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AuthException("Invalid login details.");
        }

        return new AuthResponse(issueToken(account.getId()), account.toProfile());
    }

    public VerificationCodeResponse requestVerificationCode(VerificationCodeRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        String channel = email.isBlank() ? "phone" : "email";
        String destination = email.isBlank() ? phone : email;
        String purpose = normalizePurpose(request.purpose());

        if (destination.isBlank()) {
            throw new AuthException("Email or phone number is required.");
        }
        if ("reset".equals(purpose) && findAccount(channel, destination).isEmpty()) {
            throw new AuthException("No account was found for those details.");
        }
        if ("signup".equals(purpose) && findAccount(channel, destination).isPresent()) {
            throw new AuthException(channel.equals("email")
                    ? "An account with this email already exists. Please sign in instead."
                    : "An account with this phone number already exists. Please sign in instead.");
        }

        return new VerificationCodeResponse(channel, destination, CODE_TTL_SECONDS, null);
    }

    public void confirmVerificationCode(VerificationCodeConfirmRequest request) {
        String email = normalizeEmail(request.email());
        requireVerifiedSupabaseEmail(email, request.supabaseAccessToken());
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        String channel = email.isBlank() ? "phone" : "email";
        String destination = email.isBlank() ? phone : email;

        requireVerifiedSupabaseEmail(email, request.supabaseAccessToken());
        UserAccount account = findAccount(channel, destination)
                .orElseThrow(() -> new AuthException("No account was found for those details."));
        userAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String rawToken) {
        String token = normalizeBearerToken(rawToken);
        if (token.isBlank()) {
            return Optional.empty();
        }

        try {
            String userId = jwtVerifier.verify(token).getSubject();
            return userAccountRepository.findById(userId);
        } catch (JWTVerificationException exception) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Profile getProfile(String userId) {
        return userAccountRepository.findById(userId)
                .map(UserAccount::toProfile)
                .orElseThrow(() -> new AuthException("Account was not found."));
    }

    @Transactional
    public Profile updateProfile(String userId, ProfileUpdateRequest request) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Account was not found."));
        account.applyProfile(request.toProfile(userId));
        return userAccountRepository.save(account).toProfile();
    }

    @Transactional
    public Profile updateProfile(String userId, Profile profile) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Account was not found."));
        Profile cleaned = profile.withUserId(userId);
        if (!cleaned.isReadyForMatching()) {
            throw new AuthException("Name, role, and looking-for fields are required.");
        }
        account.applyProfile(cleaned);
        return userAccountRepository.save(account).toProfile();
    }

    private String issueToken(String userId) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plus(tokenTtlMinutes, ChronoUnit.MINUTES));
        return JWT.create()
                .withSubject(userId)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(jwtAlgorithm);
    }

    private Optional<UserAccount> findExistingSupabaseAccount(SupabaseUserResponse supabaseUser) {
        String email = normalizeEmail(supabaseUser.email());
        if (!email.isBlank()) {
            Optional<UserAccount> account = userAccountRepository.findByEmail(email);
            if (account.isPresent()) {
                return account;
            }
        }
        String phone = normalizePhone(supabaseUser.phone());
        if (!phone.isBlank()) {
            return userAccountRepository.findByPhone(phone);
        }
        return Optional.empty();
    }

    private SupabaseUserResponse fetchSupabaseUser(String accessToken) {
        if (supabaseRestClient == null || supabaseApiKey.isBlank()) {
            throw new AuthException("Supabase auth is not configured on the backend.");
        }
        try {
            return supabaseRestClient.get()
                    .uri("/auth/v1/user")
                    .header("apikey", supabaseApiKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + normalizeBearerToken(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new AuthException("Supabase session is invalid or expired.");
                    })
                    .body(SupabaseUserResponse.class);
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthException("Could not verify Supabase session.");
        }
    }

    private SupabaseUserResponse requireVerifiedSupabaseEmail(String expectedEmail, String accessToken) {
        String normalizedExpectedEmail = normalizeEmail(expectedEmail);
        if (normalizedExpectedEmail.isBlank()) {
            throw new AuthException("Email is required for verification.");
        }
        SupabaseUserResponse supabaseUser = fetchSupabaseUser(accessToken);
        String verifiedEmail = normalizeEmail(supabaseUser.email());
        if (verifiedEmail.isBlank() || !verifiedEmail.equals(normalizedExpectedEmail)) {
            throw new AuthException("Verified email does not match this request.");
        }
        return supabaseUser;
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

    private String normalizePurpose(String purpose) {
        return "reset".equalsIgnoreCase(purpose) ? "reset" : "signup";
    }

    private Optional<UserAccount> findByLogin(LoginRequest request) {
        String identifier = request.identifier() == null || request.identifier().isBlank()
                ? (request.email() == null || request.email().isBlank() ? request.phone() : request.email())
                : request.identifier();
        String normalizedEmail = normalizeEmail(identifier);
        String normalizedPhone = normalizePhone(identifier);

        if (identifier != null && identifier.contains("@")) {
            return userAccountRepository.findByEmail(normalizedEmail);
        }
        if (!normalizedPhone.isBlank()) {
            return userAccountRepository.findByPhone(normalizedPhone);
        }
        return userAccountRepository.findByEmail(normalizedEmail);
    }

    private Optional<UserAccount> findAccount(String channel, String destination) {
        return "phone".equals(channel)
                ? userAccountRepository.findByPhone(normalizePhone(destination))
                : userAccountRepository.findByEmail(normalizeEmail(destination));
    }

    private void verifyCode(String channel, String destination, String purpose, String code) {
        verifyCode(channel, destination, purpose, code, true);
    }

    private CodeChallenge verifyCode(String channel, String destination, String purpose, String code, boolean removeAfterVerification) {
        if (destination == null || destination.isBlank()) {
            throw new AuthException("Email or phone number is required.");
        }
        CodeChallenge challenge = codeChallenges.get(codeKey(channel, destination, purpose));
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now())) {
            throw new AuthException("Verification code expired. Please request a new code.");
        }
        if (!challenge.code().equals(String.valueOf(code).trim())) {
            throw new AuthException("Verification code is incorrect.");
        }
        if (removeAfterVerification) {
            codeChallenges.remove(codeKey(channel, destination, purpose));
        }
        return challenge;
    }

    private String codeKey(String channel, String destination, String purpose) {
        String normalizedDestination = "phone".equals(channel) ? normalizePhone(destination) : normalizeEmail(destination);
        return channel + ":" + normalizedDestination + ":" + normalizePurpose(purpose);
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

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    private record CodeChallenge(String code, Instant expiresAt, boolean verified) {
    }
}
