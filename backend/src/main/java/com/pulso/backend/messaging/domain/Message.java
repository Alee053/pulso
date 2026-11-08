package com.pulso.backend.messaging.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A message of a private conversation. A {@code DELETED} message never exposes
 * its body, whatever the persistence adapter loaded.
 */
public record Message(
        UUID id,
        UUID conversationId,
        UUID senderProfileId,
        MessageType type,
        MessageStatus status,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime editedAt,
        OffsetDateTime deletedAt) {

    public Message {
        if (status == MessageStatus.DELETED) {
            body = null;
        }
    }
}
