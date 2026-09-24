package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.Set;

import com.pulso.backend.config.ApiMessages;

public final class ProjectLifecycle {

    private static final Set<String> PUBLICATION_REQUEST_SOURCES = Set.of("DRAFT");
    private static final Set<String> ARCHIVE_SOURCES = Set.of(
            "DRAFT",
            "PENDING_REVIEW",
            "PUBLISHED",
            "REJECTED");
    private static final Set<String> TEAM_ROLES = Set.of("OWNER", "ADMIN");

    private ProjectLifecycle() {
    }

    public static ProjectLifecycleState requestPublication(
            ProjectLifecycleState current,
            ProjectLifecycleActor actor) {
        requireCurrent(current);
        requireTeamActor(actor);
        requireAllowedSource(
                current.status(),
                PUBLICATION_REQUEST_SOURCES,
                ApiMessages.CANNOT_REQUEST_PUBLICATION_FROM);
        return new ProjectLifecycleState(
                "PENDING_REVIEW",
                current.publishedAt(),
                current.archivedAt());
    }

    public static ProjectLifecycleState archive(
            ProjectLifecycleState current,
            ProjectLifecycleActor actor,
            Instant archivedAt) {
        requireCurrent(current);
        requireTeamActor(actor);
        if (archivedAt == null) {
            throw new ProjectValidationException(ApiMessages.ARCHIVED_AT_REQUIRED);
        }
        requireAllowedSource(current.status(), ARCHIVE_SOURCES, ApiMessages.CANNOT_ARCHIVE_FROM);
        return new ProjectLifecycleState("ARCHIVED", current.publishedAt(), archivedAt);
    }

    private static void requireCurrent(ProjectLifecycleState current) {
        if (current == null) {
            throw new ProjectValidationException(ApiMessages.LIFECYCLE_STATE_REQUIRED);
        }
    }

    private static void requireTeamActor(ProjectLifecycleActor actor) {
        if (actor == null) {
            throw new ProjectValidationException(ApiMessages.MEMBERSHIP_PERMISSION_DETAIL);
        }
        if (!"ACTIVE".equals(actor.membershipStatus())) {
            throw new ProjectValidationException(ApiMessages.MEMBERSHIP_STATUS_MUST_BE_ACTIVE);
        }
        if (!TEAM_ROLES.contains(actor.role())) {
            throw new ProjectValidationException(ApiMessages.ROLE_MUST_BE_OWNER_OR_ADMIN);
        }
    }

    private static void requireAllowedSource(
            String status,
            Set<String> allowedSources,
            String messageFormat) {
        if (!allowedSources.contains(status)) {
            throw new ProjectValidationException(String.format(messageFormat, status));
        }
    }
}
