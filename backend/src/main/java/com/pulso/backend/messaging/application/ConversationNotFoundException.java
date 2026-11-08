package com.pulso.backend.messaging.application;

import com.pulso.backend.config.ApiMessages;

/**
 * The conversation does not exist or the caller is not one of its active
 * participants. Both cases look the same so a conversation id reveals nothing.
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super(ApiMessages.CONVERSATION_NOT_FOUND_DETAIL);
    }
}
