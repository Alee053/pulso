package com.pulso.backend.messaging.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.pulso.backend.messaging.domain.Message;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderProfileId,
        String messageType,
        String status,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime editedAt,
        OffsetDateTime deletedAt) {

    static MessageResponse from(Message message) {
        return new MessageResponse(
                message.id(),
                message.conversationId(),
                message.senderProfileId(),
                message.type().name(),
                message.status().name(),
                message.body(),
                message.createdAt(),
                message.editedAt(),
                message.deletedAt());
    }
}
