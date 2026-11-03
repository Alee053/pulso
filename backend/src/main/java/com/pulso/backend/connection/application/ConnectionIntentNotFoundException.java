package com.pulso.backend.connection.application;

import com.pulso.backend.config.ApiMessages;

public class ConnectionIntentNotFoundException extends RuntimeException {

    public ConnectionIntentNotFoundException() {
        super(ApiMessages.CONNECTION_INTENT_NOT_FOUND_DETAIL);
    }
}
