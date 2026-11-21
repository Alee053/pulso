package com.pulso.backend.connection.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.connection.domain.ConnectionIntentValidationException;
import com.pulso.backend.notification.application.NotificationPublisher;

@Service
public class CreateConnectionIntent {

    private final ConnectionIntentRepository repository;
    private final NotificationPublisher notifications;

    public CreateConnectionIntent(
            ConnectionIntentRepository repository,
            NotificationPublisher notifications) {
        this.repository = repository;
        this.notifications = notifications;
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

        ConnectionIntentReason parsedReason = ConnectionIntentReason.parse(reason);
        ConnectionIntent created = repository.createPending(
                senderProfileId, projectId, opportunityId, parsedReason, message);
        notifications.connectionRequested(created.id(), created.projectId(), senderProfileId);
        return created;
    }
}
