package com.pulso.backend.connection.api;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;

public record AcceptedConnectionConversationResponse(
        UUID connectionIntentId,
        UUID conversationId,
        String conversationType,
        UUID projectId,
        String status,
        List<ParticipantResponse> participants) {

    public static AcceptedConnectionConversationResponse from(
            AcceptedConnectionConversation conversation) {
        return new AcceptedConnectionConversationResponse(
                conversation.connectionIntentId(),
                conversation.conversationId(),
                conversation.conversationType(),
                conversation.projectId(),
                conversation.status(),
                conversation.participants().stream()
                        .map(participant -> new ParticipantResponse(
                                participant.profileId(),
                                participant.role()))
                        .toList());
    }

    public record ParticipantResponse(UUID profileId, String role) {
    }
}
