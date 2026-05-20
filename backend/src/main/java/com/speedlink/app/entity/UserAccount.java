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

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

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

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, Profile profile) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.passwordHash = passwordHash;
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
    }

    public Profile toProfile() {
        return new Profile(id, displayName, role, lookingFor, expertise, goals, intent);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
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
