package com.pulso.backend.interaction.domain;

import java.time.OffsetDateTime;

public record DiscoveryFeedItem(
        ProjectSummary project,
        boolean saved,
        ProjectFeedbackType feedbackType,
        int preferenceScore,
        OffsetDateTime publishedAt) {
}
