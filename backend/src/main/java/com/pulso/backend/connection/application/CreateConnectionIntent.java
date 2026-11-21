package com.pulso.backend.connection.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.connection.domain.ConnectionIntentValidationException;
import com.pulso.backend.notification.application.NotificationPublisher;
import com.pulso.backend.profile.application.ProfileBlockRepository;

@Service
public class CreateConnectionIntent {

    private final ConnectionIntentRepository repository;
    private final NotificationPublisher notifications;
    private final ProfileBlockRepository profileBlockRepository; // NUEVO

    public CreateConnectionIntent(
            ConnectionIntentRepository repository,
            NotificationPublisher notifications,
            ProfileBlockRepository profileBlockRepository) { // NUEVO
        this.repository = repository;
        this.notifications = notifications;
        this.profileBlockRepository = profileBlockRepository; // NUEVO
    }

    @Transactional
    public ConnectionIntent execute(
            UUID projectId,
            UUID senderProfileId,
            UUID opportunityId,
            String reason,
            String message) {
        if (projectId == null) {
            throw new ConnectionIntentValidationException("projectId is required.");
        }
        if (senderProfileId == null) {
            throw new ConnectionIntentValidationException("senderProfileId is required.");
        }

        // NUEVA VALIDACIÓN: Verificar si el emisor está bloqueado con el dueño del proyecto
        UUID receiverProfileId = repository.findTargetProfileId(projectId, opportunityId);
        if (receiverProfileId != null && profileBlockRepository.isBlockedInAnyDirection(senderProfileId, receiverProfileId)) {
            throw new ConnectionBlockedException();
        }

        ConnectionIntentReason parsedReason = ConnectionIntentReason.parse(reason);
        ConnectionIntent created = repository.createPending(
                senderProfileId, projectId, opportunityId, parsedReason, message);
        notifications.connectionRequested(created.id(), created.projectId(), senderProfileId);
        return created;
    }
}
