package com.pulso.backend.project.domain;

import java.util.Set;
import java.util.UUID;

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
            throw new ProjectValidationException("projectId is required.");
        }
        if (profileId == null) {
            throw new ProjectValidationException("profileId is required.");
        }
        role = MembershipAdministrationValues.requireKnown(role, "role", KNOWN_ROLES);
        membershipStatus = MembershipAdministrationValues.requireKnown(
                membershipStatus,
                "membershipStatus",
                KNOWN_MEMBERSHIP_STATUSES);
    }
}
