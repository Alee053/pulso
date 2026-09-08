package com.pulso.backend.project.application;

import java.util.UUID;

import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;

public interface ProjectRepository {

    boolean hasActiveSector(UUID sectorId);

    Project createDraft(ProjectDraft draft, UUID createdByProfileId);

    ProjectMember createOwner(UUID projectId, UUID profileId);
}
