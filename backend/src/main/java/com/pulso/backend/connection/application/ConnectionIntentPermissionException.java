package com.pulso.backend.connection.application;

import com.pulso.backend.config.ApiMessages;

public class ConnectionIntentPermissionException extends RuntimeException {

    private ConnectionIntentPermissionException(String message) {
        super(message);
    }

    public static ConnectionIntentPermissionException membershipRequired() {
        return new ConnectionIntentPermissionException(
                ApiMessages.CONNECTION_INTENT_MEMBERSHIP_REQUIRED);
    }

    public static ConnectionIntentPermissionException profilesBlocked() {
        return new ConnectionIntentPermissionException(
                ApiMessages.CONNECTION_INTENT_PROFILES_BLOCKED);
    }
}
