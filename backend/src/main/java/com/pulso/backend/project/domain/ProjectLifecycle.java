package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.Set;

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
                "cannot request publication from ");
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
            throw new ProjectValidationException("archivedAt is required.");
        }
        requireAllowedSource(current.status(), ARCHIVE_SOURCES, "cannot archive from ");
        return new ProjectLifecycleState("ARCHIVED", current.publishedAt(), archivedAt);
    }

    private static void requireCurrent(ProjectLifecycleState current) {
        if (current == null) {
            throw new ProjectValidationException("project lifecycle state is required.");
        }
    }

    private static void requireTeamActor(ProjectLifecycleActor actor) {
        if (actor == null) {
            throw new ProjectValidationException("active OWNER or ADMIN membership is required.");
        }
        if (!"ACTIVE".equals(actor.membershipStatus())) {
            throw new ProjectValidationException("membershipStatus must be ACTIVE.");
        }
        if (!TEAM_ROLES.contains(actor.role())) {
            throw new ProjectValidationException("role must be OWNER or ADMIN.");
        }
    }

    private static void requireAllowedSource(
            String status,
            Set<String> allowedSources,
            String messagePrefix) {
        if (!allowedSources.contains(status)) {
            throw new ProjectValidationException(messagePrefix + status + ".");
        }
    }
}
