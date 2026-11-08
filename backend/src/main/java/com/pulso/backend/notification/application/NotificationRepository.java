package com.pulso.backend.notification.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;

public interface NotificationRepository {

    /** The recipient's notifications, most recent first. */
    List<Notification> findPage(
            UUID recipientProfileId,
            NotificationReadFilter filter,
            int offset,
            int limit);

    long countUnread(UUID recipientProfileId);

    /**
     * Marks the recipient's notification as read, keeping the first read time when
     * it already was. Empty when the notification does not exist or belongs to
     * someone else.
     */
    Optional<Notification> markRead(UUID notificationId, UUID recipientProfileId);
}
