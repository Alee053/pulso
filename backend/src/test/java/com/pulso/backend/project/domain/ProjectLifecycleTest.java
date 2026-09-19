package com.pulso.backend.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-18T21:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void activeOwnerCanRequestPublicationFromDraftWithoutTouchingPublishedAt() {
        ProjectLifecycleState next = ProjectLifecycle.requestPublication(
                draft(),
                owner("ACTIVE"));

        assertThat(next.status()).isEqualTo("PENDING_REVIEW");
        assertThat(next.publishedAt()).isNull();
        assertThat(next.archivedAt()).isNull();
        assertThat(next.isPubliclyReadable()).isFalse();
    }

    @Test
    void activeAdminCanRequestPublicationFromDraft() {
        ProjectLifecycleState next = ProjectLifecycle.requestPublication(
                draft(),
                admin("ACTIVE"));

        assertThat(next.status()).isEqualTo("PENDING_REVIEW");
        assertThat(next.publishedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "PUBLISHED", "REJECTED", "ARCHIVED", "SUSPENDED"})
    void rejectsPublicationRequestsOutsideDraft(String status) {
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(
                state(status, PUBLISHED_AT, null),
                owner("ACTIVE")))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot request publication from " + status + ".");
    }

    @Test
    void teamActionsNeverProducePublished() {
        ProjectLifecycleState afterRequest = ProjectLifecycle.requestPublication(
                draft(),
                owner("ACTIVE"));
        ProjectLifecycleState afterArchive = ProjectLifecycle.archive(
                draft(),
                owner("ACTIVE"),
                NOW);

        assertThat(afterRequest.status()).isNotEqualTo("PUBLISHED");
        assertThat(afterArchive.status()).isEqualTo("ARCHIVED");
    }

    @ParameterizedTest
    @CsvSource({
            "DRAFT, ",
            "PENDING_REVIEW, ",
            "PUBLISHED, 2026-08-01T12:00:00Z",
            "REJECTED, "
    })
    void activeOwnerCanArchiveAllowedStatusesAndSetsArchivedAt(String status, String publishedAt) {
        Instant existingPublishedAt = publishedAt == null || publishedAt.isBlank()
                ? null
                : Instant.parse(publishedAt);

        ProjectLifecycleState next = ProjectLifecycle.archive(
                state(status, existingPublishedAt, null),
                owner("ACTIVE"),
                NOW);

        assertThat(next.status()).isEqualTo("ARCHIVED");
        assertThat(next.archivedAt()).isEqualTo(NOW);
        assertThat(next.publishedAt()).isEqualTo(existingPublishedAt);
        assertThat(next.isPubliclyReadable()).isFalse();
    }

    @Test
    void archivingAPublishedProjectKeepsTheOriginalPublishedAt() {
        ProjectLifecycleState next = ProjectLifecycle.archive(
                state("PUBLISHED", PUBLISHED_AT, null),
                admin("ACTIVE"),
                NOW);

        assertThat(next.status()).isEqualTo("ARCHIVED");
        assertThat(next.publishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(next.archivedAt()).isEqualTo(NOW);
    }

    @Test
    void archivedProjectsAreNotPubliclyReadableAndCannotBeReopened() {
        ProjectLifecycleState archived = ProjectLifecycle.archive(
                draft(),
                owner("ACTIVE"),
                NOW);

        assertThat(archived.isPubliclyReadable()).isFalse();
        assertThatThrownBy(() -> ProjectLifecycle.archive(archived, owner("ACTIVE"), NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot archive from ARCHIVED.");
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(archived, owner("ACTIVE")))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot request publication from ARCHIVED.");
    }

    @Test
    void onlyPublishedProjectsArePubliclyReadable() {
        assertThat(state("PUBLISHED", PUBLISHED_AT, null).isPubliclyReadable()).isTrue();
        assertThat(draft().isPubliclyReadable()).isFalse();
        assertThat(state("PENDING_REVIEW", null, null).isPubliclyReadable()).isFalse();
        assertThat(state("REJECTED", null, null).isPubliclyReadable()).isFalse();
        assertThat(state("ARCHIVED", PUBLISHED_AT, NOW).isPubliclyReadable()).isFalse();
        assertThat(state("SUSPENDED", PUBLISHED_AT, null).isPubliclyReadable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVITED", "LEFT", "REMOVED"})
    void rejectsInactiveMembershipsForTeamActions(String membershipStatus) {
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(
                draft(),
                owner(membershipStatus)))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("membershipStatus must be ACTIVE.");
        assertThatThrownBy(() -> ProjectLifecycle.archive(
                draft(),
                admin(membershipStatus),
                NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("membershipStatus must be ACTIVE.");
    }

    @Test
    void rejectsMembersWhoAreNotOwnerOrAdmin() {
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(
                draft(),
                member("ACTIVE")))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("role must be OWNER or ADMIN.");
        assertThatThrownBy(() -> ProjectLifecycle.archive(
                draft(),
                member("ACTIVE"),
                NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("role must be OWNER or ADMIN.");
    }

    @Test
    void rejectsMissingMembership() {
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(draft(), null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> ProjectLifecycle.archive(draft(), null, NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void rejectsMissingLifecycleState() {
        assertThatThrownBy(() -> ProjectLifecycle.requestPublication(null, owner("ACTIVE")))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("project lifecycle state is required.");
        assertThatThrownBy(() -> ProjectLifecycle.archive(null, owner("ACTIVE"), NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("project lifecycle state is required.");
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> new ProjectLifecycleState("GONE", null, null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("status contains unknown value 'GONE'.");
    }

    @Test
    void rejectsUnknownRoleAndMembershipStatus() {
        assertThatThrownBy(() -> new ProjectLifecycleActor("EDITOR", "ACTIVE"))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("role contains unknown value 'EDITOR'.");
        assertThatThrownBy(() -> new ProjectLifecycleActor("OWNER", "FROZEN"))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("membershipStatus contains unknown value 'FROZEN'.");
    }

    @Test
    void requestPublicationDoesNotInventAPublishedAt() {
        Instant alreadyPublished = Instant.parse("2026-07-04T09:30:00Z");

        ProjectLifecycleState next = ProjectLifecycle.requestPublication(
                new ProjectLifecycleState("DRAFT", alreadyPublished, null),
                owner("ACTIVE"));

        assertThat(next.status()).isEqualTo("PENDING_REVIEW");
        assertThat(next.publishedAt()).isEqualTo(alreadyPublished);
        assertThat(next.publishedAt()).isNotEqualTo(NOW);
    }

    @Test
    void archiveRequiresTheAuditInstant() {
        assertThatThrownBy(() -> ProjectLifecycle.archive(draft(), owner("ACTIVE"), null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("archivedAt is required.");
    }

    @Test
    void cannotArchiveFromSuspended() {
        assertThatThrownBy(() -> ProjectLifecycle.archive(
                state("SUSPENDED", PUBLISHED_AT, null),
                owner("ACTIVE"),
                NOW))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot archive from SUSPENDED.");
    }

    private static ProjectLifecycleState draft() {
        return state("DRAFT", null, null);
    }

    private static ProjectLifecycleState state(String status, Instant publishedAt, Instant archivedAt) {
        return new ProjectLifecycleState(status, publishedAt, archivedAt);
    }

    private static ProjectLifecycleActor owner(String membershipStatus) {
        return new ProjectLifecycleActor("OWNER", membershipStatus);
    }

    private static ProjectLifecycleActor admin(String membershipStatus) {
        return new ProjectLifecycleActor("ADMIN", membershipStatus);
    }

    private static ProjectLifecycleActor member(String membershipStatus) {
        return new ProjectLifecycleActor("MEMBER", membershipStatus);
    }
}
