package com.speedlink.app.service;

import com.speedlink.app.dto.AuthResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private static final long CODE_TTL_SECONDS = 600;

    private final UserAccountRepository userAccountRepository;
    private final SupabaseAuthClient supabaseAuthClient;
    private final AppTokenService appTokenService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            SupabaseAuthClient supabaseAuthClient,
            AppTokenService appTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.supabaseAuthClient = supabaseAuthClient;
        this.appTokenService = appTokenService;
    }

    @Transactional
    public AuthResponse exchangeSupabaseToken(String accessToken) {
        SupabaseUserResponse supabaseUser = supabaseAuthClient.fetchUser(accessToken);
        if (supabaseUser.id() == null || supabaseUser.id().isBlank()) {
            throw new AuthException("Supabase session did not include a user id.");
        }

        return authResponse(resolveSupabaseAccount(supabaseUser));
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (email.isBlank()) {
            throw new AuthException("Email is required.");
        }

        SupabaseUserResponse supabaseUser = supabaseAuthClient.requireVerifiedEmail(email, request.supabaseAccessToken());
        Profile profile = request.toProfile();
        if (!profile.isReadyForMatching()) {
            throw new AuthException("Name, role, and looking-for fields are required.");
        }

        Optional<UserAccount> existingAccount = userAccountRepository.findBySupabaseUserId(supabaseUser.id())
                .or(() -> userAccountRepository.findByEmail(email));
        if (existingAccount.isPresent()) {
            UserAccount account = existingAccount.get();
            if (supabaseUser.id().equals(account.getSupabaseUserId())) {
                return authResponse(account);
            }
            throw new AuthException("An account with this email already exists. Please sign in instead.");
        }

        UserAccount account = new UserAccount(
                supabaseUser.id(),
                email,
                phone.isBlank() ? null : phone,
                profile
        );
        return authResponse(userAccountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public VerificationCodeResponse requestVerificationCode(VerificationCodeRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        String purpose = normalizePurpose(request.purpose());
        if (email.isBlank()) {
            throw new AuthException("Email is required.");
        }
        if ("signup".equals(purpose) && phone.isBlank()) {
            throw new AuthException("WhatsApp phone number is required.");
        }
        if ("reset".equals(purpose) && userAccountRepository.findByEmail(email).isEmpty()) {
            return new VerificationCodeResponse("email", email, CODE_TTL_SECONDS, null);
        }
        if ("signup".equals(purpose) && (userAccountRepository.findByEmail(email).isPresent() || supabaseAuthClient.emailExists(email))) {
            throw new AuthException("An account with this email already exists. Please sign in instead.");
        }
        if ("signup".equals(purpose) && (userAccountRepository.findByPhone(phone).isPresent() || supabaseAuthClient.whatsappPhoneExists(phone))) {
            throw new AuthException("An account with this WhatsApp phone number already exists. Please sign in instead.");
        }
        return new VerificationCodeResponse("email", email, CODE_TTL_SECONDS, null);
    }

    public void confirmVerificationCode(VerificationCodeConfirmRequest request) {
        supabaseAuthClient.requireVerifiedEmail(normalizeEmail(request.email()), request.supabaseAccessToken());
    }

    @Transactional(readOnly = true)
    public void resetPassword(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.email());
        supabaseAuthClient.requireVerifiedEmail(email, request.supabaseAccessToken());
        if (userAccountRepository.findByEmail(email).isEmpty()) {
            throw new AuthException("No account was found for those details.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String rawToken) {
        return appTokenService.verifySubject(rawToken)
                .flatMap(userAccountRepository::findById);
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

    private AuthResponse authResponse(UserAccount account) {
        return new AuthResponse(appTokenService.issueToken(account.getId()), account.toProfile());
    }

    private UserAccount resolveSupabaseAccount(SupabaseUserResponse supabaseUser) {
        String supabaseUserId = supabaseUser.id();
        String email = normalizeEmail(supabaseUser.email());
        return userAccountRepository.findBySupabaseUserId(supabaseUserId)
                .or(() -> findExistingSupabaseAccount(supabaseUser).map(account -> linkSupabaseAccount(account, supabaseUserId)))
                .orElseGet(() -> createSupabaseAccount(supabaseUserId, email, supabaseUser));
    }

    private UserAccount linkSupabaseAccount(UserAccount account, String supabaseUserId) {
        String linkedSupabaseUserId = account.getSupabaseUserId();
        if (linkedSupabaseUserId == null || linkedSupabaseUserId.isBlank()) {
            account.linkSupabaseUser(supabaseUserId);
            return userAccountRepository.save(account);
        }
        if (linkedSupabaseUserId.equals(supabaseUserId)) {
            return account;
        }
        throw new AuthException("This email is linked to a different sign-in account. Please contact support.");
    }

    private UserAccount createSupabaseAccount(String supabaseUserId, String email, SupabaseUserResponse supabaseUser) {
        String phone = normalizePhone(metadataValue(supabaseUser, "whatsapp_phone"));
        if (phone.isBlank()) {
            phone = normalizePhone(supabaseUser.phone());
        }
        return userAccountRepository.save(new UserAccount(
                supabaseUserId,
                email,
                phone.isBlank() ? null : phone,
                defaultProfile(supabaseUser)
        ));
    }

    private Optional<UserAccount> findExistingSupabaseAccount(SupabaseUserResponse supabaseUser) {
        String email = normalizeEmail(supabaseUser.email());
        if (!email.isBlank()) {
            return userAccountRepository.findByEmail(email);
        }
        return Optional.empty();
    }

    private Profile defaultProfile(SupabaseUserResponse supabaseUser) {
        String email = normalizeEmail(supabaseUser.email());
        String displayName = metadataValue(supabaseUser, "full_name");
        if (displayName.isBlank()) {
            displayName = email.isBlank() ? "SpeedLink user" : email;
        }
        return new Profile(
                "",
                displayName,
                "Other / Random",
                "Anyone/Random",
                "",
                "",
                "Explore New People",
                "",
                "Explore New People",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );
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

    private String metadataValue(SupabaseUserResponse supabaseUser, String key) {
        Object value = supabaseUser.userMetadata() == null ? null : supabaseUser.userMetadata().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
