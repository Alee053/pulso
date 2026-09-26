package com.pulso.backend.project.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

public final class MembershipAdministration {

    private static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN");
    private static final Set<String> ACTIVATABLE_STATUSES = Set.of("LEFT", "REMOVED");
    private static final Set<String> WITHDRAWABLE_STATUSES = Set.of("INVITED", "ACTIVE");
    private static final Set<String> KNOWN_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");

    private MembershipAdministration() {
    }

    public static ProjectMember activate(
            MembershipActor actor,
            ProjectMember target,
            Collection<ProjectMember> members) {
        requireCanAdminister(actor, target);
        requireTarget(target);
        if (target.profileId() == null) {
            throw new MembershipInvalidTransitionException(
                    ApiMessages.CANNOT_ACTIVATE_NO_PROFILE);
        }
        if (!ACTIVATABLE_STATUSES.contains(target.membershipStatus())) {
            throw new MembershipInvalidTransitionException(
                    String.format(ApiMessages.CANNOT_ACTIVATE_STATUS, target.membershipStatus()));
        }
        return replace(
                target,
                target.role(),
                "ACTIVE",
                members);
    }

    public static ProjectMember changeRole(
            MembershipActor actor,
            ProjectMember target,
            String newRole,
            Collection<ProjectMember> members) {
        requireCanAdminister(actor, target);
        requireTarget(target);
        String normalizedRole = MembershipAdministrationValues.requireKnown(newRole, "role", KNOWN_ROLES);
        if (!"ACTIVE".equals(target.membershipStatus())) {
            throw new MembershipInvalidTransitionException(
                    String.format(ApiMessages.CANNOT_CHANGE_ROLE_STATUS, target.membershipStatus()));
        }
        if (isSelf(actor, target) && rank(normalizedRole) > rank(target.role())) {
            throw new MembershipPermissionException(ApiMessages.MEMBERSHIP_SELF_ELEVATION);
        }
        return replace(target, normalizedRole, target.membershipStatus(), members);
    }

    public static ProjectMember withdraw(
            MembershipActor actor,
            ProjectMember target,
            Collection<ProjectMember> members) {
        requireTarget(target);
        boolean self = isSelf(actor, target);
        if (self) {
            requireActiveMembership(actor, target);
        } else {
            requireCanAdminister(actor, target);
        }
        if (!WITHDRAWABLE_STATUSES.contains(target.membershipStatus())) {
            throw new MembershipInvalidTransitionException(
                    String.format(ApiMessages.CANNOT_WITHDRAW_STATUS, target.membershipStatus()));
        }
        return replace(
                target,
                target.role(),
                self ? "LEFT" : "REMOVED",
                members);
    }

    private static ProjectMember replace(
            ProjectMember target,
            String role,
            String membershipStatus,
            Collection<ProjectMember> members) {
        ProjectMember next = target.withRoleAndStatus(role, membershipStatus);
        requireActiveOwner(members, target, next);
        return next;
    }

    private static void requireCanAdminister(MembershipActor actor, ProjectMember target) {
        if (!hasAdminAuthority(actor, target)) {
            throw new MembershipPermissionException();
        }
    }

    private static void requireActiveMembership(MembershipActor actor, ProjectMember target) {
        if (actor == null
                || target != null && !actor.projectId().equals(target.projectId())
                || !"ACTIVE".equals(actor.membershipStatus())) {
            throw new MembershipPermissionException();
        }
    }

    private static boolean hasAdminAuthority(MembershipActor actor, ProjectMember target) {
        return actor != null
                && (target == null || actor.projectId().equals(target.projectId()))
                && "ACTIVE".equals(actor.membershipStatus())
                && ADMIN_ROLES.contains(actor.role());
    }

    private static boolean isSelf(MembershipActor actor, ProjectMember target) {
        return actor != null
                && target != null
                && actor.projectId().equals(target.projectId())
                && actor.profileId().equals(target.profileId());
    }

    private static void requireTarget(ProjectMember target) {
        if (target == null) {
            throw new ProjectValidationException(ApiMessages.MEMBERSHIP_REQUIRED);
        }
    }

    private static void requireActiveOwner(
            Collection<ProjectMember> members,
            ProjectMember current,
            ProjectMember next) {
        Map<UUID, ProjectMember> byId = new LinkedHashMap<>();
        Collection<ProjectMember> currentMembers =
                members == null ? List.<ProjectMember>of() : members;
        for (ProjectMember member : currentMembers) {
            if (member != null && member.id() != null) {
                byId.put(member.id(), member);
            }
        }
        byId.put(current.id(), next);
        boolean hasActiveOwner = byId.values().stream().anyMatch(MembershipAdministration::isActiveOwner);
        if (!hasActiveOwner) {
            throw new MembershipOwnerInvariantException();
        }
    }

    private static boolean isActiveOwner(ProjectMember member) {
        return member != null
                && "OWNER".equals(member.role())
                && "ACTIVE".equals(member.membershipStatus());
    }

    private static int rank(String role) {
        return switch (role) {
            case "MEMBER" -> 0;
            case "ADMIN" -> 1;
            case "OWNER" -> 2;
            default -> 0;
        };
    }
}
