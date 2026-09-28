package com.pulso.backend.project.application;

import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

public interface ProjectUpdateRepository {

    boolean hasActiveSector(UUID sectorId);

    Optional<UpdatedProject> updateInformation(
            UUID projectId,
            UUID authenticatedProfileId,
            ProjectUpdatePatch patch);
}
