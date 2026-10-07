package com.pulso.backend.interaction.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.interaction.domain.ProjectPage;
import com.pulso.backend.interaction.domain.ProjectSummary;

@Service
public class ListProjectCollection {

    public enum CollectionType {
        SAVED,
        INTERESTED
    }

    private final ProjectInteractionRepository repository;

    public ListProjectCollection(ProjectInteractionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProjectPage<ProjectSummary> execute(
            UUID profileId,
            CollectionType type,
            int offset,
            int limit) {
        List<ProjectSummary> rows = type == CollectionType.SAVED
                ? repository.findSavedProjects(profileId, offset, limit + 1)
                : repository.findInterestedProjects(profileId, offset, limit + 1);
        boolean hasMore = rows.size() > limit;
        long totalCount = type == CollectionType.SAVED
                ? repository.countSavedProjects(profileId)
                : repository.countInterestedProjects(profileId);
        return new ProjectPage<>(rows.stream().limit(limit).toList(), offset, limit, hasMore, totalCount);
    }
}
