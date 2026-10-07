package com.pulso.backend.project.application;

import java.util.List;
import java.util.UUID;

import com.pulso.backend.project.domain.CurrentProject;

public interface CurrentProjectsRepository {
    List<CurrentProject> findActive(UUID profileId, int offset, int limit);
}
