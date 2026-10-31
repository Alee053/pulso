package com.pulso.backend.connection.domain;

import java.util.Objects;
import java.util.UUID;

public record ConnectionIntent(
        UUID id,
        UUID fromProfileId,
        UUID projectId,
        Status status,
        UUID respondedByProfileId) {

    public ConnectionIntent {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(fromProfileId, "fromProfileId is required");
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(status, "status is required");
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean wasAcceptedBy(UUID profileId) {
        return status == Status.ACCEPTED && Objects.equals(respondedByProfileId, profileId);
    }

    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED,
        WITHDRAWN,
        EXPIRED,
        CONVERTED
    }
}
