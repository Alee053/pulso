package com.pulso.backend.connection.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionIntent;

public interface ConnectionIntentAcceptanceRepository {

    Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId);

    boolean hasActiveProjectMembership(UUID projectId, UUID profileId);

    boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId);

    Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId);

    AcceptedConnectionConversation acceptAndCreateConversation(
            ConnectionIntent connectionIntent,
            UUID responderProfileId);
    
    void updateStatus(ConnectionIntent connectionIntent, UUID respondedByProfileId);

    java.util.List<ConnectionIntent> findPendingByProjectMembership(java.util.UUID profileId);

}
