package com.pulso.backend.notification.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationValidationException;

@Service
public class MarkNotificationRead {

    private final NotificationRepository repository;

    public MarkNotificationRead(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Idempotent: marking an already read notification keeps its first read time. */
    @Transactional
    public Notification execute(UUID notificationId, UUID authenticatedProfileId) {
        if (notificationId == null) {
            throw new NotificationValidationException(ApiMessages.NOTIFICATION_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new NotificationValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }

        return repository.markRead(notificationId, authenticatedProfileId)
                .orElseThrow(NotificationNotFoundException::new);
    }
}
