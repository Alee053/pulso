package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.MembershipOwnerInvariantException;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

class AdministerProjectMembershipTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OWNER_MEMBERSHIP_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_MEMBERSHIP_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Test
    void activeOwnerCanChangeAnotherMembershipRole() {
        ProjectMember target = member("MEMBER", "ACTIVE");
        CapturingRepository repository = repository(
                owner(),
                target,
                List.of(ownerMembership(), target));

        ProjectMember next = service(repository).execute(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                OWNER_ID,
                "changeRole",
                "ADMIN");

        assertThat(next).isEqualTo(target.withRoleAndStatus("ADMIN", "ACTIVE"));
        assertThat(repository.updated).isTrue();
        assertThat(repository.nextRole).isEqualTo("ADMIN");
        assertThat(repository.nextStatus).isEqualTo("ACTIVE");
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
    }

    @Test
    void inactiveOrMissingActorCannotChangeMemberships() {
        CapturingRepository repository = repository(
                null,
                member("MEMBER", "ACTIVE"),
                List.of(ownerMembership(), member("MEMBER", "ACTIVE")));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                OWNER_ID,
                "changeRole",
                "ADMIN"))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThat(repository.updated).isFalse();
    }

    @Test
    void cannotDemoteTheLastActiveOwner() {
        ProjectMember target = ownerMembership();
        CapturingRepository repository = repository(
                owner(),
                target,
                List.of(target));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                OWNER_MEMBERSHIP_ID,
                OWNER_ID,
                "changeRole",
                "ADMIN"))
                .isInstanceOf(MembershipOwnerInvariantException.class)
                .hasMessage("project must keep at least one active OWNER.");
        assertThat(repository.updated).isFalse();
    }

    @Test
    void rejectsUnknownActionsBeforePersistence() {
        CapturingRepository repository = repository(
                owner(),
                member("MEMBER", "ACTIVE"),
                List.of(ownerMembership(), member("MEMBER", "ACTIVE")));

        assertThatThrownBy(() -> service(repository).execute(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                OWNER_ID,
                "delete",
                null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("action contains unknown value 'delete'.");
        assertThat(repository.updated).isFalse();
    }

    private static AdministerProjectMembership service(CapturingRepository repository) {
        return new AdministerProjectMembership(repository);
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
                OWNER_MEMBERSHIP_ID,
                PROJECT_ID,
                OWNER_ID,
                "OWNER",
                "ACTIVE",
                OWNER_ID);
    }

    private static ProjectMember member(String role, String membershipStatus) {
        return new ProjectMember(
                MEMBER_MEMBERSHIP_ID,
                PROJECT_ID,
                MEMBER_ID,
                role,
                membershipStatus,
                OWNER_ID);
    }

    private static final class CapturingRepository implements MembershipAdministrationRepository {

        private MembershipActor actor;
        private ProjectMember target;
        private List<ProjectMember> members = List.of();
        private boolean updated;
        private String nextRole;
        private String nextStatus;

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
        public int updateMembership(
                UUID projectId,
                UUID memberId,
                String expectedRole,
                String expectedMembershipStatus,
                String nextRole,
                String nextMembershipStatus) {
            updated = true;
            this.nextRole = nextRole;
            this.nextStatus = nextMembershipStatus;
            return 1;
        }
    }
}
