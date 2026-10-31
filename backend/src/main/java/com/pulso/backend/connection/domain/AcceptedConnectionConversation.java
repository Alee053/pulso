package com.pulso.backend.connection.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AcceptedConnectionConversation(
        UUID connectionIntentId,
        UUID conversationId,
        UUID projectId,
        UUID initiatorProfileId,
        UUID responderProfileId) {

    public AcceptedConnectionConversation {
        Objects.requireNonNull(connectionIntentId, "connectionIntentId is required");
        Objects.requireNonNull(conversationId, "conversationId is required");
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(initiatorProfileId, "initiatorProfileId is required");
        Objects.requireNonNull(responderProfileId, "responderProfileId is required");
    }

    public String conversationType() {
        return "PROJECT_CONTACT";
    }

    public String status() {
        return "ACTIVE";
    }

    public List<Participant> participants() {
        return List.of(
                new Participant(initiatorProfileId, "INITIATOR"),
                new Participant(responderProfileId, "PROJECT_MEMBER"));
    }

    public record Participant(UUID profileId, String role) {
        public Participant {
            Objects.requireNonNull(profileId, "profileId is required");
            Objects.requireNonNull(role, "role is required");
        }
    }
}
