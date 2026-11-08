package com.pulso.backend.messaging.domain;

import java.util.UUID;

/**
 * Persisted state of a conversation seen by one of its active participants.
 */
public record ConversationAccess(UUID conversationId, String status) {

    private static final String ACTIVE = "ACTIVE";

    public boolean acceptsMessages() {
        return ACTIVE.equals(status);
    }
}
