package com.pulso.backend.connection.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ConnectionIntent {

    private static final String INITIAL_STATUS = "PENDING";

    private final UUID id;
    private final UUID fromProfileId;
    private final UUID projectId;
    private final UUID opportunityId;
    private final ConnectionIntentReason reason;
    private final String message;
    private final String status;
    private final OffsetDateTime createdAt;

    private ConnectionIntent(
            UUID id,
            UUID fromProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message,
            OffsetDateTime createdAt) {
        this.id = required(id, "id");
        this.fromProfileId = required(fromProfileId, "fromProfileId");
        this.projectId = required(projectId, "projectId");
        this.opportunityId = opportunityId;
        this.reason = required(reason, "reason");
        this.message = normalizeOptionalMessage(message);
        this.status = INITIAL_STATUS;
        this.createdAt = required(createdAt, "createdAt");
    }

    public static ConnectionIntent pending(
            UUID id,
            UUID fromProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message,
            OffsetDateTime createdAt) {
        return new ConnectionIntent(
                id, fromProfileId, projectId, opportunityId, reason, message, createdAt);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ConnectionIntentValidationException(field + " is required.");
        }
        return value;
    }

    private static String normalizeOptionalMessage(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public UUID id() {
        return id;
    }

    public UUID fromProfileId() {
        return fromProfileId;
    }

    public UUID projectId() {
        return projectId;
    }

    public UUID opportunityId() {
        return opportunityId;
    }

    public ConnectionIntentReason reason() {
        return reason;
    }

    public String message() {
        return message;
    }

    public String status() {
        return status;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
