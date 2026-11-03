package com.pulso.backend.connection.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;

public record ConnectionIntentResponse(
        UUID id,
        UUID projectId,
        UUID opportunityId,
        UUID senderProfileId,
        ConnectionIntentReason reason,
        String message,
        String status,
        OffsetDateTime createdAt) {

    public static ConnectionIntentResponse from(ConnectionIntent intent) {
        return new ConnectionIntentResponse(
                intent.id(),
                intent.projectId(),
                intent.opportunityId(),
                intent.fromProfileId(),
                intent.reason(),
                intent.message(),
                intent.status(),
                intent.createdAt());
    }
}
