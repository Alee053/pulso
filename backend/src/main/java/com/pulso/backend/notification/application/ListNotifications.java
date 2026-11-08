package com.pulso.backend.notification.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.notification.domain.Notification;
import com.pulso.backend.notification.domain.NotificationReadFilter;
import com.pulso.backend.notification.domain.NotificationValidationException;

@Service
public class ListNotifications {

    private final NotificationRepository repository;

    public ListNotifications(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists only the authenticated person's notifications, most recent first. The
     * caller validates the page window against the configured limits.
     */
    @Transactional(readOnly = true)
    public NotificationPage execute(
            UUID authenticatedProfileId,
            NotificationReadFilter filter,
            int offset,
            int limit) {
        if (authenticatedProfileId == null) {
            throw new NotificationValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }

        List<Notification> window = repository.findPage(
                authenticatedProfileId,
                filter == null ? NotificationReadFilter.ALL : filter,
                offset,
                limit + 1);
        boolean hasMore = window.size() > limit;
        return new NotificationPage(
                hasMore ? window.subList(0, limit) : window,
                hasMore,
                repository.countUnread(authenticatedProfileId));
    }
}
