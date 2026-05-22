package com.speedlink.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speedlink.app.model.CallStartedPayload;
import com.speedlink.app.model.ClientMessage;
import com.speedlink.app.model.MatchCancelledPayload;
import com.speedlink.app.model.MatchOfferPayload;
import com.speedlink.app.model.Profile;
import com.speedlink.app.model.QueueStatusPayload;
import com.speedlink.app.model.ServerMessage;
import com.speedlink.app.model.SignalEnvelope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class MatchingService {
    private static final long MATCH_ACCEPT_WINDOW_MILLIS = 15_000;
    private static final long CALL_WINDOW_MILLIS = 300_000;
    private static final long REJECTED_PAIR_COOLDOWN_MILLIS = 60_000;

    private static final String QUEUE_KEY = "speedlink:queue";
    private static final String PROFILE_KEY_PREFIX = "speedlink:profile:";
    private static final String PENDING_MATCH_KEY_PREFIX = "speedlink:pendingMatch:";
    private static final String USER_TO_MATCH_KEY_PREFIX = "speedlink:userToMatch:";
    private static final String REJECTED_PAIR_KEY_PREFIX = "speedlink:rejectedPair:";

    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeRooms = new HashMap<>();
    private final Map<String, CallSession> callSessions = new HashMap<>();

    public MatchingService(ObjectMapper objectMapper, AuthService authService, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.authService = authService;
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

        String data = redisTemplate.opsForValue().get(PROFILE_KEY_PREFIX + userId);
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
        redisTemplate.delete(PROFILE_KEY_PREFIX + userId);
        profiles.remove(userId);
    }

    private void addToQueue(String userId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId);
        redisTemplate.opsForList().rightPush(QUEUE_KEY, userId);
    }

    private void removeFromQueue(String userId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, userId);
    }

    private List<String> getQueuedUsers() {
        List<String> items = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        return items == null ? List.of() : items;
    }

    private void savePendingMatch(PendingMatch match) {
        try {
            redisTemplate.opsForValue().set(PENDING_MATCH_KEY_PREFIX + match.id(), objectMapper.writeValueAsString(match));
            redisTemplate.opsForValue().set(USER_TO_MATCH_KEY_PREFIX + match.userA(), match.id());
            redisTemplate.opsForValue().set(USER_TO_MATCH_KEY_PREFIX + match.userB(), match.id());
        } catch (Exception ignored) {
        }
    }

    private PendingMatch loadPendingMatch(String matchId) {
        String payload = redisTemplate.opsForValue().get(PENDING_MATCH_KEY_PREFIX + matchId);
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, PendingMatch.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void removePendingMatch(String matchId) {
        PendingMatch match = loadPendingMatch(matchId);
        redisTemplate.delete(PENDING_MATCH_KEY_PREFIX + matchId);
        if (match != null) {
            redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + match.userA());
            redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + match.userB());
        }
    }

    private String getUserToMatch(String userId) {
        return redisTemplate.opsForValue().get(USER_TO_MATCH_KEY_PREFIX + userId);
    }

    private void deleteUserToMatch(String userId) {
        redisTemplate.delete(USER_TO_MATCH_KEY_PREFIX + userId);
    }

    private void setRejectedPairCooldown(String userA, String userB) {
        String key = REJECTED_PAIR_KEY_PREFIX + pairKey(userA, userB);
        redisTemplate.opsForValue().set(key, "1", REJECTED_PAIR_COOLDOWN_MILLIS, TimeUnit.MILLISECONDS);
    }

    private boolean isRejectedPairCooldown(String userA, String userB) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REJECTED_PAIR_KEY_PREFIX + pairKey(userA, userB)));
    }

    private int queueSize() {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY);
        return size == null ? 0 : size.intValue();
    }

    private int pendingMatchesSize() {
        try {
            return redisTemplate.keys(PENDING_MATCH_KEY_PREFIX + "*").size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<PendingMatch> getAllPendingMatches() {
        Set<String> keys = redisTemplate.keys(PENDING_MATCH_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
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

    public synchronized void connect(WebSocketSession session, Profile profile) {
        String userId = profile.userId();
        WebSocketSession previousSession = sessions.put(userId, session);
        if (previousSession != null && previousSession.isOpen() && !previousSession.getId().equals(session.getId())) {
            try {
                previousSession.close();
            } catch (IOException ignored) {
            }
        }

        sessionToUserId.put(session.getId(), userId);
        profiles.put(userId, profile);
        saveProfile(profile);
        send(userId, "connected", Map.of("userId", userId, "profile", profile));
    }

    public synchronized void disconnectSession(String sessionId) {
        String userId = sessionToUserId.remove(sessionId);
        WebSocketSession activeSession = userId == null ? null : sessions.get(userId);
        if (activeSession != null && activeSession.getId().equals(sessionId)) {
            disconnect(userId);
        }
    }

    private synchronized void disconnect(String userId) {
        sessions.remove(userId);
        removeFromQueue(userId);
        deleteProfile(userId);

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

        broadcastQueueStates("Queue updated");
    }

    public synchronized void handle(WebSocketSession session, ClientMessage message) {
        String type = message.getType() == null ? "" : message.getType();
        String userId = sessionToUserId.get(session.getId());
        if (userId == null) {
            return;
        }

        try {
            switch (type) {
                case "ping" -> send(userId, "pong", Map.of("timestamp", Instant.now().toEpochMilli()));
                case "updateProfile" -> updateProfile(userId, message.getProfile());
                case "joinQueue" -> joinQueue(userId, message.getProfile());
                case "leaveQueue" -> leaveQueue(userId);
                case "acceptMatch" -> acceptMatch(userId, message.getMatchId());
                case "rejectMatch" -> rejectMatch(userId, message.getMatchId());
                case "signal" -> forwardSignal(userId, message);
                case "continueCall" -> continueCall(userId, message.getRoomId());
                case "endCall" -> endCall(userId, message.getRoomId());
                default -> send(userId, "error", Map.of("message", "Unsupported message type: " + type));
            }
        } catch (Exception exception) {
            send(userId, "error", Map.of("message", "Realtime action failed. Please try again."));
        }
    }

    public synchronized Map<String, Object> snapshot() {
        return Map.of(
                "onlineUsers", sessions.size(),
                "profiles", profiles.size(),
                "queuedUsers", queueSize(),
                "pendingMatches", pendingMatchesSize(),
                "activeRooms", activeRooms.size()
        );
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

    private void joinQueue(String userId, Profile profile) {
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
        send(userId, "queue-status", new QueueStatusPayload(true, queueSize(), "Searching for a relevant match"));
        tryMatchQueuedUsers();
        broadcastQueueStates("Searching for a relevant match");
    }

    private void leaveQueue(String userId) {
        removeFromQueue(userId);
        send(userId, "queue-status", new QueueStatusPayload(false, queueSize(), "Left the queue"));
        broadcastQueueStates("Queue updated");
    }

    private void acceptMatch(String userId, String matchId) {
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
            callSessions.put(roomId, new CallSession(endsAt));
            String initiatorUserId = match.userA().compareTo(match.userB()) <= 0 ? match.userA() : match.userB();

            send(match.userA(), "call-started", new CallStartedPayload(match.id(), roomId, profiles.get(match.userB()), initiatorUserId, endsAt));
            send(match.userB(), "call-started", new CallStartedPayload(match.id(), roomId, profiles.get(match.userA()), initiatorUserId, endsAt));
            scheduler.schedule(() -> expireCall(roomId, endsAt), CALL_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
            broadcastQueueStates("Queue updated");
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
        broadcastQueueStates("Searching for a relevant match");
    }

    private void expireMatch(String matchId) {
        synchronized (this) {
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
            broadcastQueueStates("Searching for a relevant match");
        }
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

    private void endCall(String userId, String roomId) {
        Set<String> participants = activeRooms.get(roomId);
        if (participants == null || !participants.contains(userId)) {
            return;
        }

        endCall(roomId, "Session ended", null);
        broadcastQueueStates("Queue updated");
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

    private synchronized void expireCall(String roomId, long expectedEndsAt) {
        CallSession callSession = callSessions.get(roomId);
        if (callSession == null
                || callSession.continueUntilDisconnected
                || callSession.endsAtEpochMillis != expectedEndsAt) {
            return;
        }

        endCall(roomId, "Call time ended", null);
        broadcastQueueStates("Queue updated");
    }

    private void endCall(String roomId, String reason, String excludedUserId) {
        Set<String> participants = activeRooms.remove(roomId);
        callSessions.remove(roomId);
        if (participants == null) {
            return;
        }

        for (String participant : participants) {
            if (!participant.equals(excludedUserId)) {
                send(participant, "call-ended", Map.of("roomId", roomId, "reason", reason));
            }
        }
    }

    private void tryMatchQueuedUsers() {
        cleanupPairCooldowns();

        boolean madeMatch;
        do {
            madeMatch = false;
            List<String> waitingUsers = getQueuedUsers();
            Set<String> queuedUsers = new HashSet<>(waitingUsers);

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

                    if (isCompatible(userA, userB)
                            || noPreferredPairExists(waitingUsers, queuedUsers) && canFallbackMatch(userA, userB)) {
                        removeFromQueue(userA);
                        removeFromQueue(userB);
                        queuedUsers.remove(userA);
                        queuedUsers.remove(userB);
                        createPendingMatch(userA, userB);
                        madeMatch = true;
                        break;
                    }
                }

                if (madeMatch) {
                    break;
                }
            }
        } while (madeMatch);
    }

    private void createPendingMatch(String userA, String userB) {
        String matchId = "match-" + UUID.randomUUID();
        long expiresAt = Instant.now().toEpochMilli() + MATCH_ACCEPT_WINDOW_MILLIS;
        PendingMatch match = new PendingMatch(matchId, userA, userB, expiresAt);

        savePendingMatch(match);

        send(userA, "match-offer", new MatchOfferPayload(matchId, profiles.get(userB), expiresAt));
        send(userB, "match-offer", new MatchOfferPayload(matchId, profiles.get(userA), expiresAt));

        scheduler.schedule(() -> expireMatch(matchId), MATCH_ACCEPT_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
    }

    private boolean isCompatible(String userA, String userB) {
        if (isRejectedPairCooldown(userA, userB)) {
            return false;
        }

        Profile profileA = profiles.get(userA);
        Profile profileB = profiles.get(userB);

        if (profileA == null || profileB == null) {
            return false;
        }

        boolean aWantsB = lookingForMatchesRole(profileA.lookingFor(), profileB.role());
        boolean bWantsA = lookingForMatchesRole(profileB.lookingFor(), profileA.role());
        boolean aOpen = containsOpenTarget(profileA.lookingFor());
        boolean bOpen = containsOpenTarget(profileB.lookingFor());

        return (aWantsB && (bWantsA || bOpen)) || (bWantsA && aOpen);
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

    private void requeueIfAvailable(String userId) {
        if (sessions.containsKey(userId) && profiles.containsKey(userId) && findRoomForUser(userId) == null) {
            addToQueue(userId);
            send(userId, "queue-status", new QueueStatusPayload(true, queueSize(), "Searching for a relevant match"));
        }
    }

    private void broadcastQueueStates(String message) {
        List<String> queuedUsers = getQueuedUsers();
        int size = queueSize();
        for (String userId : queuedUsers) {
            send(userId, "queue-status", new QueueStatusPayload(true, size, message));
        }
    }

    private String findRoomForUser(String userId) {
        for (Map.Entry<String, Set<String>> entry : activeRooms.entrySet()) {
            if (entry.getValue().contains(userId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void cleanupPairCooldowns() {
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
        private final String id;
        private final String userA;
        private final String userB;
        private final long expiresAtEpochMillis;
        private final Set<String> acceptedUsers = new HashSet<>();

        private PendingMatch(String id, String userA, String userB, long expiresAtEpochMillis) {
            this.id = id;
            this.userA = userA;
            this.userB = userB;
            this.expiresAtEpochMillis = expiresAtEpochMillis;
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
}
