package com.pulso.backend.connection.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public final class ConnectionIntent {

    private final UUID id;
    private final UUID fromProfileId;
    private final UUID projectId;
    private final UUID opportunityId;
    private final ConnectionIntentReason reason;
    private final String message;
    private final Status status;
    private final UUID respondedByProfileId;
    private final OffsetDateTime createdAt;

    private ConnectionIntent(
            UUID id,
            UUID fromProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message,
            Status status,
            UUID respondedByProfileId,
            OffsetDateTime createdAt) {
        this.id = required(id, "id");
        this.fromProfileId = required(fromProfileId, "fromProfileId");
        this.projectId = required(projectId, "projectId");
        this.opportunityId = opportunityId;
        this.reason = required(reason, "reason");
        this.message = normalizeOptionalMessage(message);
        this.status = required(status, "status");
        this.respondedByProfileId = respondedByProfileId;
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
        return restored(
                id,
                fromProfileId,
                projectId,
                opportunityId,
                reason,
                message,
                Status.PENDING.name(),
                null,
                createdAt);
    }

    public static ConnectionIntent restored(
            UUID id,
            UUID fromProfileId,
            UUID projectId,
            UUID opportunityId,
            ConnectionIntentReason reason,
            String message,
            String status,
            UUID respondedByProfileId,
            OffsetDateTime createdAt) {
        return new ConnectionIntent(
                id,
                fromProfileId,
                projectId,
                opportunityId,
                reason,
                message,
                parseStatus(status),
                respondedByProfileId,
                createdAt);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ConnectionIntentValidationException(field + " is required.");
        }
        return value;
    }

    private static Status parseStatus(String value) {
        try {
            return Status.valueOf(required(value, "status"));
        } catch (IllegalArgumentException exception) {
            throw new ConnectionIntentValidationException("status contains an unknown value.");
        }
    }

    private static String normalizeOptionalMessage(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean wasAcceptedBy(UUID profileId) {
        return (status == Status.ACCEPTED || status == Status.CONVERTED)
                && Objects.equals(respondedByProfileId, profileId);
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
        return status.name();
    }

    public UUID respondedByProfileId() {
        return respondedByProfileId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
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
