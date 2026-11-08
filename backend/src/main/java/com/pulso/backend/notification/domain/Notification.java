package com.pulso.backend.notification.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A notice for one recipient. It references the related records by id and never
 * carries private content such as message text.
 */
public record Notification(
        UUID id,
        UUID recipientProfileId,
        UUID actorProfileId,
        NotificationType type,
        String title,
        String body,
        UUID projectId,
        UUID conversationId,
        UUID connectionIntentId,
        Map<String, String> payload,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {

    public Notification {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public boolean isRead() {
        return readAt != null;
    }
}
