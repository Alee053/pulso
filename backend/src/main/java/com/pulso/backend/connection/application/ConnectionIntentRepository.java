package com.pulso.backend.connection.application;

import java.util.UUID;

import com.pulso.backend.connection.domain.ConnectionIntent;
import com.pulso.backend.connection.domain.ConnectionIntentReason;

public interface ConnectionIntentRepository {

    ConnectionIntent createPending(
            UUID senderProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message);
}
