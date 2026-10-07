package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pulso.backend.project.domain.ProjectLifecycleActor;
import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;
import com.pulso.backend.project.domain.ProjectValidationException;

class ChangeProjectLifecycleTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final Instant ARCHIVED_AT = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T12:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(ARCHIVED_AT, ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource({
            "OWNER, 11111111-1111-1111-1111-111111111111",
            "ADMIN, 99999999-9999-9999-9999-999999999999"
    })
    void activeOwnerAndAdminCanRequestPublication(String role, UUID profileId) {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, profileId, "DRAFT", role, "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        ProjectLifecycleState next = useCase.requestPublication(PROJECT_ID, profileId);

        assertThat(next.status()).isEqualTo("PENDING_REVIEW");
        assertThat(next.publishedAt()).isNull();
        assertThat(next.archivedAt()).isNull();
        assertThat(repository.requestedProfileId).isEqualTo(profileId);
        assertThat(repository.updatedState.status()).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER, 11111111-1111-1111-1111-111111111111",
            "ADMIN, 99999999-9999-9999-9999-999999999999"
    })
    void activeOwnerAndAdminCanArchive(String role, UUID profileId) {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, profileId, "PUBLISHED", role, "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        ProjectLifecycleState next = useCase.archive(PROJECT_ID, profileId);

        assertThat(next.status()).isEqualTo("ARCHIVED");
        assertThat(next.archivedAt()).isEqualTo(ARCHIVED_AT);
        assertThat(next.publishedAt()).isNull();
        assertThat(repository.updatedState.status()).isEqualTo("ARCHIVED");
    }

    @ParameterizedTest
    @CsvSource({
            "MEMBER, ACTIVE",
            "OWNER, INVITED",
            "ADMIN, LEFT",
            "OWNER, REMOVED"
    })
    void rejectsMembersWithoutActiveOwnerOrAdminAuthorization(
            String role,
            String membershipStatus) {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, "DRAFT", role, membershipStatus));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> useCase.requestPublication(PROJECT_ID, PROFILE_ID))
                .isInstanceOf(ProjectLifecycleAuthorizationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> useCase.archive(PROJECT_ID, PROFILE_ID))
                .isInstanceOf(ProjectLifecycleAuthorizationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThat(repository.updatedState).isNull();
    }

    @Test
    void rejectsAnExternalProfileWithoutRevealingProjectState() {
        CapturingRepository repository = new CapturingRepository(null);
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> useCase.requestPublication(PROJECT_ID, OTHER_PROFILE_ID))
                .isInstanceOf(ProjectLifecycleAuthorizationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThat(repository.requestedProfileId).isEqualTo(OTHER_PROFILE_ID);
    }

    @Test
    void rejectsAProjectSnapshotThatDoesNotMatchTheRequestedProject() {
        CapturingRepository repository = new CapturingRepository(
                snapshot(OTHER_PROJECT_ID, PROFILE_ID, "DRAFT", "OWNER", "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> useCase.requestPublication(PROJECT_ID, PROFILE_ID))
                .isInstanceOf(ProjectLifecycleAuthorizationException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThat(repository.updatedState).isNull();
    }

    @Test
    void doesNotCreateAPublishedStateOrAcceptClientReviewData() {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, "DRAFT", "OWNER", "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        ProjectLifecycleState next = useCase.requestPublication(PROJECT_ID, PROFILE_ID);

        assertThat(next.status()).isEqualTo("PENDING_REVIEW");
        assertThat(next.status()).isNotEqualTo("PUBLISHED");
        assertThat(next.publishedAt()).isNull();
        assertThat(next.archivedAt()).isNull();
    }

    @Test
    void archivePreservesPublicationAuditDataAndSetsOnlyArchiveTime() {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, "PUBLISHED", "OWNER", "ACTIVE",
                        PUBLISHED_AT, null));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        ProjectLifecycleState next = useCase.archive(PROJECT_ID, PROFILE_ID);

        assertThat(next.status()).isEqualTo("ARCHIVED");
        assertThat(next.publishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(next.archivedAt()).isEqualTo(ARCHIVED_AT);
    }

    @Test
    void rejectsPublicationFromAnAlreadyPublishedProject() {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, "PUBLISHED", "OWNER", "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> useCase.requestPublication(PROJECT_ID, PROFILE_ID))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot request publication from PUBLISHED.");
        assertThat(repository.updatedState).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHIVED", "SUSPENDED"})
    void rejectsArchiveFromArchivedOrSuspendedProjectsWithoutPersisting(String status) {
        CapturingRepository repository = new CapturingRepository(
                snapshot(PROJECT_ID, PROFILE_ID, status, "OWNER", "ACTIVE"));
        ChangeProjectLifecycle useCase = new ChangeProjectLifecycle(repository, FIXED_CLOCK);

        assertThatThrownBy(() -> useCase.archive(PROJECT_ID, PROFILE_ID))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot archive from " + status + ".");
        assertThat(repository.updatedState).isNull();
    }

    private static ProjectLifecycleSnapshot snapshot(
            UUID projectId,
            UUID profileId,
            String status,
            String role,
            String membershipStatus) {
        return snapshot(projectId, profileId, status, role, membershipStatus, null, null);
    }

    private static ProjectLifecycleSnapshot snapshot(
            UUID projectId,
            UUID profileId,
            String status,
            String role,
            String membershipStatus,
            Instant publishedAt,
            Instant archivedAt) {
        return new ProjectLifecycleSnapshot(
                projectId,
                profileId,
                new ProjectLifecycleState(status, publishedAt, archivedAt),
                new ProjectLifecycleActor(role, membershipStatus));
    }

    private static final class CapturingRepository implements ProjectLifecycleRepository {

        private final ProjectLifecycleSnapshot snapshot;
        private UUID requestedProfileId;
        private ProjectLifecycleState updatedState;

        private CapturingRepository(ProjectLifecycleSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<ProjectLifecycleSnapshot> findForActor(UUID projectId, UUID profileId) {
            requestedProfileId = profileId;
            return Optional.ofNullable(snapshot);
        }

        @Override
        public int updateLifecycle(
                UUID projectId,
                UUID profileId,
                String expectedStatus,
                ProjectLifecycleState nextState) {
            updatedState = nextState;
            return 1;
        }
    }
}
