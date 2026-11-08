package com.pulso.backend.notification.application;

import java.util.UUID;

/**
 * Records the notices produced by other features. Implementations must run in
 * the caller's transaction so an event and its notices are stored together.
 */
public interface NotificationPublisher {

    /**
     * Notifies every active member of the project, except the sender and anyone
     * separated from the sender by a block.
     */
    void connectionRequested(UUID connectionIntentId, UUID projectId, UUID senderProfileId);

    /** Tells the person who asked for the connection that it was accepted. */
    void connectionAccepted(
            UUID connectionIntentId,
            UUID projectId,
            UUID conversationId,
            UUID initiatorProfileId,
            UUID responderProfileId);

    /** Notifies the other active participants of the conversation. */
    void messageSent(UUID conversationId, UUID messageId, UUID senderProfileId);
}
