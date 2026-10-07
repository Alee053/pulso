package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.ManagedProjectMember;
import com.pulso.backend.project.domain.MembershipOwnerInvariantException;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

class AdministerProjectMembershipTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MEMBER_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OWNER_MEMBERSHIP_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_MEMBERSHIP_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @ParameterizedTest
    @CsvSource({
            "OWNER, activate, LEFT, MEMBER, ACTIVE",
            "OWNER, activate, REMOVED, MEMBER, ACTIVE",
            "OWNER, changeRole, ACTIVE, ADMIN, ACTIVE",
            "OWNER, withdraw, ACTIVE, MEMBER, REMOVED",
            "ADMIN, activate, LEFT, MEMBER, ACTIVE",
            "ADMIN, activate, REMOVED, MEMBER, ACTIVE",
            "ADMIN, changeRole, ACTIVE, ADMIN, ACTIVE",
            "ADMIN, withdraw, ACTIVE, MEMBER, REMOVED"
    })
    void activeOwnerAndAdminCanAdministerAnotherMembership(
            String actorRole,
            String action,
            String initialStatus,
            String nextRole,
            String nextStatus) {
        ProjectMember target = member("MEMBER", initialStatus);
        UUID actorProfileId = "OWNER".equals(actorRole) ? OWNER_ID : ADMIN_ID;
        CapturingRepository repository = repository(
                new MembershipActor(PROJECT_ID, actorProfileId, actorRole, "ACTIVE"),
                target,
                List.of(ownerMembership(), target));

        ProjectMember next = service(repository).execute(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                actorProfileId,
                action,
                "changeRole".equals(action) ? nextRole : null);

        assertThat(next).isEqualTo(target.withRoleAndStatus(nextRole, nextStatus));
        assertThat(repository.projectLocked).isTrue();
        assertThat(repository.updated).isTrue();
        assertThat(repository.nextRole).isEqualTo(nextRole);
        assertThat(repository.nextStatus).isEqualTo(nextStatus);
        assertThat(repository.events).containsExactly(new MembershipAdministrationAccepted(
                actorProfileId, action, PROJECT_ID, MEMBER_MEMBERSHIP_ID,
                "MEMBER", nextRole, initialStatus, nextStatus));
    }

    @Test
    void activeMemberCanLeaveTheirOwnMembership() {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(
                new MembershipActor(PROJECT_ID, MEMBER_ID, "MEMBER", "ACTIVE"),
                target,
                List.of(ownerMembership(), target));

        ProjectMember next = service(repository).execute(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                MEMBER_ID,
                "withdraw",
                null);

        assertThat(next.membershipStatus()).isEqualTo("LEFT");
        assertThat(next.profileId()).isEqualTo(MEMBER_ID);
        assertThat(repository.updated).isTrue();
        assertThat(repository.events).containsExactly(new MembershipAdministrationAccepted(
                MEMBER_ID, "withdraw", PROJECT_ID, MEMBER_MEMBERSHIP_ID,
                "MEMBER", "MEMBER", "ACTIVE", "LEFT"));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedActorsAndActions")
    void unauthorizedActorsCannotAdministerAnotherMembership(MembershipActor actor, String action) {
        ProjectMember target = targetFor(action);
        CapturingRepository repository = repository(
                actor, target, List.of(ownerMembership(), target));

        assertThatThrownBy(() -> execute(repository, action))
                .isInstanceOf(MembershipPermissionException.class);
        assertNotUpdatedOrPublished(repository);
    }

    private static Stream<Arguments> unauthorizedActorsAndActions() {
        List<MembershipActor> actors = new ArrayList<>();
        actors.add(null);
        actors.add(new MembershipActor(PROJECT_ID, OWNER_ID, "MEMBER", "ACTIVE"));
        for (String role : List.of("OWNER", "ADMIN", "MEMBER")) {
            for (String status : List.of("INVITED", "LEFT", "REMOVED")) {
                actors.add(new MembershipActor(PROJECT_ID, OWNER_ID, role, status));
            }
        }
        return actors.stream().flatMap(actor -> Stream.of("activate", "changeRole", "withdraw")
                .map(action -> Arguments.of(actor, action)));
    }

    @ParameterizedTest
    @CsvSource({"MEMBER, ACTIVE", "OWNER, INVITED", "OWNER, LEFT", "OWNER, REMOVED"})
    void rejectsAuthorityRevokedWhileWaitingForTheProjectLock(String role, String status) {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));
        repository.afterLock = () -> repository.actor = new MembershipActor(PROJECT_ID, OWNER_ID, role, status);

        assertThatThrownBy(() -> execute(repository, "changeRole"))
                .isInstanceOf(MembershipPermissionException.class);
        assertThat(repository.projectLocked).isTrue();
        assertNotUpdatedOrPublished(repository);
    }

    @Test
    void rejectsActorDeletedWhileWaitingForTheProjectLock() {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));
        repository.afterLock = () -> repository.actor = null;

        assertThatThrownBy(() -> execute(repository, "changeRole"))
                .isInstanceOf(MembershipPermissionException.class);
        assertThat(repository.projectLocked).isTrue();
        assertNotUpdatedOrPublished(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"activate", "changeRole", "withdraw"})
    void missingTargetIsDeniedWithoutUpdatingOrPublishing(String action) {
        CapturingRepository repository = repository(owner(), null, List.of(ownerMembership()));

        assertThatThrownBy(() -> execute(repository, action))
                .isInstanceOf(MembershipPermissionException.class);
        assertNotUpdatedOrPublished(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"activate", "changeRole", "withdraw"})
    void targetFromAnotherProjectCannotBeAdministered(String action) {
        ProjectMember initial = targetFor(action);
        ProjectMember target = new ProjectMember(
                initial.id(), OTHER_PROJECT_ID, initial.profileId(),
                initial.role(), initial.membershipStatus(), initial.createdByProfileId());
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));

        assertThatThrownBy(() -> execute(repository, action))
                .isInstanceOf(MembershipPermissionException.class);
        assertNotUpdatedOrPublished(repository);
    }

    @Test
    void adminCannotElevateTheirOwnRole() {
        ProjectMember target = member("ADMIN", "ACTIVE");
        CapturingRepository repository = repository(
                new MembershipActor(PROJECT_ID, MEMBER_ID, "ADMIN", "ACTIVE"),
                target, List.of(ownerMembership(), target));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID, MEMBER_MEMBERSHIP_ID, MEMBER_ID, "changeRole", "OWNER"))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("cannot elevate own role.");
        assertNotUpdatedOrPublished(repository);
    }

    @ParameterizedTest
    @CsvSource({"OWNER, changeRole", "OWNER, withdraw", "ADMIN, changeRole", "ADMIN, withdraw"})
    void cannotDemoteOrWithdrawTheLastActiveOwner(String actorRole, String action) {
        ProjectMember target = ownerMembership();
        UUID actorProfileId = "OWNER".equals(actorRole) ? OWNER_ID : ADMIN_ID;
        CapturingRepository repository = repository(
                new MembershipActor(PROJECT_ID, actorProfileId, actorRole, "ACTIVE"),
                target, List.of(target));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID, OWNER_MEMBERSHIP_ID, actorProfileId, action,
                "changeRole".equals(action) ? "ADMIN" : null))
                .isInstanceOf(MembershipOwnerInvariantException.class)
                .hasMessage("project must keep at least one active OWNER.");
        assertNotUpdatedOrPublished(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVITED", "LEFT", "REMOVED"})
    void inactiveOwnerCannotReplaceTheLastActiveOwner(String inactiveStatus) {
        ProjectMember target = ownerMembership();
        CapturingRepository repository = repository(
                owner(), target, List.of(target, member("OWNER", inactiveStatus)));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID, OWNER_MEMBERSHIP_ID, OWNER_ID, "changeRole", "ADMIN"))
                .isInstanceOf(MembershipOwnerInvariantException.class);
        assertNotUpdatedOrPublished(repository);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void doesNotPublishAnEventUnlessExactlyOneMembershipWasUpdated(int updateCount) {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));
        repository.updateCount = updateCount;

        assertThatThrownBy(() -> execute(repository, "changeRole"))
                .isInstanceOf(MembershipConflictException.class);
        assertThat(repository.updated).isTrue();
        assertThat(repository.events).isEmpty();
    }

    @Test
    void persistenceFailureDoesNotPublishAnAcceptedChange() {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));
        RuntimeException failure = new IllegalStateException("membership update failed");
        repository.updateFailure = failure;

        assertThatThrownBy(() -> execute(repository, "changeRole"))
                .isSameAs(failure);
        assertThat(repository.events).isEmpty();
    }

    @Test
    void rejectsUnknownActionsBeforePersistence() {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(owner(), target, List.of(ownerMembership(), target));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID, MEMBER_MEMBERSHIP_ID, OWNER_ID, "delete", null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("action contains unknown value 'delete'.");
        assertNotUpdatedOrPublished(repository);
    }

    private static ProjectMember execute(CapturingRepository repository, String action) {
        return service(repository).execute(
                PROJECT_ID, MEMBER_MEMBERSHIP_ID, OWNER_ID, action,
                "changeRole".equals(action) ? "ADMIN" : null);
    }

    private static AdministerProjectMembership service(CapturingRepository repository) {
        return new AdministerProjectMembership(repository, repository.events::add);
    }

    private static void assertNotUpdatedOrPublished(CapturingRepository repository) {
        assertThat(repository.updated).isFalse();
        assertThat(repository.events).isEmpty();
    }

    private static CapturingRepository repository(
            MembershipActor actor,
            ProjectMember target,
            List<ProjectMember> members) {
        CapturingRepository repository = new CapturingRepository();
        repository.actor = actor;
        repository.target = target;
        repository.members = new ArrayList<>(members);
        return repository;
    }

    private static MembershipActor owner() {
        return new MembershipActor(PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE");
    }

    private static ProjectMember ownerMembership() {
        return new ProjectMember(
                OWNER_MEMBERSHIP_ID, PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE", OWNER_ID);
    }

    private static ProjectMember targetFor(String action) {
        return member("MEMBER", "activate".equals(action) ? "LEFT" : "ACTIVE");
    }

    private static ProjectMember member(String role, String membershipStatus) {
        return new ProjectMember(
                MEMBER_MEMBERSHIP_ID, PROJECT_ID, MEMBER_ID, role, membershipStatus, OWNER_ID);
    }

    private static final class CapturingRepository implements MembershipAdministrationRepository {

        private MembershipActor actor;
        private ProjectMember target;
        private List<ProjectMember> members = List.of();
        private final List<Object> events = new ArrayList<>();
        private Runnable afterLock = () -> { };
        private boolean projectLocked;
        private boolean updated;
        private String nextRole;
        private String nextStatus;
        private int updateCount = 1;
        private RuntimeException updateFailure;

        @Override
        public void lockProject(UUID projectId) {
            projectLocked = true;
            afterLock.run();
        }

        @Override
        public Optional<MembershipActor> findActor(UUID projectId, UUID profileId) {
            return Optional.ofNullable(actor);
        }

        @Override
        public Optional<ProjectMember> findMember(UUID projectId, UUID memberId) {
            return Optional.ofNullable(target);
        }

        @Override
        public List<ProjectMember> findMembers(UUID projectId) {
            return members;
        }

        @Override
        public List<ManagedProjectMember> findManagedMembers(UUID projectId, int offset, int limit) {
            return List.of();
        }

        @Override
        public int updateMembership(
                UUID projectId,
                UUID memberId,
                String expectedRole,
                String expectedMembershipStatus,
                String nextRole,
                String nextMembershipStatus) {
            updated = true;
            if (updateFailure != null) {
                throw updateFailure;
            }
            this.nextRole = nextRole;
            this.nextStatus = nextMembershipStatus;
            return updateCount;
        }
    }
}
