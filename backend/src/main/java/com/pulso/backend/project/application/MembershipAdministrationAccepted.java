package com.pulso.backend.project.application;

import java.util.UUID;

/** Contains only identifiers and policy values needed to audit an accepted change. */
public record MembershipAdministrationAccepted(
        UUID actorProfileId,
        String action,
        UUID projectId,
        UUID memberId,
        String previousRole,
        String nextRole,
        String previousStatus,
        String nextStatus) {
}
