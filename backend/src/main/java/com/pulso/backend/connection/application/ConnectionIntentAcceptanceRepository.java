package com.pulso.backend.connection.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.connection.domain.AcceptedConnectionConversation;
import com.pulso.backend.connection.domain.ConnectionInboxEntry;
import com.pulso.backend.connection.domain.ConnectionInboxView;
import com.pulso.backend.connection.domain.ConnectionIntent;

public interface ConnectionIntentAcceptanceRepository {

    Optional<ConnectionIntent> findByIdForUpdate(UUID connectionIntentId);

    boolean canManageProjectRequests(UUID projectId, UUID profileId);

    boolean profilesAreBlocked(UUID firstProfileId, UUID secondProfileId);

    Optional<AcceptedConnectionConversation> findConversation(UUID connectionIntentId);

    AcceptedConnectionConversation acceptAndCreateConversation(
            ConnectionIntent connectionIntent,
            UUID responderProfileId);

    void updateStatus(ConnectionIntent connectionIntent, UUID respondedByProfileId);

    List<ConnectionInboxEntry> findInbox(UUID profileId, ConnectionInboxView view, int offset, int limit);
}
