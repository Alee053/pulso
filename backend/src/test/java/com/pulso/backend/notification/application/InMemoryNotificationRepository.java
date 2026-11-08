package com.pulso.backend.notification.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.notification.domain.NotificationType;

/**
 * Fake that mirrors the repository contract: it only ever exposes a recipient's own
 * notifications, most recent first.
 */
public final class InMemoryNotificationRepository implements NotificationRepository {

    public final List<Notification> notifications = new ArrayList<>();
    public OffsetDateTime readClock = OffsetDateTime.parse("2026-11-08T18:00:00Z");

    public Notification add(UUID recipient, NotificationType type, String createdAt, boolean read) {
        OffsetDateTime created = OffsetDateTime.parse(createdAt);
        Notification notification = new Notification(
                UUID.randomUUID(), recipient, UUID.randomUUID(), type, type.title(), null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Map.of(),
                read ? created.plusMinutes(1) : null, created);
        notifications.add(notification);
        return notification;
    }

    @Override
    public List<Notification> findPage(
            UUID recipientProfileId,
            NotificationReadFilter filter,
            int offset,
            int limit) {
        return notifications.stream()
                .filter(notification -> notification.recipientProfileId().equals(recipientProfileId))
                .filter(notification -> switch (filter) {
                    case ALL -> true;
                    case UNREAD -> !notification.isRead();
                    case READ -> notification.isRead();
                })
                .sorted(Comparator.comparing(Notification::createdAt)
                        .thenComparing(Notification::id)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countUnread(UUID recipientProfileId) {
        return notifications.stream()
                .filter(notification -> notification.recipientProfileId().equals(recipientProfileId))
                .filter(notification -> !notification.isRead())
                .count();
    }

    @Override
    public Optional<Notification> markRead(UUID notificationId, UUID recipientProfileId) {
        for (int index = 0; index < notifications.size(); index++) {
            Notification current = notifications.get(index);
            if (current.id().equals(notificationId)
                    && current.recipientProfileId().equals(recipientProfileId)) {
                Notification updated = new Notification(
                        current.id(), current.recipientProfileId(), current.actorProfileId(),
                        current.type(), current.title(), current.body(), current.projectId(),
                        current.conversationId(), current.connectionIntentId(), current.payload(),
                        current.readAt() == null ? readClock : current.readAt(), current.createdAt());
                notifications.set(index, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }
}
