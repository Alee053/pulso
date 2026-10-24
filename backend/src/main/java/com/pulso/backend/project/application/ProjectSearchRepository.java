package com.pulso.backend.project.application;

import java.util.List;

import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.domain.ProjectSearchFilters;

public interface ProjectSearchRepository {

    List<ProjectSummary> search(ProjectSearchFilters filters, int offset, int limit);

    long count(ProjectSearchFilters filters);
}
