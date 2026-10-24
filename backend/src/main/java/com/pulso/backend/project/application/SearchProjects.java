package com.pulso.backend.project.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@Service
public class SearchProjects {

    private final ProjectSearchRepository repository;

    public SearchProjects(ProjectSearchRepository repository) {
        this.repository = repository;
    }

    public SearchResult execute(ProjectSearchFilters filters, int offset, int limit) {
        List<ProjectSummary> data = repository.search(filters, offset, limit);
        long totalCount = repository.count(filters);
        return new SearchResult(data, totalCount);
    }

    public record SearchResult(List<ProjectSummary> data, long totalCount) {}
}
