package com.pulso.backend.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MembershipAdministrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID MEMBER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TARGET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OWNER_MEMBERSHIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_MEMBERSHIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MEMBER_MEMBERSHIP_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TARGET_MEMBERSHIP_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @ParameterizedTest
    @CsvSource({
            "MEMBER, ACTIVE",
            "OWNER, INVITED",
            "ADMIN, INVITED",
            "OWNER, LEFT",
            "ADMIN, REMOVED"
    })
    void onlyActiveOwnerOrAdminCanAdministerExistingMemberships(String role, String membershipStatus) {
        MembershipActor actor = new MembershipActor(PROJECT_ID, OWNER_ID, role, membershipStatus);
        ProjectMember target = leftMember();
        List<ProjectMember> members = List.of(activeOwner(), target);

        assertThatThrownBy(() -> MembershipAdministration.activate(actor, target, members))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> MembershipAdministration.changeRole(actor, target, "ADMIN", members))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> MembershipAdministration.withdraw(actor, target, members))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void missingActorCannotAdminister() {
        ProjectMember target = leftMember();
        List<ProjectMember> members = List.of(activeOwner(), target);

        assertThatThrownBy(() -> MembershipAdministration.activate(null, target, members))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void actorFromAnotherProjectCannotAdminister() {
        MembershipActor actor = new MembershipActor(OTHER_PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE");
        ProjectMember target = leftMember();

        assertThatThrownBy(() -> MembershipAdministration.activate(
                actor,
                target,
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void activeOwnerCanActivateALeftMembershipWithoutClearingTheProfile() {
        ProjectMember target = leftMember();

        ProjectMember next = MembershipAdministration.activate(
                owner(),
                target,
                List.of(activeOwner(), target));

        assertThat(next.id()).isEqualTo(target.id());
        assertThat(next.projectId()).isEqualTo(PROJECT_ID);
        assertThat(next.profileId()).isEqualTo(TARGET_ID);
        assertThat(next.role()).isEqualTo("MEMBER");
        assertThat(next.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(next.createdByProfileId()).isEqualTo(OWNER_ID);
    }

    @Test
    void activeAdminCanActivateARemovedMembership() {
        ProjectMember target = removedMember();

        ProjectMember next = MembershipAdministration.activate(
                admin(),
                target,
                List.of(activeOwner(), activeAdmin(), target));

        assertThat(next.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(next.profileId()).isEqualTo(TARGET_ID);
        assertThat(next.role()).isEqualTo("MEMBER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVITED", "ACTIVE"})
    void cannotActivateInvitedOrAlreadyActiveMemberships(String membershipStatus) {
        ProjectMember target = membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "MEMBER", membershipStatus);

        assertThatThrownBy(() -> MembershipAdministration.activate(
                owner(),
                target,
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipInvalidTransitionException.class)
                .hasMessage("cannot activate a membership with status " + membershipStatus + ".");
    }

    @Test
    void cannotActivateAMembershipWithoutAProfile() {
        ProjectMember target = membership(TARGET_MEMBERSHIP_ID, null, "MEMBER", "REMOVED");

        assertThatThrownBy(() -> MembershipAdministration.activate(
                owner(),
                target,
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipInvalidTransitionException.class)
                .hasMessage("cannot activate a membership without a profile.");
    }

    @Test
    void activeOwnerCanChangeAnotherMembersRole() {
        ProjectMember target = activeMember();

        ProjectMember next = MembershipAdministration.changeRole(
                owner(),
                target,
                "ADMIN",
                List.of(activeOwner(), target));

        assertThat(next.id()).isEqualTo(target.id());
        assertThat(next.profileId()).isEqualTo(MEMBER_ID);
        assertThat(next.role()).isEqualTo("ADMIN");
        assertThat(next.membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void cannotElevateOwnRole() {
        ProjectMember target = activeAdmin();

        assertThatThrownBy(() -> MembershipAdministration.changeRole(
                admin(),
                target,
                "OWNER",
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("cannot elevate own role.");
    }

    @Test
    void ownerCanDemoteOwnRoleWhenAnotherActiveOwnerRemains() {
        ProjectMember secondOwner = membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "OWNER", "ACTIVE");

        ProjectMember next = MembershipAdministration.changeRole(
                owner(),
                activeOwner(),
                "ADMIN",
                List.of(activeOwner(), secondOwner));

        assertThat(next.profileId()).isEqualTo(OWNER_ID);
        assertThat(next.role()).isEqualTo("ADMIN");
        assertThat(next.membershipStatus()).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVITED", "LEFT", "REMOVED"})
    void cannotChangeRoleUnlessTheMembershipIsActive(String membershipStatus) {
        ProjectMember target = membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "MEMBER", membershipStatus);

        assertThatThrownBy(() -> MembershipAdministration.changeRole(
                owner(),
                target,
                "ADMIN",
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipInvalidTransitionException.class)
                .hasMessage("cannot change role of a membership with status " + membershipStatus + ".");
    }

    @Test
    void withdrawingAnotherMembershipKeepsTheRowAndMarksItRemoved() {
        ProjectMember target = activeMember();

        ProjectMember next = MembershipAdministration.withdraw(
                owner(),
                target,
                List.of(activeOwner(), target));

        assertThat(next.id()).isEqualTo(target.id());
        assertThat(next.projectId()).isEqualTo(PROJECT_ID);
        assertThat(next.profileId()).isEqualTo(MEMBER_ID);
        assertThat(next.role()).isEqualTo("MEMBER");
        assertThat(next.membershipStatus()).isEqualTo("REMOVED");
        assertThat(next.createdByProfileId()).isEqualTo(OWNER_ID);
    }

    @Test
    void withdrawingAnInvitationKeepsTheRowWithoutInventingAProfile() {
        ProjectMember target = membership(TARGET_MEMBERSHIP_ID, null, "MEMBER", "INVITED");

        ProjectMember next = MembershipAdministration.withdraw(
                admin(),
                target,
                List.of(activeOwner(), activeAdmin(), target));

        assertThat(next.id()).isEqualTo(target.id());
        assertThat(next.profileId()).isNull();
        assertThat(next.membershipStatus()).isEqualTo("REMOVED");
    }

    @Test
    void anActiveMemberCanLeaveWithoutDeletingTheProfile() {
        ProjectMember target = activeMember();

        ProjectMember next = MembershipAdministration.withdraw(
                member(),
                target,
                List.of(activeOwner(), target));

        assertThat(next.profileId()).isEqualTo(MEMBER_ID);
        assertThat(next.membershipStatus()).isEqualTo("LEFT");
        assertThat(next.role()).isEqualTo("MEMBER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "REMOVED"})
    void cannotWithdrawAnAlreadyClosedMembership(String membershipStatus) {
        ProjectMember target = membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "MEMBER", membershipStatus);

        assertThatThrownBy(() -> MembershipAdministration.withdraw(
                owner(),
                target,
                List.of(activeOwner(), target)))
                .isInstanceOf(MembershipInvalidTransitionException.class)
                .hasMessage("cannot withdraw a membership with status " + membershipStatus + ".");
    }

    @Test
    void cannotLeaveTheProjectWithoutAnActiveOwner() {
        ProjectMember onlyOwner = activeOwner();

        assertThatThrownBy(() -> MembershipAdministration.withdraw(
                owner(),
                onlyOwner,
                List.of(onlyOwner, activeMember())))
                .isInstanceOf(MembershipOwnerInvariantException.class)
                .hasMessage("project must keep at least one active OWNER.");
        assertThatThrownBy(() -> MembershipAdministration.changeRole(
                owner(),
                onlyOwner,
                "ADMIN",
                List.of(onlyOwner)))
                .isInstanceOf(MembershipOwnerInvariantException.class)
                .hasMessage("project must keep at least one active OWNER.");
        assertThatThrownBy(() -> MembershipAdministration.withdraw(
                admin(),
                onlyOwner,
                List.of(onlyOwner, activeAdmin())))
                .isInstanceOf(MembershipOwnerInvariantException.class)
                .hasMessage("project must keep at least one active OWNER.");
    }

    @Test
    void aMemberCannotWithdrawSomeoneElse() {
        ProjectMember target = activeAdmin();

        assertThatThrownBy(() -> MembershipAdministration.withdraw(
                member(),
                target,
                List.of(activeOwner(), target, activeMember())))
                .isInstanceOf(MembershipPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void activeAdminCanPromoteAnotherMemberToOwner() {
        ProjectMember target = activeMember();

        ProjectMember next = MembershipAdministration.changeRole(
                admin(),
                target,
                "OWNER",
                List.of(activeOwner(), activeAdmin(), target));

        assertThat(next.role()).isEqualTo("OWNER");
        assertThat(next.profileId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void rejectsUnknownRoles() {
        assertThatThrownBy(() -> new MembershipActor(PROJECT_ID, OWNER_ID, "EDITOR", "ACTIVE"))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("role contains unknown value 'EDITOR'.");
        assertThatThrownBy(() -> MembershipAdministration.changeRole(
                owner(),
                activeMember(),
                "EDITOR",
                List.of(activeOwner(), activeMember())))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("role contains unknown value 'EDITOR'.");
    }

    private static MembershipActor owner() {
        return new MembershipActor(PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE");
    }

    private static MembershipActor admin() {
        return new MembershipActor(PROJECT_ID, ADMIN_ID, "ADMIN", "ACTIVE");
    }

    private static MembershipActor member() {
        return new MembershipActor(PROJECT_ID, MEMBER_ID, "MEMBER", "ACTIVE");
    }

    private static ProjectMember activeOwner() {
        return membership(OWNER_MEMBERSHIP_ID, OWNER_ID, "OWNER", "ACTIVE");
    }

    private static ProjectMember activeAdmin() {
        return membership(ADMIN_MEMBERSHIP_ID, ADMIN_ID, "ADMIN", "ACTIVE");
    }

    private static ProjectMember activeMember() {
        return membership(MEMBER_MEMBERSHIP_ID, MEMBER_ID, "MEMBER", "ACTIVE");
    }

    private static ProjectMember leftMember() {
        return membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "MEMBER", "LEFT");
    }

    private static ProjectMember removedMember() {
        return membership(TARGET_MEMBERSHIP_ID, TARGET_ID, "MEMBER", "REMOVED");
    }

    private static ProjectMember membership(
            UUID id,
            UUID profileId,
            String role,
            String membershipStatus) {
        return new ProjectMember(id, PROJECT_ID, profileId, role, membershipStatus, OWNER_ID);
    }
}
