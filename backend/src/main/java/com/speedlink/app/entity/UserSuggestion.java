package com.speedlink.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_suggestions")
public class UserSuggestion {
    @Id
    private String id;

    @Column(nullable = false, length = 80)
    private String userId;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 2000)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserSuggestion() {
    }

    public UserSuggestion(String userId, String category, String title, String details) {
        this.id = UUID.randomUUID().toString();
        this.userId = clean(userId);
        this.category = clean(category);
        this.title = clean(title);
        this.details = clean(details);
    }

    @PrePersist
    public void markCreated() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
