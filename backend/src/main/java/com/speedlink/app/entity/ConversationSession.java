package com.speedlink.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "conversation_sessions")
public class ConversationSession {
    @Id
    private String roomId;

    @Column(nullable = false, length = 120)
    private String matchId;

    @Column(nullable = false, length = 80)
    private String userAId;

    @Column(nullable = false, length = 80)
    private String userBId;

    @Column(nullable = false, length = 120)
    private String userAName;

    @Column(nullable = false, length = 240)
    private String userARole;

    @Column(nullable = false, length = 120)
    private String userBName;

    @Column(nullable = false, length = 240)
    private String userBRole;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant endedAt;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 240)
    private String endReason;

    protected ConversationSession() {
    }

    public ConversationSession(String roomId, String matchId, String userAId, String userBId, String userAName, String userARole, String userBName, String userBRole) {
        this.roomId = roomId;
        this.matchId = matchId;
        this.userAId = userAId;
        this.userBId = userBId;
        this.userAName = clean(userAName);
        this.userARole = clean(userARole);
        this.userBName = clean(userBName);
        this.userBRole = clean(userBRole);
        this.startedAt = Instant.now();
        this.status = "ACTIVE";
    }

    public void end(String reason) {
        if (endedAt == null) {
            endedAt = Instant.now();
        }
        status = "ENDED";
        endReason = clean(reason);
    }

    public String getRoomId() {
        return roomId;
    }

    public String getMatchId() {
        return matchId;
    }

    public String getUserAId() {
        return userAId;
    }

    public String getUserBId() {
        return userBId;
    }

    public String getUserAName() {
        return userAName;
    }

    public String getUserARole() {
        return userARole;
    }

    public String getUserBName() {
        return userBName;
    }

    public String getUserBRole() {
        return userBRole;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getEndReason() {
        return endReason;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
