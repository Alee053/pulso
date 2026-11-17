package com.pulso.backend.project.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pulso.backend.project.domain.ProjectReviewCandidate;
import com.pulso.backend.project.domain.ProjectReviewResult;

public interface ProjectReviewRepository {
    boolean isActiveReviewer(UUID profileId);
    boolean lockActiveReviewer(UUID profileId);
    List<ProjectReviewCandidate> findPending(UUID reviewerId, int offset, int limit);
    Optional<ProjectReviewCandidate> lockCandidate(UUID projectId, UUID reviewerId);
    int updateReview(ProjectReviewResult result);
    void appendAudit(ProjectReviewResult result);
}
