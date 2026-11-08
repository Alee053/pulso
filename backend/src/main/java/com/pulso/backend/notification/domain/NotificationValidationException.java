package com.pulso.backend.notification.domain;

public class NotificationValidationException extends RuntimeException {

    public NotificationValidationException(String message) {
        super(message);
    }
}
