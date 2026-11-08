package com.pulso.backend.notification.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the notices a use case asked for, so tests can assert on them without a database.
 */
public final class RecordingNotificationPublisher implements NotificationPublisher {

    public final List<ConnectionRequested> requests = new ArrayList<>();
    public final List<ConnectionAccepted> acceptances = new ArrayList<>();
    public final List<MessageSent> messages = new ArrayList<>();

    @Override
    public void connectionRequested(UUID connectionIntentId, UUID projectId, UUID senderProfileId) {
        requests.add(new ConnectionRequested(connectionIntentId, projectId, senderProfileId));
    }

    @Override
    public void connectionAccepted(
            UUID connectionIntentId,
            UUID projectId,
            UUID conversationId,
            UUID initiatorProfileId,
            UUID responderProfileId) {
        acceptances.add(new ConnectionAccepted(
                connectionIntentId, projectId, conversationId, initiatorProfileId, responderProfileId));
    }

    @Override
    public void messageSent(UUID conversationId, UUID messageId, UUID senderProfileId) {
        messages.add(new MessageSent(conversationId, messageId, senderProfileId));
    }

    public boolean isEmpty() {
        return requests.isEmpty() && acceptances.isEmpty() && messages.isEmpty();
    }

    public record ConnectionRequested(UUID connectionIntentId, UUID projectId, UUID senderProfileId) {
    }

    public record ConnectionAccepted(
            UUID connectionIntentId,
            UUID projectId,
            UUID conversationId,
            UUID initiatorProfileId,
            UUID responderProfileId) {
    }

    public record MessageSent(UUID conversationId, UUID messageId, UUID senderProfileId) {
    }
}
