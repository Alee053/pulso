package com.pulso.backend.project.domain;

import java.util.Set;

public record ProjectLifecycleActor(
        String role,
        String membershipStatus) {

    private static final Set<String> KNOWN_ROLES = Set.of("OWNER", "ADMIN", "MEMBER");
    private static final Set<String> KNOWN_MEMBERSHIP_STATUSES = Set.of(
            "INVITED",
            "ACTIVE",
            "LEFT",
            "REMOVED");

    public ProjectLifecycleActor {
        role = ProjectLifecycleValues.requireKnown(role, "role", KNOWN_ROLES);
        membershipStatus = ProjectLifecycleValues.requireKnown(
                membershipStatus,
                "membershipStatus",
                KNOWN_MEMBERSHIP_STATUSES);
    }
}
