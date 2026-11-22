package com.pulso.backend.connection.domain;

import java.util.UUID;

public record ConnectionInboxEntry(
        ConnectionIntent intent,
        String projectName,
        String opportunityTitle,
        UUID conversationId) {
}
