package com.pulso.backend.notification.domain;

import java.util.Locale;

import com.pulso.backend.config.ApiMessages;

/**
 * Which notifications a listing returns: only the unread ones, only the read
 * ones, or both.
 */
public enum NotificationReadFilter {
    ALL,
    UNREAD,
    READ;

    public static NotificationReadFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new NotificationValidationException(ApiMessages.NOTIFICATION_STATUS_INVALID);
        }
    }
}
