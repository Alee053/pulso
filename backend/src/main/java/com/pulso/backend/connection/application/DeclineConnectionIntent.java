package com.pulso.backend.connection.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pulso.backend.connection.domain.ConnectionIntent;

@Service
public class DeclineConnectionIntent {
    private final ConnectionIntentAcceptanceRepository repository;

    public DeclineConnectionIntent(ConnectionIntentAcceptanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConnectionIntent execute(UUID connectionIntentId, UUID authenticatedProfileId) {
        ConnectionIntent intent = repository.findByIdForUpdate(connectionIntentId)
                .orElseThrow(ConnectionIntentNotFoundException::new);

        if (!repository.hasActiveProjectMembership(intent.projectId(), authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.membershipRequired();
        }
        if (!intent.isPending() || intent.fromProfileId().equals(authenticatedProfileId)) {
            throw new ConnectionIntentConflictException();
        }

        ConnectionIntent declined = intent.decline(authenticatedProfileId);
        repository.updateStatus(declined, authenticatedProfileId);
        return declined;
    }
}