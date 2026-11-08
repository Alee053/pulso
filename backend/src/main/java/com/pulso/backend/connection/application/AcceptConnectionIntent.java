package com.pulso.backend.connection.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.notification.application.NotificationPublisher;

@Service
public class AcceptConnectionIntent {

    private final ConnectionIntentAcceptanceRepository repository;
    private final NotificationPublisher notifications;

    public AcceptConnectionIntent(
            ConnectionIntentAcceptanceRepository repository,
            NotificationPublisher notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    @Transactional
    public AcceptedConnectionConversation execute(
            UUID connectionIntentId,
            UUID authenticatedProfileId) {
        ConnectionIntent connectionIntent = repository.findByIdForUpdate(connectionIntentId)
                .orElseThrow(ConnectionIntentNotFoundException::new);

        if (connectionIntent.wasAcceptedBy(authenticatedProfileId)) {
            return repository.findConversation(connectionIntent.id())
                    .orElseThrow(ConnectionIntentConflictException::new);
        }
        if (!repository.hasActiveProjectMembership(
                connectionIntent.projectId(),
                authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.membershipRequired();
        }
        if (!connectionIntent.isPending()
                || connectionIntent.fromProfileId().equals(authenticatedProfileId)) {
            throw new ConnectionIntentConflictException();
        }
        if (repository.profilesAreBlocked(
                connectionIntent.fromProfileId(),
                authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.profilesBlocked();
        }

        AcceptedConnectionConversation accepted = repository.acceptAndCreateConversation(
                connectionIntent,
                authenticatedProfileId);
        notifications.connectionAccepted(
                accepted.connectionIntentId(),
                accepted.projectId(),
                accepted.conversationId(),
                accepted.initiatorProfileId(),
                accepted.responderProfileId());
        return accepted;
    }
}
