package com.pulso.backend.connection.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.notification.application.NotificationPublisher;

@Service
public class DeclineConnectionIntent {
    private final ConnectionIntentAcceptanceRepository repository;
    private final NotificationPublisher notifications;

    public DeclineConnectionIntent(
            ConnectionIntentAcceptanceRepository repository,
            NotificationPublisher notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    @Transactional
    public ConnectionIntent execute(UUID connectionIntentId, UUID authenticatedProfileId) {
        ConnectionIntent intent = repository.findByIdForUpdate(connectionIntentId)
                .orElseThrow(ConnectionIntentNotFoundException::new);

        if (!repository.canManageProjectRequests(intent.projectId(), authenticatedProfileId)) {
            throw ConnectionIntentPermissionException.membershipRequired();
        }
        if (!intent.isPending() || intent.fromProfileId().equals(authenticatedProfileId)) {
            throw new ConnectionIntentConflictException();
        }
        if (repository.profilesAreBlocked(authenticatedProfileId, intent.fromProfileId())) {
            throw ConnectionIntentPermissionException.profilesBlocked();
        }

        ConnectionIntent declined = intent.decline(authenticatedProfileId);
        repository.updateStatus(declined, authenticatedProfileId);
        notifications.connectionDeclined(
                declined.id(), declined.projectId(), declined.fromProfileId(), authenticatedProfileId);
        return declined;
    }
}
