package com.speedlink.app.model;

public record QueueStatusPayload(
        boolean inQueue,
        int queueSize,
        String message
) {
}
