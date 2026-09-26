package com.pulso.backend.project.domain;

import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

public record MembershipActor(
        UUID projectId,
        UUID profileId,
        String role,
        String membershipStatus) {

    private static final Set<String> KNOWN_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
    private static final Set<String> KNOWN_MEMBERSHIP_STATUSES = Set.of(
            "INVITED",
            "ACTIVE",
            "LEFT",
            "REMOVED");

    public MembershipActor {
        if (projectId == null) {
            throw new ProjectValidationException(ApiMessages.PROJECT_ID_REQUIRED);
        }
        if (profileId == null) {
            throw new ProjectValidationException(ApiMessages.PROFILE_ID_REQUIRED);
        }
        role = MembershipAdministrationValues.requireKnown(role, "role", KNOWN_ROLES);
        membershipStatus = MembershipAdministrationValues.requireKnown(
                membershipStatus,
                "membershipStatus",
                KNOWN_MEMBERSHIP_STATUSES);
    }
}
