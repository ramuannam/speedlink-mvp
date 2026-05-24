package com.speedlink.app.entity;

import com.speedlink.app.model.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    private String id;

    @Column(unique = true, length = 190)
    private String email;

    @Column(unique = true, length = 80)
    private String supabaseUserId;

    @Column(length = 32)
    private String phone;

    @Column
    private Boolean emailVerified = false;

    @Column
    private Boolean phoneVerified = false;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 240)
    private String role;

    @Column(nullable = false, length = 240)
    private String lookingFor;

    @Column(length = 500)
    private String expertise;

    @Column(length = 500)
    private String goals;

    @Column(length = 160)
    private String intent;

    @Column(length = 700)
    private String bio;

    @Column(length = 700)
    private String interests;

    @Column(length = 240)
    private String companyType;

    @Column(length = 80)
    private String ageRange;

    @Column(length = 4000)
    private String profilePhoto;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String supabaseUserId, String email, String phone, Profile profile) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.supabaseUserId = supabaseUserId;
        this.email = email;
        this.phone = phone;
        this.emailVerified = email != null && !email.isBlank();
        this.phoneVerified = phone != null && !phone.isBlank();
        this.createdAt = now;
        this.updatedAt = now;
        applyProfile(profile);
    }

    @PreUpdate
    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public void applyProfile(Profile profile) {
        this.displayName = clean(profile.displayName());
        this.role = clean(profile.role());
        this.lookingFor = clean(profile.lookingFor());
        this.expertise = clean(profile.expertise());
        this.goals = clean(profile.goals());
        this.intent = clean(profile.intent());
        this.bio = clean(profile.bio());
        this.interests = clean(profile.interests());
        this.companyType = clean(profile.companyType());
        this.ageRange = clean(profile.ageRange());
        this.profilePhoto = clean(profile.profilePhoto());
    }

    public Profile toProfile() {
        return new Profile(id, displayName, role, lookingFor, expertise, goals, intent, bio, interests, companyType, ageRange, profilePhoto);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSupabaseUserId() {
        return supabaseUserId;
    }

    public String getPhone() {
        return phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
