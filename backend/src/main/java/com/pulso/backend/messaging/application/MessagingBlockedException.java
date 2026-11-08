package com.pulso.backend.messaging.application;

import com.pulso.backend.config.ApiMessages;

public class MessagingBlockedException extends RuntimeException {

    public MessagingBlockedException() {
        super(ApiMessages.MESSAGING_BLOCKED_DETAIL);
    }
}
