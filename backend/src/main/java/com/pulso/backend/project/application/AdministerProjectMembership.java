package com.pulso.backend.project.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.project.domain.MembershipAdministration;
import com.pulso.backend.project.domain.MembershipActor;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ProjectValidationException;

@Service
public class AdministerProjectMembership {

    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "activate",
            "changeRole",
            "withdraw");

    private final MembershipAdministrationRepository repository;
    private final ApplicationEventPublisher events;

    public AdministerProjectMembership(
            MembershipAdministrationRepository repository,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public ProjectMember execute(
            UUID projectId,
            UUID memberId,
            UUID authenticatedProfileId,
            String action,
            String requestedRole) {
        requireIdentifiers(projectId, memberId, authenticatedProfileId);
        String normalizedAction = requireAction(action);

        MembershipActor actor = repository.findActor(projectId, authenticatedProfileId)
                .orElseThrow(MembershipPermissionException::new);
        repository.lockProject(projectId);
        actor = repository.findActor(projectId, authenticatedProfileId)
                .orElseThrow(MembershipPermissionException::new);
        ProjectMember target = repository.findMember(projectId, memberId)
                .orElseThrow(MembershipPermissionException::new);
        List<ProjectMember> members = repository.findMembers(projectId);

        ProjectMember next = apply(normalizedAction, actor, target, requestedRole, members);
        int updated = repository.updateMembership(
                projectId,
                memberId,
                target.role(),
                target.membershipStatus(),
                next.role(),
                next.membershipStatus());
        if (updated != 1) {
            throw new MembershipConflictException();
        }
        events.publishEvent(new MembershipAdministrationAccepted(
                authenticatedProfileId,
                normalizedAction,
                projectId,
                memberId,
                target.role(),
                next.role(),
                target.membershipStatus(),
                next.membershipStatus()));
        return next;
    }

    private static ProjectMember apply(
            String action,
            MembershipActor actor,
            ProjectMember target,
            String requestedRole,
            List<ProjectMember> members) {
        return switch (action) {
            case "activate" -> MembershipAdministration.activate(actor, target, members);
            case "changeRole" -> MembershipAdministration.changeRole(
                    actor,
                    target,
                    requestedRole,
                    members);
            case "withdraw" -> MembershipAdministration.withdraw(actor, target, members);
            default -> throw new ProjectValidationException(
                    String.format(ApiMessages.KNOWN_VALUE_UNKNOWN, "action", action));
        };
    }

    private static String requireAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.KNOWN_VALUE_REQUIRED, "action"));
        }
        String normalized = action.trim();
        if (!KNOWN_ACTIONS.contains(normalized)) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.KNOWN_VALUE_UNKNOWN, "action", normalized));
        }
        return normalized;
    }

    private static void requireIdentifiers(
            UUID projectId,
            UUID memberId,
            UUID authenticatedProfileId) {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (memberId == null) {
            throw new ProjectValidationException(ApiMessages.MEMBERSHIP_ID_REQUIRED);
        }
        if (authenticatedProfileId == null) {
            throw new ProjectValidationException(ApiMessages.AUTHENTICATED_PROFILE_REQUIRED);
        }
    }
}
