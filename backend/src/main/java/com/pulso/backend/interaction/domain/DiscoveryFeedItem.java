package com.pulso.backend.interaction.domain;

public record DiscoveryFeedItem(
        ProjectSummary project,
        boolean saved,
        ProjectFeedbackType feedbackType) {
}
