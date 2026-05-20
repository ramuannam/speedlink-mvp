package com.speedlink.app.model;

import com.fasterxml.jackson.databind.JsonNode;

public record SignalEnvelope(
        String roomId,
        String fromUserId,
        JsonNode payload
) {
}
