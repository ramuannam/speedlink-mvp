package com.speedlink.app.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speedlink.app.model.ClientMessage;
import com.speedlink.app.service.AuthService;
import com.speedlink.app.service.MatchingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class SpeedLinkWebSocketHandler extends TextWebSocketHandler {
    private final MatchingService matchingService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public SpeedLinkWebSocketHandler(MatchingService matchingService, AuthService authService, ObjectMapper objectMapper) {
        this.matchingService = matchingService;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = tokenFromUri(session.getUri());
        authService.authenticate(token)
                .ifPresentOrElse(
                        account -> matchingService.connect(session, account.toProfile()),
                        () -> closeUnauthenticated(session)
                );
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ClientMessage clientMessage = objectMapper.readValue(message.getPayload(), ClientMessage.class);
        matchingService.handle(session, clientMessage);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        matchingService.disconnectSession(session.getId());
    }

    private void closeUnauthenticated(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage("{\"type\":\"auth-required\",\"payload\":{\"message\":\"Login is required.\"}}"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Login is required"));
        } catch (Exception ignored) {
        }
    }

    private String tokenFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return "";
        }

        return Arrays.stream(uri.getQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].equals("token"))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse("");
    }
}
