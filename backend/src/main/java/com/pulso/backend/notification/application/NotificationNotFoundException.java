package com.pulso.backend.notification.application;

import com.pulso.backend.config.ApiMessages;

/**
 * The notification does not exist or belongs to another person. Both cases look
 * the same so an id reveals nothing about other people's notifications.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super(ApiMessages.NOTIFICATION_NOT_FOUND_DETAIL);
    }
}
