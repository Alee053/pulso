package com.pulso.backend.notification.api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.notification.domain.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        UUID actorProfileId,
        UUID projectId,
        UUID conversationId,
        UUID connectionIntentId,
        Map<String, String> payload,
        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type().name(),
                notification.title(),
                notification.body(),
                notification.actorProfileId(),
                notification.projectId(),
                notification.conversationId(),
                notification.connectionIntentId(),
                notification.payload(),
                notification.isRead(),
                notification.readAt(),
                notification.createdAt());
    }
}
