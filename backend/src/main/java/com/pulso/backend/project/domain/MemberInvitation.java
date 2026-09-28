package com.pulso.backend.project.domain;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.pulso.backend.config.ApiMessages;

public record MemberInvitation(
        UUID projectId,
        String inviteeEmail,
        UUID profileId,
        String role,
        String membershipStatus,
        UUID createdByProfileId,
        Limits limits) {

    private static final Set<String> INVITE_ROLES = Set.of("OWNER", "ADMIN");
    private static final Set<String> KNOWN_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
    private static final Set<String> KNOWN_MEMBERSHIP_STATUSES = Set.of(
            "INVITED",
            "ACTIVE",
            "LEFT",
            "REMOVED");
    private static final Pattern INVITEE_EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public record Limits(int inviteeEmailMaxLength) {
    }

    public MemberInvitation {
        requireProjectId(projectId);
        requireCreatedByProfileId(createdByProfileId);
        inviteeEmail = normalizeOptionalEmail(inviteeEmail, limits.inviteeEmailMaxLength());
        role = MemberInvitationValues.requireKnown(role, "role", KNOWN_ROLES);
        membershipStatus = MemberInvitationValues.requireKnown(
                membershipStatus,
                "membershipStatus",
                KNOWN_MEMBERSHIP_STATUSES);
        if (inviteeEmail == null && profileId == null) {
            throw new ProjectValidationException(ApiMessages.PROFILE_OR_EMAIL_REQUIRED);
        }
        if ("INVITED".equals(membershipStatus)) {
            if (profileId != null) {
                throw new ProjectValidationException(ApiMessages.PROFILE_MUST_BE_EMPTY);
            }
            if (inviteeEmail == null) {
                throw new ProjectValidationException(ApiMessages.INVITEE_EMAIL_REQUIRED);
            }
            if (!"MEMBER".equals(role)) {
                throw new ProjectValidationException(ApiMessages.ROLE_MUST_BE_MEMBER);
            }
        }
        if ("ACTIVE".equals(membershipStatus) && profileId == null) {
            throw new ProjectValidationException(ApiMessages.PROFILE_ID_REQUIRED);
        }
    }

    public static CreatedMemberInvitation invite(
            UUID projectId,
            boolean projectExists,
            MemberInvitationActor actor,
            String inviteeEmail,
            String acceptToken,
            Collection<MemberInvitation> existingMembers,
            Limits limits) {
        if (!projectExists) {
            throw new MemberInvitationProjectNotFoundException();
        }
        requireCanInvite(projectId, actor);
        String digest = InviteTokenDigest.fromPlaintext(acceptToken);
        MemberInvitation invitation = new MemberInvitation(
                projectId,
                inviteeEmail,
                null,
                "MEMBER",
                "INVITED",
                actor.profileId(),
                limits);
        if (hasActiveDuplicate(existingMembers, invitation)) {
            throw new DuplicateMemberInvitationException();
        }
        return new CreatedMemberInvitation(invitation, digest);
    }

    public static boolean isActiveDuplicate(
            MemberInvitation existing,
            UUID projectId,
            String candidateEmail) {
        if (existing == null
                || projectId == null
                || !projectId.equals(existing.projectId())
                || !"INVITED".equals(existing.membershipStatus())) {
            return false;
        }
        String normalizedCandidate = normalizeOptionalEmail(
                candidateEmail,
                existing.limits().inviteeEmailMaxLength());
        return existing.inviteeEmail() != null
                && existing.inviteeEmail().equals(normalizedCandidate);
    }

    public MemberInvitation accept(UUID acceptingProfileId) {
        if (!"INVITED".equals(membershipStatus)) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.CANNOT_ACCEPT_STATUS, membershipStatus));
        }
        if (acceptingProfileId == null) {
            throw new ProjectValidationException(ApiMessages.PROFILE_ID_REQUIRED);
        }
        return new MemberInvitation(
                projectId,
                inviteeEmail,
                acceptingProfileId,
                role,
                "ACTIVE",
                createdByProfileId,
                limits);
    }

    public boolean matchesInviteeEmail(String authenticatedEmail) {
        try {
            return inviteeEmail != null
                    && inviteeEmail.equals(normalizeOptionalEmail(
                            authenticatedEmail,
                            limits.inviteeEmailMaxLength()));
        } catch (ProjectValidationException exception) {
            return false;
        }
    }

    private static void requireProjectId(UUID projectId) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
    }

    private static void requireCreatedByProfileId(UUID createdByProfileId) {
        if (createdByProfileId == null) {
            throw new ProjectValidationException(ApiMessages.CREATED_BY_REQUIRED);
        }
    }

    private static void requireCanInvite(UUID projectId, MemberInvitationActor actor) {
        if (actor == null
                || !projectId.equals(actor.projectId())
                || !"ACTIVE".equals(actor.membershipStatus())
                || !INVITE_ROLES.contains(actor.role())) {
            throw new MemberInvitationPermissionException();
        }
    }

    private static boolean hasActiveDuplicate(
            Collection<MemberInvitation> existingMembers,
            MemberInvitation invitation) {
        Collection<MemberInvitation> members = existingMembers == null ? List.of() : existingMembers;
        return members.stream()
                .anyMatch(existing -> isActiveDuplicate(
                        existing,
                        invitation.projectId(),
                        invitation.inviteeEmail()));
    }

    private static String normalizeOptionalEmail(String value, int inviteeEmailMaxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ProjectValidationException(ApiMessages.INVITEE_EMAIL_REQUIRED);
        }
        if (normalized.length() > inviteeEmailMaxLength) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.INVITEE_EMAIL_MAX_LENGTH, inviteeEmailMaxLength));
        }
        if (!INVITEE_EMAIL.matcher(normalized).matches()) {
            throw new ProjectValidationException(ApiMessages.INVITEE_EMAIL_INVALID);
        }
        return normalized;
    }
}
