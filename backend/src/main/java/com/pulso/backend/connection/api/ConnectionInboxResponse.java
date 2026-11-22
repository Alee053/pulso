package com.pulso.backend.connection.api;

import java.util.UUID;

import com.pulso.backend.connection.domain.ConnectionInboxEntry;

record ConnectionInboxResponse(
        ConnectionIntentResponse request,
        String projectName,
        String opportunityTitle,
        UUID conversationId) {

    static ConnectionInboxResponse from(ConnectionInboxEntry entry) {
        return new ConnectionInboxResponse(
                ConnectionIntentResponse.from(entry.intent()),
                entry.projectName(),
                entry.opportunityTitle(),
                entry.conversationId());
    }
}
