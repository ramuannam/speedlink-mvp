package com.speedlink.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.speedlink.app.model.CallStartedPayload;
import com.speedlink.app.model.ClientMessage;
import com.speedlink.app.model.MatchCancelledPayload;
import com.speedlink.app.model.MatchOfferPayload;
import com.speedlink.app.model.Profile;
import com.speedlink.app.model.QueueStatusPayload;
import com.speedlink.app.model.ServerMessage;
import com.speedlink.app.model.SignalEnvelope;
import com.speedlink.app.entity.ConversationSession;
import com.speedlink.app.entity.UserAccount;
import com.speedlink.app.entity.UserSuggestion;
import com.speedlink.app.repository.ConversationSessionRepository;
import com.speedlink.app.repository.UserAccountRepository;
import com.speedlink.app.repository.UserSuggestionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MatchingService {
    private static final long MATCH_ACCEPT_WINDOW_MILLIS = 15_000;
    private static final long CALL_WINDOW_MILLIS = 300_000;
    private static final long REJECTED_PAIR_COOLDOWN_MILLIS = 60_000;
    private static final int MATCH_SCAN_LIMIT = 250;
    private static final int WEBSOCKET_SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int WEBSOCKET_SEND_BUFFER_SIZE_BYTES = 256 * 1024;

    private static final String QUEUE_KEY = "speedlink:queue";
    private static final String PROFILE_KEY_PREFIX = "speedlink:profile:";
    private static final String PENDING_MATCH_KEY_PREFIX = "speedlink:pendingMatch:";
    private static final String USER_TO_MATCH_KEY_PREFIX = "speedlink:userToMatch:";
    private static final String REJECTED_PAIR_KEY_PREFIX = "speedlink:rejectedPair:";

    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final MatchingWindowService matchingWindowService;
    private final ConversationSessionRepository conversationSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserSuggestionRepository userSuggestionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(configInt("SPEEDLINK_SCHEDULER_THREADS", 8));
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Long> userConnectedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> userQueuedAt = new ConcurrentHashMap<>();
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeRooms = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoom = new ConcurrentHashMap<>();
    private final Map<String, CallSession> callSessions = new ConcurrentHashMap<>();
    private final Map<String, String> matchingModes = new ConcurrentHashMap<>();
    private final List<String> localQueue = new CopyOnWriteArrayList<>();
    private final Map<String, PendingMatch> localPendingMatches = new ConcurrentHashMap<>();
    private final Map<String, String> localUserToMatch = new ConcurrentHashMap<>();
    private final Map<String, Long> localRejectedPairCooldowns = new ConcurrentHashMap<>();

    public MatchingService(
            ObjectMapper objectMapper,
            AuthService authService,
            MatchingWindowService matchingWindowService,
            ConversationSessionRepository conversationSessionRepository,
            UserAccountRepository userAccountRepository,
            UserSuggestionRepository userSuggestionRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.matchingWindowService = matchingWindowService;
        this.conversationSessionRepository = conversationSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.userSuggestionRepository = userSuggestionRepository;
        this.redisTemplate = redisTemplate;
    }

    private void saveProfile(Profile profile) {
        if (profile == null || profile.userId() == null || profile.userId().isBlank()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(PROFILE_KEY_PREFIX + profile.userId(), payload);
        } catch (Exception ignored) {
        }
    }

    private Profile loadProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        Profile profile = profiles.get(userId);
        if (profile != null) {
            return profile;
        }

        String data;
        try {
            data = redisTemplate.opsForValue().get(PROFILE_KEY_PREFIX + userId);
        } catch (Exception exception) {
            return null;
        }
        if (data == null) {
            return null;
        }

        try {
            profile = objectMapper.readValue(data, Profile.class);
            profiles.put(userId, profile);
            return profile;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void deleteProfile(String userId) {
        try {
            redisTemplate.delete(PROFILE_KEY_PREFIX + userId);
        } catch (Exception ignored) {
        }
        profiles.remove(userId);
    }

    private void addToQueue(String userId) {
        try {
            redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId);
            redisTemplate.opsForList().rightPush(QUEUE_KEY, userId);
            return;
        } catch (Exception ignored) {
        }
        localQueue.remove(userId);
        localQueue.add(userId);
    }

    private void removeFromQueue(String userId) {
        userQueuedAt.remove(userId);
        try {
            redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId);
            return;
        } catch (Exception ignored) {
        }
        localQueue.remove(userId);
    }

    private List<String> getQueuedUsers() {
        try {
            List<String> items = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
            return items == null ? List.of() : items;
        } catch (Exception exception) {
            return List.copyOf(localQueue);
        }
    }

    private List<String> getQueueWindow() {
        try {
            List<String> items = redisTemplate.opsForList().range(QUEUE_KEY, 0, MATCH_SCAN_LIMIT - 1);
            return items == null ? List.of() : items;
        } catch (Exception exception) {
            return localQueue.stream().limit(MATCH_SCAN_LIMIT).toList();
        }
    }

    private void savePendingMatch(PendingMatch match) {
        localPendingMatches.put(match.id(), match);
        localUserToMatch.put(match.userA(), match.id());
        localUserToMatch.put(match.userB(), match.id());
        try {
            redisTemplate.opsForValue().set(PENDING_MATCH_KEY_PREFIX + match.id(), objectMapper.writeValueAsString(match));
            redisTemplate.opsForValue().set(USER_TO_MATCH_KEY_PREFIX + match.userA(), match.id());
            redisTemplate.opsForValue().set(USER_TO_MATCH_KEY_PREFIX + match.userB(), match.id());
        } catch (Exception ignored) {
        }
    }

    private PendingMatch loadPendingMatch(String matchId) {
        String payload;
        try {
            payload = redisTemplate.opsForValue().get(PENDING_MATCH_KEY_PREFIX + matchId);
        } catch (Exception exception) {
            return localPendingMatches.get(matchId);
        }
        if (payload == null) {
            return localPendingMatches.get(matchId);
        }
        try {
            return objectMapper.readValue(payload, PendingMatch.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removePendingMatch(String matchId) {
        PendingMatch match = loadPendingMatch(matchId);
        localPendingMatches.remove(matchId);
        if (match != null) {
            localUserToMatch.remove(match.userA());
            localUserToMatch.remove(match.userB());
        }
        try {
            redisTemplate.delete(PENDING_MATCH_KEY_PREFIX + matchId);
            if (match != null) {
                redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + match.userA());
                redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + match.userB());
            }
        } catch (Exception ignored) {
        }
    }

    private String getUserToMatch(String userId) {
        try {
            String matchId = redisTemplate.opsForValue().get(USER_TO_MATCH_KEY_PREFIX + userId);
            return matchId == null ? localUserToMatch.get(userId) : matchId;
        } catch (Exception exception) {
            return localUserToMatch.get(userId);
        }
    }

    private void deleteUserToMatch(String userId) {
        localUserToMatch.remove(userId);
        try {
            redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + userId);
        } catch (Exception ignored) {
        }
    }

    private void setRejectedPairCooldown(String userA, String userB) {
        String key = REJECTED_PAIR_KEY_PREFIX + pairKey(userA, userB);
        localRejectedPairCooldowns.put(key, Instant.now().toEpochMilli() + REJECTED_PAIR_COOLDOWN_MILLIS);
        try {
            redisTemplate.opsForValue().set(key, "1", REJECTED_PAIR_COOLDOWN_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    private boolean isRejectedPairCooldown(String userA, String userB) {
        String key = REJECTED_PAIR_KEY_PREFIX + pairKey(userA, userB);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception exception) {
            Long expiresAt = localRejectedPairCooldowns.get(key);
            if (expiresAt == null) {
                return false;
            }
            if (expiresAt <= Instant.now().toEpochMilli()) {
                localRejectedPairCooldowns.remove(key);
                return false;
            }
            return true;
        }
    }

    private int queueSize() {
        try {
            Long size = redisTemplate.opsForList().size(QUEUE_KEY);
            return size == null ? 0 : size.intValue();
        } catch (Exception exception) {
            return localQueue.size();
        }
    }

    private int pendingMatchesSize() {
        try {
            return redisTemplate.keys(PENDING_MATCH_KEY_PREFIX + "*").size();
        } catch (Exception ignored) {
            return localPendingMatches.size();
        }
    }

    private List<PendingMatch> getAllPendingMatches() {
        Set<String> keys;
        try {
            keys = redisTemplate.keys(PENDING_MATCH_KEY_PREFIX + "*");
        } catch (Exception exception) {
            return List.copyOf(localPendingMatches.values());
        }
        if (keys == null || keys.isEmpty()) {
            return List.copyOf(localPendingMatches.values());
        }
        List<PendingMatch> matches = new ArrayList<>();
        for (String key : keys) {
            PendingMatch match = loadPendingMatch(key.replace(PENDING_MATCH_KEY_PREFIX, ""));
            if (match != null) {
                matches.add(match);
            }
        }
        return matches;
    }

    public void connect(WebSocketSession session, Profile profile) {
        String userId = profile.userId();
        WebSocketSession decoratedSession = new ConcurrentWebSocketSessionDecorator(
                session,
                WEBSOCKET_SEND_TIME_LIMIT_MILLIS,
                WEBSOCKET_SEND_BUFFER_SIZE_BYTES
        );
        WebSocketSession previousSession = sessions.put(userId, decoratedSession);
        if (previousSession != null && previousSession.isOpen() && !previousSession.getId().equals(session.getId())) {
            try {
                previousSession.close();
            } catch (IOException ignored) {
            }
        }

        sessionToUserId.put(session.getId(), userId);
        userConnectedAt.put(userId, Instant.now().toEpochMilli());
        profiles.put(userId, profile);
        saveProfile(profile);
        send(userId, "connected", Map.of("userId", userId, "profile", profile));
    }

    public void disconnectSession(String sessionId) {
        String userId = sessionToUserId.remove(sessionId);
        WebSocketSession activeSession = userId == null ? null : sessions.get(userId);
        if (activeSession != null && activeSession.getId().equals(sessionId)) {
            disconnect(userId);
        }
    }

    private void disconnect(String userId) {
        sessions.remove(userId);
        removeFromQueue(userId);
        deleteProfile(userId);
        matchingModes.remove(userId);
        userConnectedAt.remove(userId);
        userQueuedAt.remove(userId);

        String pendingMatchId = getUserToMatch(userId);
        if (pendingMatchId != null) {
            PendingMatch match = loadPendingMatch(pendingMatchId);
            removePendingMatch(pendingMatchId);
            if (match != null) {
                String peerId = match.peerOf(userId);
                send(peerId, "match-cancelled", new MatchCancelledPayload(match.id(), "Peer went offline"));
                requeueIfAvailable(peerId);
            }
        }

        String roomId = findRoomForUser(userId);
        if (roomId != null) {
            endCall(roomId, "Peer disconnected", userId);
        }

        notifyQueueChanged();
    }

    public void handle(WebSocketSession session, ClientMessage message) {
        String type = message.getType() == null ? "" : message.getType();
        String userId = sessionToUserId.get(session.getId());
        if (userId == null) {
            return;
        }

        try {
            switch (type) {
                case "ping" -> send(userId, "pong", Map.of("timestamp", Instant.now().toEpochMilli()));
                case "updateProfile" -> updateProfile(userId, message.getProfile());
                case "joinQueue" -> joinQueue(userId, message.getProfile(), message.getMatchingMode());
                case "leaveQueue" -> leaveQueue(userId);
                case "acceptMatch" -> acceptMatch(userId, message.getMatchId());
                case "rejectMatch" -> rejectMatch(userId, message.getMatchId());
                case "signal" -> forwardSignal(userId, message);
                case "chatMessage" -> forwardChatMessage(userId, message);
                case "continueCall" -> continueCall(userId, message.getRoomId());
                case "endCall" -> endCall(userId, message.getRoomId());
                default -> send(userId, "error", Map.of("message", "Unsupported message type: " + type));
            }
        } catch (Exception exception) {
            send(userId, "error", Map.of("message", "Realtime action failed. Please try again."));
        }
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "onlineUsers", sessions.size(),
                "profiles", profiles.size(),
                "queuedUsers", queueSize(),
                "pendingMatches", pendingMatchesSize(),
                "activeRooms", activeRooms.size(),
                "matchingWindow", matchingWindowService.current()
        );
    }

    public Map<String, Object> adminDashboard(String date) {
        List<Map<String, Object>> onlineUsers = sessions.keySet().stream()
                .map(userId -> userSummary(userId, "online"))
                .toList();
        List<Map<String, Object>> queuedUsers = getQueuedUsers().stream()
                .map(userId -> userSummary(userId, "queued"))
                .toList();
        InstantRange dateRange = parseDateRange(date);
        List<ConversationSession> conversationSessions = dateRange == null
                ? conversationSessionRepository.findTop50ByOrderByStartedAtDesc()
                : conversationSessionRepository.findByStartedAtBetweenOrderByStartedAtDesc(dateRange.start(), dateRange.end());
        List<UserSuggestion> userSuggestions = dateRange == null
                ? userSuggestionRepository.findTop100ByOrderByCreatedAtDesc()
                : userSuggestionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(dateRange.start(), dateRange.end());
        List<Map<String, Object>> conversations = conversationSessions.stream()
                .map(this::conversationSummary)
                .toList();
        List<Map<String, Object>> suggestions = userSuggestions.stream()
                .map(this::suggestionSummary)
                .toList();

        return Map.of(
                "onlineUsers", onlineUsers,
                "queuedUsers", queuedUsers,
                "conversations", conversations,
                "suggestions", suggestions,
                "dateFilter", dateRange == null ? "" : date,
                "counts", Map.of(
                        "onlineUsers", onlineUsers.size(),
                        "queuedUsers", queuedUsers.size(),
                        "conversations", conversations.size(),
                        "suggestions", suggestions.size(),
                        "activeRooms", activeRooms.size()
                )
        );
    }

    public void clearQueue(String reason) {
        List<String> users = getQueuedUsers();
        for (String userId : users) {
            removeFromQueue(userId);
            matchingModes.remove(userId);
            userQueuedAt.remove(userId);
            send(userId, "queue-status", new QueueStatusPayload(false, queueSize(), reason));
        }
        notifyQueueChanged();
    }

    private void updateProfile(String userId, Profile profile) {
        if (profile == null) {
            send(userId, "error", Map.of("message", "Profile is required"));
            return;
        }

        try {
            Profile savedProfile = authService.updateProfile(userId, profile);
            profiles.put(userId, savedProfile);
            saveProfile(savedProfile);
            send(userId, "profile-updated", savedProfile);
        } catch (AuthService.AuthException exception) {
            send(userId, "error", Map.of("message", exception.getMessage()));
        }
    }

    private void joinQueue(String userId, Profile profile, String matchingMode) {
        if (!matchingWindowService.isOpenNow()) {
            var window = matchingWindowService.current();
            send(userId, "queue-status", new QueueStatusPayload(false, queueSize(), "Search opens at " + window.displayLabel()));
            send(userId, "error", Map.of("message", "Search is available only from " + window.displayLabel()));
            return;
        }

        if (profile != null) {
            updateProfile(userId, profile);
        } else if (!profiles.containsKey(userId)) {
            Profile loaded = loadProfile(userId);
            if (loaded == null) {
                loaded = authService.getProfile(userId);
                saveProfile(loaded);
            }
            profiles.put(userId, loaded);
        }

        Profile savedProfile = profiles.get(userId);

        if (savedProfile == null || !savedProfile.isReadyForMatching()) {
            send(userId, "error", Map.of("message", "Add your name, role, and who you are looking for before joining."));
            return;
        }

        if (getUserToMatch(userId) != null || findRoomForUser(userId) != null) {
            send(userId, "queue-status", new QueueStatusPayload(false, queueSize(), "Already in a live match"));
            return;
        }

        addToQueue(userId);
        userQueuedAt.put(userId, Instant.now().toEpochMilli());
        matchingModes.put(userId, normalizeMatchingMode(matchingMode));
        send(userId, "queue-status", new QueueStatusPayload(true, queueSize(), isBasicMode(userId)
                ? "Searching randomly"
                : "Searching for a relevant match"));
        tryMatchQueuedUsers();
        notifyQueueChanged();
    }

    private void leaveQueue(String userId) {
        removeFromQueue(userId);
        matchingModes.remove(userId);
        userQueuedAt.remove(userId);
        send(userId, "queue-status", new QueueStatusPayload(false, queueSize(), "Left the queue"));
        notifyQueueChanged();
    }

    private synchronized void acceptMatch(String userId, String matchId) {
        PendingMatch match = loadPendingMatch(matchId);
        if (match == null || !match.hasParticipant(userId)) {
            send(userId, "match-cancelled", new MatchCancelledPayload(matchId, "This match is no longer available"));
            return;
        }

        match.accept(userId);
        savePendingMatch(match);
        send(userId, "match-accepted", Map.of("matchId", matchId));

        if (match.isMutuallyAccepted()) {
            removePendingMatch(matchId);

            String roomId = "room-" + UUID.randomUUID();
            long endsAt = Instant.now().toEpochMilli() + CALL_WINDOW_MILLIS;
            activeRooms.put(roomId, Set.of(match.userA(), match.userB()));
            userToRoom.put(match.userA(), roomId);
            userToRoom.put(match.userB(), roomId);
            callSessions.put(roomId, new CallSession(endsAt));
            String initiatorUserId = match.userA().compareTo(match.userB()) <= 0 ? match.userA() : match.userB();
            saveConversationStarted(roomId, match);

            send(match.userA(), "call-started", new CallStartedPayload(match.id(), roomId, profiles.get(match.userB()), initiatorUserId, endsAt));
            send(match.userB(), "call-started", new CallStartedPayload(match.id(), roomId, profiles.get(match.userA()), initiatorUserId, endsAt));
            scheduler.schedule(() -> expireCall(roomId, endsAt), CALL_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
            notifyQueueChanged();
        }
    }

    private void rejectMatch(String userId, String matchId) {
        PendingMatch match = loadPendingMatch(matchId);
        if (match == null || !match.hasParticipant(userId)) {
            send(userId, "match-cancelled", new MatchCancelledPayload(matchId, "This match is no longer available"));
            return;
        }

        removePendingMatch(matchId);
        setRejectedPairCooldown(match.userA(), match.userB());

        send(match.userA(), "match-cancelled", new MatchCancelledPayload(match.id(), "Match rejected"));
        send(match.userB(), "match-cancelled", new MatchCancelledPayload(match.id(), "Match rejected"));

        requeueIfAvailable(match.userA());
        requeueIfAvailable(match.userB());
        tryMatchQueuedUsers();
        notifyQueueChanged();
    }

    private void expireMatch(String matchId) {
        PendingMatch match = loadPendingMatch(matchId);
        if (match == null) {
            return;
        }
        removePendingMatch(matchId);

        send(match.userA(), "match-cancelled", new MatchCancelledPayload(match.id(), "Accept window expired"));
        send(match.userB(), "match-cancelled", new MatchCancelledPayload(match.id(), "Accept window expired"));

        requeueIfAvailable(match.userA());
        requeueIfAvailable(match.userB());
        tryMatchQueuedUsers();
        notifyQueueChanged();
    }

    private void forwardSignal(String userId, ClientMessage message) {
        String roomId = message.getRoomId();
        Set<String> participants = activeRooms.get(roomId);

        if (participants == null || !participants.contains(userId)) {
            send(userId, "error", Map.of("message", "You are not in this room"));
            return;
        }

        for (String participant : participants) {
            if (!participant.equals(userId)) {
                send(participant, "signal", new SignalEnvelope(roomId, userId, message.getPayload()));
            }
        }
    }

    private void forwardChatMessage(String userId, ClientMessage message) {
        String roomId = message.getRoomId();
        Set<String> participants = activeRooms.get(roomId);

        if (participants == null || !participants.contains(userId)) {
            send(userId, "error", Map.of("message", "You are not in this room"));
            return;
        }

        String text = message.getPayload() == null || message.getPayload().get("text") == null
                ? ""
                : message.getPayload().get("text").asText("").trim();

        if (text.isBlank()) {
            return;
        }

        if (text.length() > 500) {
            text = text.substring(0, 500);
        }

        Map<String, Object> payload = Map.of(
                "roomId", roomId,
                "senderUserId", userId,
                "text", text,
                "sentAtEpochMillis", Instant.now().toEpochMilli()
        );

        for (String participant : participants) {
            send(participant, "chat-message", payload);
        }
    }

    private void endCall(String userId, String roomId) {
        Set<String> participants = activeRooms.get(roomId);
        if (participants == null || !participants.contains(userId)) {
            return;
        }

        endCall(roomId, "Session ended", null);
        notifyQueueChanged();
    }

    private void continueCall(String userId, String roomId) {
        Set<String> participants = activeRooms.get(roomId);
        CallSession callSession = callSessions.get(roomId);
        if (participants == null || !participants.contains(userId) || callSession == null) {
            return;
        }

        callSession.continueUntilDisconnected = true;
        for (String participant : participants) {
            send(participant, "call-continued", Map.of("roomId", roomId));
        }
    }

    private void expireCall(String roomId, long expectedEndsAt) {
        CallSession callSession = callSessions.get(roomId);
        if (callSession == null
                || callSession.continueUntilDisconnected
                || callSession.endsAtEpochMillis != expectedEndsAt) {
            return;
        }

        endCall(roomId, "Call time ended", null);
        notifyQueueChanged();
    }

    private void endCall(String roomId, String reason, String excludedUserId) {
        Set<String> participants = activeRooms.remove(roomId);
        callSessions.remove(roomId);
        if (participants == null) {
            return;
        }

        for (String participant : participants) {
            userToRoom.remove(participant);
            if (!participant.equals(excludedUserId)) {
                send(participant, "call-ended", Map.of("roomId", roomId, "reason", reason));
            }
        }
        saveConversationEnded(roomId, reason);
    }

    private void tryMatchQueuedUsers() {
        cleanupPairCooldowns();

        boolean madeMatch;
        do {
            madeMatch = false;
            List<String> waitingUsers = getQueueWindow();
            Set<String> queuedUsers = new HashSet<>(waitingUsers);

            MatchCandidate bestMatch = findBestMatch(waitingUsers, queuedUsers);
            if (bestMatch != null) {
                removeFromQueue(bestMatch.userA());
                removeFromQueue(bestMatch.userB());
                createPendingMatch(bestMatch.userA(), bestMatch.userB());
                madeMatch = true;
            }
        } while (madeMatch);
    }

    private MatchCandidate findBestMatch(List<String> waitingUsers, Set<String> queuedUsers) {
        MatchCandidate bestMatch = null;
        boolean allowFallback = noPreferredPairExists(waitingUsers, queuedUsers);

        for (int i = 0; i < waitingUsers.size(); i++) {
            String userA = waitingUsers.get(i);
            if (!queuedUsers.contains(userA)) {
                continue;
            }

            for (int j = i + 1; j < waitingUsers.size(); j++) {
                String userB = waitingUsers.get(j);
                if (!queuedUsers.contains(userB)) {
                    continue;
                }

                int score = compatibilityScore(userA, userB);
                if (score == 0 && allowFallback && canFallbackMatch(userA, userB)) {
                    score = 1;
                }
                if (score > 0 && (bestMatch == null || score > bestMatch.score())) {
                    bestMatch = new MatchCandidate(userA, userB, score);
                }
            }
        }

        return bestMatch;
    }

    private void createPendingMatch(String userA, String userB) {
        String matchId = "match-" + UUID.randomUUID();
        long expiresAt = Instant.now().toEpochMilli() + MATCH_ACCEPT_WINDOW_MILLIS;
        PendingMatch match = new PendingMatch(matchId, userA, userB, expiresAt, Set.of());

        savePendingMatch(match);

        send(userA, "match-offer", new MatchOfferPayload(matchId, profiles.get(userB), expiresAt));
        send(userB, "match-offer", new MatchOfferPayload(matchId, profiles.get(userA), expiresAt));

        scheduler.schedule(() -> expireMatch(matchId), MATCH_ACCEPT_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
    }

    private int compatibilityScore(String userA, String userB) {
        if (isRejectedPairCooldown(userA, userB)) {
            return 0;
        }

        Profile profileA = profiles.get(userA);
        Profile profileB = profiles.get(userB);

        if (profileA == null || profileB == null) {
            return 0;
        }

        if (isBasicMode(userA) || isBasicMode(userB)) {
            return 1;
        }

        boolean aWantsB = lookingForMatchesRole(profileA.lookingFor(), profileB.role());
        boolean bWantsA = lookingForMatchesRole(profileB.lookingFor(), profileA.role());
        boolean aOpen = containsOpenTarget(profileA.lookingFor());
        boolean bOpen = containsOpenTarget(profileB.lookingFor());
        int score = 0;

        if (aWantsB && bWantsA) {
            score += 8;
        } else if ((aWantsB && bOpen) || (bWantsA && aOpen)) {
            score += 5;
        }

        score += overlapCount(profileA.interests(), profileB.interests()) * 3;
        score += overlapCount(profileA.intent(), profileB.intent()) * 4;
        score += overlapCount(profileA.companyType(), profileB.companyType()) * 2;

        if (containsOpenTarget(profileA.intent()) && containsOpenTarget(profileB.intent())) {
            score += 6;
        }

        return score;
    }

    private boolean isCompatible(String userA, String userB) {
        return compatibilityScore(userA, userB) > 0;
    }

    private boolean noPreferredPairExists(List<String> waitingUsers, Set<String> queuedUsers) {
        for (int i = 0; i < waitingUsers.size(); i++) {
            String userA = waitingUsers.get(i);
            if (!queuedUsers.contains(userA)) {
                continue;
            }

            for (int j = i + 1; j < waitingUsers.size(); j++) {
                String userB = waitingUsers.get(j);
                if (queuedUsers.contains(userB) && isCompatible(userA, userB)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean canFallbackMatch(String userA, String userB) {
        return !isRejectedPairCooldown(userA, userB)
                && profiles.containsKey(userA)
                && profiles.containsKey(userB);
    }

    private boolean isBasicMode(String userId) {
        return "basic".equals(matchingModes.getOrDefault(userId, "advanced"));
    }

    private String normalizeMatchingMode(String matchingMode) {
        return "basic".equalsIgnoreCase(matchingMode) ? "basic" : "advanced";
    }

    private int overlapCount(String left, String right) {
        Set<String> leftValues = new HashSet<>(splitProfileValues(left));
        Set<String> rightValues = new HashSet<>(splitProfileValues(right));
        leftValues.retainAll(rightValues);
        return leftValues.size();
    }

    private boolean lookingForMatchesRole(String lookingFor, String role) {
        List<String> targets = splitProfileValues(lookingFor);
        List<String> candidateRoles = splitProfileValues(role);

        if (targets.isEmpty() || candidateRoles.isEmpty()) {
            return false;
        }

        for (String target : targets) {
            if (isRandomTarget(target)) {
                return true;
            }

            for (String candidateRole : candidateRoles) {
                if (isRandomTarget(candidateRole)
                        || target.contains(candidateRole)
                        || candidateRole.contains(target)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<String> splitProfileValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String item : value.split("[,;|]+")) {
            String normalized = normalize(item);
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private boolean isRandomTarget(String value) {
        return value.contains("any")
                || value.contains("open")
                || value.contains("random")
                || value.contains("other");
    }

    private boolean containsOpenTarget(String value) {
        return splitProfileValues(value).stream().anyMatch(this::isRandomTarget);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("-", " ").trim();
    }

    private static int configInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void requeueIfAvailable(String userId) {
        if (sessions.containsKey(userId) && profiles.containsKey(userId) && findRoomForUser(userId) == null) {
            addToQueue(userId);
            userQueuedAt.put(userId, Instant.now().toEpochMilli());
            send(userId, "queue-status", new QueueStatusPayload(true, queueSize(), isBasicMode(userId)
                    ? "Searching randomly"
                    : "Searching for a relevant match"));
        }
    }

    private Map<String, Object> userSummary(String userId, String state) {
        Profile profile = profiles.get(userId);
        if (profile == null) {
            profile = loadProfile(userId);
        }
        UserAccount account = userAccountRepository.findById(userId).orElse(null);

        Map<String, Object> row = new HashMap<>(accountSummary(account));
        row.putAll(Map.ofEntries(
                Map.entry("userId", userId),
                Map.entry("state", state),
                Map.entry("displayName", profile == null ? "Unknown user" : safe(profile.displayName())),
                Map.entry("role", profile == null ? "" : safe(profile.role())),
                Map.entry("lookingFor", profile == null ? "" : safe(profile.lookingFor())),
                Map.entry("companyType", profile == null ? "" : safe(profile.companyType())),
                Map.entry("interests", profile == null ? "" : safe(profile.interests())),
                Map.entry("goals", profile == null ? "" : safe(profile.goals())),
                Map.entry("matchingMode", matchingModes.getOrDefault(userId, "advanced")),
                Map.entry("connectedAtEpochMillis", userConnectedAt.getOrDefault(userId, 0L)),
                Map.entry("queuedAtEpochMillis", userQueuedAt.getOrDefault(userId, 0L))
        ));
        return row;
    }

    private Map<String, Object> conversationSummary(ConversationSession conversation) {
        long startedAt = conversation.getStartedAt() == null ? 0L : conversation.getStartedAt().toEpochMilli();
        long endedAt = conversation.getEndedAt() == null ? 0L : conversation.getEndedAt().toEpochMilli();
        long durationSeconds = endedAt > 0 && startedAt > 0 ? Math.max(0, (endedAt - startedAt) / 1000) : 0;

        return Map.of(
                "roomId", conversation.getRoomId(),
                "matchId", conversation.getMatchId(),
                "status", conversation.getStatus(),
                "startedAtEpochMillis", startedAt,
                "endedAtEpochMillis", endedAt,
                "durationSeconds", durationSeconds,
                "endReason", safe(conversation.getEndReason()),
                "users", List.of(
                        conversationUserSummary(conversation.getUserAId(), conversation.getUserAName(), conversation.getUserARole()),
                        conversationUserSummary(conversation.getUserBId(), conversation.getUserBName(), conversation.getUserBRole())
                )
        );
    }

    private Map<String, Object> conversationUserSummary(String userId, String fallbackName, String fallbackRole) {
        UserAccount account = userAccountRepository.findById(userId).orElse(null);
        Map<String, Object> row = new HashMap<>(accountSummary(account));
        row.put("userId", userId);
        row.put("displayName", account == null ? safe(fallbackName) : safe(account.getDisplayName()));
        row.put("role", account == null ? safe(fallbackRole) : safe(account.getRole()));
        return row;
    }

    private Map<String, Object> suggestionSummary(UserSuggestion suggestion) {
        UserAccount account = userAccountRepository.findById(suggestion.getUserId()).orElse(null);
        Map<String, Object> row = new HashMap<>(accountSummary(account));
        row.put("id", suggestion.getId());
        row.put("userId", suggestion.getUserId());
        row.put("category", suggestion.getCategory());
        row.put("title", suggestion.getTitle());
        row.put("details", suggestion.getDetails());
        row.put("createdAtEpochMillis", suggestion.getCreatedAt() == null ? 0L : suggestion.getCreatedAt().toEpochMilli());
        return row;
    }

    private Map<String, Object> accountSummary(UserAccount account) {
        if (account == null) {
            return Map.ofEntries(
                    Map.entry("email", ""),
                    Map.entry("phone", ""),
                    Map.entry("emailVerified", false),
                    Map.entry("phoneVerified", false),
                    Map.entry("expertise", ""),
                    Map.entry("intent", ""),
                    Map.entry("bio", ""),
                    Map.entry("ageRange", ""),
                    Map.entry("profilePhoto", ""),
                    Map.entry("createdAtEpochMillis", 0L),
                    Map.entry("updatedAtEpochMillis", 0L)
            );
        }

        return Map.ofEntries(
                Map.entry("email", safe(account.getEmail())),
                Map.entry("phone", safe(account.getPhone())),
                Map.entry("emailVerified", Boolean.TRUE.equals(account.getEmailVerified())),
                Map.entry("phoneVerified", Boolean.TRUE.equals(account.getPhoneVerified())),
                Map.entry("expertise", safe(account.getExpertise())),
                Map.entry("intent", safe(account.getIntent())),
                Map.entry("bio", safe(account.getBio())),
                Map.entry("ageRange", safe(account.getAgeRange())),
                Map.entry("profilePhoto", safe(account.getProfilePhoto())),
                Map.entry("createdAtEpochMillis", account.getCreatedAt() == null ? 0L : account.getCreatedAt().toEpochMilli()),
                Map.entry("updatedAtEpochMillis", account.getUpdatedAt() == null ? 0L : account.getUpdatedAt().toEpochMilli())
        );
    }

    private void saveConversationStarted(String roomId, PendingMatch match) {
        Profile userA = profiles.get(match.userA());
        Profile userB = profiles.get(match.userB());
        try {
            conversationSessionRepository.save(new ConversationSession(
                    roomId,
                    match.id(),
                    match.userA(),
                    match.userB(),
                    userA == null ? "" : userA.displayName(),
                    userA == null ? "" : userA.role(),
                    userB == null ? "" : userB.displayName(),
                    userB == null ? "" : userB.role()
            ));
        } catch (Exception ignored) {
        }
    }

    private void saveConversationEnded(String roomId, String reason) {
        try {
            conversationSessionRepository.findById(roomId).ifPresent(conversation -> {
                conversation.end(reason);
                conversationSessionRepository.save(conversation);
            });
        } catch (Exception ignored) {
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private InstantRange parseDateRange(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(date.trim());
            ZoneId zoneId = ZoneId.systemDefault();
            Instant start = localDate.atStartOfDay(zoneId).toInstant();
            Instant end = localDate.plusDays(1).atStartOfDay(zoneId).toInstant();
            return new InstantRange(start, end);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Date filter is invalid.");
        }
    }

    private record InstantRange(Instant start, Instant end) {
    }

    private void notifyQueueChanged() {
        // At production scale, broadcasting every queue-size change to every waiting user
        // turns a single join into O(n) websocket writes. Users receive direct status
        // updates when they join, leave, match, expire, or are requeued.
    }

    private String findRoomForUser(String userId) {
        return userToRoom.get(userId);
    }

    private void cleanupPairCooldowns() {
        long now = Instant.now().toEpochMilli();
        localRejectedPairCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        // Redis TTL handles rejected pair cooldown expiration.
    }

    private String pairKey(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + ":" + userB : userB + ":" + userA;
    }

    private void send(String userId, String type, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(new ServerMessage(type, payload));
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException exception) {
            sessions.remove(userId);
        }
    }

    private static final class PendingMatch {
        @JsonProperty
        private final String id;
        @JsonProperty
        private final String userA;
        @JsonProperty
        private final String userB;
        @JsonProperty
        private final long expiresAtEpochMillis;
        @JsonProperty
        private final Set<String> acceptedUsers;

        @JsonCreator
        private PendingMatch(
                @JsonProperty("id") String id,
                @JsonProperty("userA") String userA,
                @JsonProperty("userB") String userB,
                @JsonProperty("expiresAtEpochMillis") long expiresAtEpochMillis,
                @JsonProperty("acceptedUsers") Set<String> acceptedUsers
        ) {
            this.id = id;
            this.userA = userA;
            this.userB = userB;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
            this.acceptedUsers = acceptedUsers == null ? new HashSet<>() : new HashSet<>(acceptedUsers);
        }

        private String id() {
            return id;
        }

        private String userA() {
            return userA;
        }

        private String userB() {
            return userB;
        }

        private boolean hasParticipant(String userId) {
            return userA.equals(userId) || userB.equals(userId);
        }

        private void accept(String userId) {
            if (Instant.now().toEpochMilli() <= expiresAtEpochMillis && hasParticipant(userId)) {
                acceptedUsers.add(userId);
            }
        }

        private boolean isMutuallyAccepted() {
            return acceptedUsers.contains(userA) && acceptedUsers.contains(userB);
        }

        private String peerOf(String userId) {
            return userA.equals(userId) ? userB : userA;
        }
    }

    private static final class CallSession {
        private final long endsAtEpochMillis;
        private boolean continueUntilDisconnected;

        private CallSession(long endsAtEpochMillis) {
            this.endsAtEpochMillis = endsAtEpochMillis;
        }
    }

    private record MatchCandidate(String userA, String userB, int score) {
    }
}
