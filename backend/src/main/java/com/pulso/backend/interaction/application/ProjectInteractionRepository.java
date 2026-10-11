package com.pulso.backend.interaction.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectSummary;

public interface ProjectInteractionRepository {

    List<DiscoveryFeedItem> findDiscoveryFeed(
            UUID profileId,
            List<String> preferredSectors,
            List<String> preferredStages,
            int offset,
            DiscoveryFeedCursor cursor,
            int limit);

    long countDiscoveryFeed(UUID profileId);

    List<ProjectSummary> findSavedProjects(UUID profileId, int offset, int limit);

    long countSavedProjects(UUID profileId);

    List<ProjectSummary> findInterestedProjects(UUID profileId, int offset, int limit);

    long countInterestedProjects(UUID profileId);

    void setSaved(UUID profileId, UUID projectId, boolean saved);

    void setFeedback(UUID profileId, UUID projectId, ProjectFeedbackType feedbackType);

void insertSharedEvent(UUID profileId, UUID projectId, com.pulso.backend.project.domain.ProjectShareOrigin origin);
}
