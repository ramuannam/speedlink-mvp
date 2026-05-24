package com.speedlink.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "app_settings")
public class AppSetting {
    @Id
    private String id;

    @Column(nullable = false)
    private boolean matchingWindowEnabled;

    @Column(nullable = false)
    private LocalTime matchingWindowStart;

    @Column(nullable = false)
    private LocalTime matchingWindowEnd;

    @Column(nullable = false, length = 80)
    private String matchingWindowZoneId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AppSetting() {
    }

    public AppSetting(String id, boolean enabled, LocalTime start, LocalTime end, String zoneId) {
        Instant now = Instant.now();
        this.id = id;
        this.matchingWindowEnabled = enabled;
        this.matchingWindowStart = start;
        this.matchingWindowEnd = end;
        this.matchingWindowZoneId = zoneId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public boolean isMatchingWindowEnabled() {
        return matchingWindowEnabled;
    }

    public LocalTime getMatchingWindowStart() {
        return matchingWindowStart;
    }

    public LocalTime getMatchingWindowEnd() {
        return matchingWindowEnd;
    }

    public String getMatchingWindowZoneId() {
        return matchingWindowZoneId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateMatchingWindow(boolean enabled, LocalTime start, LocalTime end, String zoneId) {
        this.matchingWindowEnabled = enabled;
        this.matchingWindowStart = start;
        this.matchingWindowEnd = end;
        this.matchingWindowZoneId = zoneId;
        this.updatedAt = Instant.now();
    }
}
