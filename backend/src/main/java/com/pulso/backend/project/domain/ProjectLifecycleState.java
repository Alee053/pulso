package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.Set;

public record ProjectLifecycleState(
        String status,
        Instant publishedAt,
        Instant archivedAt) {

    private static final Set<String> KNOWN_STATUSES = Set.of(
            "DRAFT",
            "PENDING_REVIEW",
            "PUBLISHED",
            "REJECTED",
            "ARCHIVED",
            "SUSPENDED");

    public ProjectLifecycleState {
        status = ProjectLifecycleValues.requireKnown(status, "status", KNOWN_STATUSES);
    }

    public boolean isPubliclyReadable() {
        return "PUBLISHED".equals(status);
    }
}
