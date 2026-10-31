package com.pulso.backend.connection.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;

@Service
public class AcceptConnectionIntent {

    private final ConnectionIntentAcceptanceRepository repository;

    public AcceptConnectionIntent(ConnectionIntentAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AcceptedConnectionConversation execute(
            UUID connectionIntentId,
            UUID authenticatedProfileId) {
        ConnectionIntent connectionIntent = repository.findByIdForUpdate(connectionIntentId)
                .orElseThrow(ConnectionIntentNotFoundException::new);

        if (!repository.hasActiveProjectMembership(
                connectionIntent.projectId(),
                authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.membershipRequired();
        }

        if (connectionIntent.wasAcceptedBy(authenticatedProfileId)) {
            return repository.findConversation(connectionIntent.id())
                    .orElseThrow(ConnectionIntentConflictException::new);
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

        return repository.acceptAndCreateConversation(
                connectionIntent,
                authenticatedProfileId);
    }
}
