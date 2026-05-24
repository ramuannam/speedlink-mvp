package com.speedlink.app.config;

import com.speedlink.app.websocket.SpeedLinkWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final SpeedLinkWebSocketHandler handler;
    private final String allowedOrigins;
    private final String allowedOriginPatterns;

    public WebSocketConfig(
            SpeedLinkWebSocketHandler handler,
            @Value("${speedlink.cors.allowed-origins}") String allowedOrigins,
            @Value("${speedlink.cors.allowed-origin-patterns}") String allowedOriginPatterns
    ) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(value -> value.replace("\"", ""))
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        String[] originPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .map(value -> value.replace("\"", ""))
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);

        registry.addHandler(handler, "/ws")
                .setAllowedOrigins(origins)
                .setAllowedOriginPatterns(originPatterns);
    }
}
