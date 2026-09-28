package com.pulso.backend.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberInvitationTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCEPTING_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String PLAINTEXT_TOKEN = "accept-me";
    private static final String TOKEN_DIGEST =
            "69a24810112e8f3e257dfd85e240bcc8e8a60155787eaa0ec9526f584f0deb7d";
    private static final int INVITEE_EMAIL_MAX_LENGTH = 254;
    private static final MemberInvitation.Limits LIMITS =
            new MemberInvitation.Limits(INVITEE_EMAIL_MAX_LENGTH);

    @Test
    void inviteNormalizesEmailAndStartsInvitedAsMemberWithoutAProfile() {
        CreatedMemberInvitation created = invite(" Ada@Example.COM ");

        MemberInvitation invitation = created.invitation();
        assertThat(invitation.projectId()).isEqualTo(PROJECT_ID);
        assertThat(invitation.inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(invitation.profileId()).isNull();
        assertThat(invitation.role()).isEqualTo("MEMBER");
        assertThat(invitation.membershipStatus()).isEqualTo("INVITED");
        assertThat(invitation.createdByProfileId()).isEqualTo(OWNER_ID);
        assertThat(created.inviteTokenDigest()).isEqualTo(TOKEN_DIGEST);
        assertThat(created.invitation().toString()).doesNotContain(PLAINTEXT_TOKEN);
    }

    @Test
    void invitePersistsOnlyADigestDerivedFromTheAcceptToken() {
        CreatedMemberInvitation created = invite("ada@example.com");

        assertThat(InviteTokenDigest.fromPlaintext(PLAINTEXT_TOKEN)).isEqualTo(TOKEN_DIGEST);
        assertThat(created.inviteTokenDigest()).isEqualTo(TOKEN_DIGEST);
        assertThat(created.inviteTokenDigest()).isNotEqualTo(PLAINTEXT_TOKEN);
        assertThat(created.invitation().toString()).doesNotContain(PLAINTEXT_TOKEN);
        assertThat(created.invitation().toString()).doesNotContain("inviteTokenDigest");
    }

    @Test
    void rejectsAMissingProject() {
        assertThatThrownBy(() -> MemberInvitation.invite(
                PROJECT_ID,
                false,
                owner(),
                "ada@example.com",
                PLAINTEXT_TOKEN,
                List.of(),
                LIMITS))
                .isInstanceOf(MemberInvitationProjectNotFoundException.class)
                .hasMessage("project does not exist.");
    }

    @Test
    void rejectsMissingOrInsufficientPermission() {
        assertThatThrownBy(() -> MemberInvitation.invite(
                PROJECT_ID,
                true,
                null,
                "ada@example.com",
                PLAINTEXT_TOKEN,
                List.of(),
                LIMITS))
                .isInstanceOf(MemberInvitationPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> inviteWith(member("ACTIVE"), List.of()))
                .isInstanceOf(MemberInvitationPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> inviteWith(new MemberInvitationActor(
                PROJECT_ID,
                OWNER_ID,
                "OWNER",
                "INVITED"), List.of()))
                .isInstanceOf(MemberInvitationPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
        assertThatThrownBy(() -> inviteWith(new MemberInvitationActor(
                OTHER_PROJECT_ID,
                OWNER_ID,
                "OWNER",
                "ACTIVE"), List.of()))
                .isInstanceOf(MemberInvitationPermissionException.class)
                .hasMessage("active OWNER or ADMIN membership is required.");
    }

    @Test
    void activeAdminCanInvite() {
        CreatedMemberInvitation created = MemberInvitation.invite(
                PROJECT_ID,
                true,
                new MemberInvitationActor(PROJECT_ID, OWNER_ID, "ADMIN", "ACTIVE"),
                "ada@example.com",
                PLAINTEXT_TOKEN,
                List.of(),
                LIMITS);

        assertThat(created.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(created.invitation().createdByProfileId()).isEqualTo(OWNER_ID);
    }

    @Test
    void duplicateIsAnActiveInvitationForTheSameProjectAndNormalizedEmail() {
        assertThat(MemberInvitation.isActiveDuplicate(
                invited("ada@example.com"),
                PROJECT_ID,
                " ADA@example.com "))
                .isTrue();
        assertThat(MemberInvitation.isActiveDuplicate(
                invited("ada@example.com"),
                PROJECT_ID,
                "other@example.com"))
                .isFalse();
        assertThat(MemberInvitation.isActiveDuplicate(
                invited("ada@example.com"),
                OTHER_PROJECT_ID,
                "ada@example.com"))
                .isFalse();
        assertThat(MemberInvitation.isActiveDuplicate(
                left("ada@example.com"),
                PROJECT_ID,
                "ada@example.com"))
                .isFalse();
        assertThat(MemberInvitation.isActiveDuplicate(
                activeMember(),
                PROJECT_ID,
                "ada@example.com"))
                .isFalse();
    }

    @Test
    void rejectsAnActiveDuplicateInvitation() {
        assertThatThrownBy(() -> inviteWith(
                owner(),
                List.of(invited("ADA@example.com"))))
                .isInstanceOf(DuplicateMemberInvitationException.class)
                .hasMessage("an active invitation already exists for this project and email.");
    }

    @Test
    void allowsANewInvitationAfterThePreviousOneLeftOrWasRemoved() {
        CreatedMemberInvitation created = inviteWith(
                owner(),
                List.of(left("ada@example.com"), removed("ada@example.com")));

        assertThat(created.invitation().inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(created.invitation().membershipStatus()).isEqualTo("INVITED");
    }

    @Test
    void acceptBindsTheProfileAndActivatesMembershipWithoutExposingAToken() {
        MemberInvitation accepted = invite("ada@example.com")
                .invitation()
                .accept(ACCEPTING_PROFILE_ID);

        assertThat(accepted.projectId()).isEqualTo(PROJECT_ID);
        assertThat(accepted.inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(accepted.profileId()).isEqualTo(ACCEPTING_PROFILE_ID);
        assertThat(accepted.role()).isEqualTo("MEMBER");
        assertThat(accepted.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(accepted.createdByProfileId()).isEqualTo(OWNER_ID);
        assertThat(accepted.toString()).doesNotContain(PLAINTEXT_TOKEN);
        assertThat(accepted.toString()).doesNotContain(TOKEN_DIGEST);
    }

    @Test
    void matchesTheAuthenticatedEmailUsingTheInvitationNormalizationRules() {
        MemberInvitation invitation = invite("ada@example.com").invitation();

        assertThat(invitation.matchesInviteeEmail(" Ada@Example.COM ")).isTrue();
        assertThat(invitation.matchesInviteeEmail("other@example.com")).isFalse();
        assertThat(invitation.matchesInviteeEmail("not-an-email")).isFalse();
        assertThat(invitation.matchesInviteeEmail(null)).isFalse();
    }

    @Test
    void acceptRequiresAnInvitedMembershipAndAProfile() {
        MemberInvitation invitation = invite("ada@example.com").invitation();

        assertThatThrownBy(() -> invitation.accept(null))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("profileId is required.");
        assertThatThrownBy(() -> invitation.accept(ACCEPTING_PROFILE_ID)
                .accept(ACCEPTING_PROFILE_ID))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("cannot accept a membership with status ACTIVE.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-an-email", "ada@", "@example.com", "ada example@test.com"})
    void rejectsInvalidInviteeEmails(String inviteeEmail) {
        assertThatThrownBy(() -> invite(inviteeEmail))
                .isInstanceOf(ProjectValidationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsAMissingAcceptToken(String acceptToken) {
        assertThatThrownBy(() -> MemberInvitation.invite(
                PROJECT_ID,
                true,
                owner(),
                "ada@example.com",
                acceptToken,
                List.of(),
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("invite token is required.");
    }

    @ParameterizedTest
    @CsvSource({
            "EDITOR, ACTIVE",
            "OWNER, FROZEN"
    })
    void rejectsUnknownActorValues(String role, String membershipStatus) {
        assertThatThrownBy(() -> new MemberInvitationActor(PROJECT_ID, OWNER_ID, role, membershipStatus))
                .isInstanceOf(ProjectValidationException.class);
    }

    private static CreatedMemberInvitation invite(String inviteeEmail) {
        return inviteWith(owner(), List.of(), inviteeEmail);
    }

    private static CreatedMemberInvitation inviteWith(
            MemberInvitationActor actor,
            List<MemberInvitation> existingMembers) {
        return inviteWith(actor, existingMembers, "ada@example.com");
    }

    private static CreatedMemberInvitation inviteWith(
            MemberInvitationActor actor,
            List<MemberInvitation> existingMembers,
            String inviteeEmail) {
        return MemberInvitation.invite(
                PROJECT_ID,
                true,
                actor,
                inviteeEmail,
                PLAINTEXT_TOKEN,
                existingMembers,
                LIMITS);
    }

    private static MemberInvitationActor owner() {
        return new MemberInvitationActor(PROJECT_ID, OWNER_ID, "OWNER", "ACTIVE");
    }

    private static MemberInvitationActor member(String membershipStatus) {
        return new MemberInvitationActor(PROJECT_ID, OWNER_ID, "MEMBER", membershipStatus);
    }

    private static MemberInvitation invited(String inviteeEmail) {
        return new MemberInvitation(
                PROJECT_ID,
                inviteeEmail,
                null,
                "MEMBER",
                "INVITED",
                OWNER_ID,
                LIMITS);
    }

    private static MemberInvitation left(String inviteeEmail) {
        return new MemberInvitation(
                PROJECT_ID,
                inviteeEmail,
                null,
                "MEMBER",
                "LEFT",
                OWNER_ID,
                LIMITS);
    }

    private static MemberInvitation removed(String inviteeEmail) {
        return new MemberInvitation(
                PROJECT_ID,
                inviteeEmail,
                null,
                "MEMBER",
                "REMOVED",
                OWNER_ID,
                LIMITS);
    }

    private static MemberInvitation activeMember() {
        return new MemberInvitation(
                PROJECT_ID,
                "ada@example.com",
                ACCEPTING_PROFILE_ID,
                "MEMBER",
                "ACTIVE",
                OWNER_ID,
                LIMITS);
    }
}
