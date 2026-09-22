package com.pulso.backend.project.domain;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record MemberInvitation(
        UUID projectId,
        String inviteeEmail,
        UUID profileId,
        String role,
        String membershipStatus,
        UUID createdByProfileId) {

    private static final Set<String> INVITE_ROLES = Set.of("OWNER", "ADMIN");
    private static final Set<String> KNOWN_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
    private static final Set<String> KNOWN_MEMBERSHIP_STATUSES = Set.of(
            "INVITED",
            "ACTIVE",
            "LEFT",
            "REMOVED");
    private static final Pattern INVITEE_EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final int INVITEE_EMAIL_MAX_LENGTH = 254;

    public MemberInvitation {
        if (projectId == null) {
            throw new ProjectValidationException("projectId is required.");
        }
        if (createdByProfileId == null) {
            throw new ProjectValidationException("createdByProfileId is required.");
        }
        role = MemberInvitationValues.requireKnown(role, "role", KNOWN_ROLES);
        membershipStatus = MemberInvitationValues.requireKnown(
                membershipStatus,
                "membershipStatus",
                KNOWN_MEMBERSHIP_STATUSES);
        inviteeEmail = normalizeOptionalEmail(inviteeEmail);
        if (inviteeEmail == null && profileId == null) {
            throw new ProjectValidationException("profileId or inviteeEmail is required.");
        }
        if ("INVITED".equals(membershipStatus)) {
            if (profileId != null) {
                throw new ProjectValidationException("profileId must be empty until the invitation is accepted.");
            }
            if (inviteeEmail == null) {
                throw new ProjectValidationException("inviteeEmail is required.");
            }
            if (!"MEMBER".equals(role)) {
                throw new ProjectValidationException("role must be MEMBER.");
            }
        }
        if ("ACTIVE".equals(membershipStatus) && profileId == null) {
            throw new ProjectValidationException("profileId is required.");
        }
    }

    public static CreatedMemberInvitation invite(
            UUID projectId,
            boolean projectExists,
            MemberInvitationActor actor,
            String inviteeEmail,
            String acceptToken,
            Collection<MemberInvitation> existingMembers) {
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
                actor.profileId());
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
        String normalizedCandidate = normalizeOptionalEmail(candidateEmail);
        return existing.inviteeEmail() != null
                && existing.inviteeEmail().equals(normalizedCandidate);
    }

    public MemberInvitation accept(UUID acceptingProfileId) {
        if (!"INVITED".equals(membershipStatus)) {
            throw new ProjectValidationException(
                    "cannot accept a membership with status " + membershipStatus + ".");
        }
        if (acceptingProfileId == null) {
            throw new ProjectValidationException("profileId is required.");
        }
        return new MemberInvitation(
                projectId,
                inviteeEmail,
                acceptingProfileId,
                role,
                "ACTIVE",
                createdByProfileId);
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

    private static String normalizeOptionalEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ProjectValidationException("inviteeEmail is required.");
        }
        if (normalized.length() > INVITEE_EMAIL_MAX_LENGTH) {
            throw new ProjectValidationException(
                    "inviteeEmail must not exceed " + INVITEE_EMAIL_MAX_LENGTH + " characters.");
        }
        if (!INVITEE_EMAIL.matcher(normalized).matches()) {
            throw new ProjectValidationException("inviteeEmail is invalid.");
        }
        return normalized;
    }
}
