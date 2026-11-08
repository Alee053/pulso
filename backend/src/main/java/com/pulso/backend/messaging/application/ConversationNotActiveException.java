package com.pulso.backend.messaging.application;

import com.pulso.backend.config.ApiMessages;

public class ConversationNotActiveException extends RuntimeException {

    public ConversationNotActiveException() {
        super(ApiMessages.CONVERSATION_NOT_ACTIVE_DETAIL);
    }
}
