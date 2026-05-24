package com.speedlink.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsResponseFilter extends OncePerRequestFilter {
    private final List<String> allowedOrigins;
    private final List<String> allowedOriginPatterns;

    public CorsResponseFilter(
            @Value("${speedlink.cors.allowed-origins}") String allowedOrigins,
            @Value("${speedlink.cors.allowed-origin-patterns}") String allowedOriginPatterns
    ) {
        this.allowedOrigins = splitValues(allowedOrigins);
        this.allowedOriginPatterns = splitValues(allowedOriginPatterns);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && isAllowedOrigin(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS,HEAD");
            response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,Accept,Origin,X-Requested-With");
            response.setHeader("Access-Control-Expose-Headers", "Authorization");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        return allowedOriginPatterns.stream().anyMatch(pattern -> matchesPattern(origin, pattern));
    }

    private boolean matchesPattern(String origin, String pattern) {
        if (!pattern.contains("*")) {
            return origin.equals(pattern);
        }
        String regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return origin.matches(regex);
    }

    private List<String> splitValues(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .map(value -> value.replace("\"", ""))
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
