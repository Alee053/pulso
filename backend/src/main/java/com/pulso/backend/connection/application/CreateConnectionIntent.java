package com.pulso.backend.connection.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;
import com.pulso.backend.connection.domain.ConnectionIntentValidationException;

@Service
public class CreateConnectionIntent {

    private final ConnectionIntentRepository repository;

    public CreateConnectionIntent(ConnectionIntentRepository repository) {
        this.repository = repository;
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
        return repository.createPending(senderProfileId, projectId, opportunityId, parsedReason, message);
    }
}
