package com.pulso.backend.connection.application;

import com.pulso.backend.config.ApiMessages;

public class ConnectionIntentConflictException extends RuntimeException {

    public ConnectionIntentConflictException() {
        super(ApiMessages.CONNECTION_INTENT_CONFLICT_DETAIL);
    }
}
