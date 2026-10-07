package com.pulso.backend.project.application;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMembersReader {

    // Return the persisted active actor and its authorized page from one read snapshot.
    // Missing authorization is distinct from an authorized empty page.
    Optional<ProjectMembersSnapshot> findAuthorizedPage(UUID projectId, UUID profileId, int offset, int limit);
}
