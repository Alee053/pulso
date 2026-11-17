package com.pulso.backend.project.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulso.backend.config.ProjectReviewProperties;
import com.pulso.backend.project.domain.ProjectReview;
import com.pulso.backend.project.domain.ProjectReviewCandidate;
import com.pulso.backend.project.domain.ProjectReviewException;
import com.pulso.backend.project.domain.ProjectReviewResult;

@Service
public class ReviewProjects {
    private final ProjectReviewRepository repository;
    private final ProjectReviewProperties properties;
    private final Clock clock;

    @Autowired
    public ReviewProjects(ProjectReviewRepository repository, ProjectReviewProperties properties) {
        this(repository, properties, Clock.systemUTC());
    }

    public ReviewProjects(ProjectReviewRepository repository, ProjectReviewProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectReviewCandidate> list(UUID reviewerId, int offset, int limit) {
        if (reviewerId == null || !repository.isActiveReviewer(reviewerId)) {
            throw new ProjectReviewException(ProjectReviewException.Reason.FORBIDDEN);
        }
        return repository.findPending(reviewerId, offset, limit);
    }

    @Transactional
    public ProjectReviewResult decide(UUID projectId, UUID reviewerId,
            String decision, String verificationStatus, String note) {
        if (reviewerId == null || !repository.lockActiveReviewer(reviewerId)) {
            throw new ProjectReviewException(ProjectReviewException.Reason.FORBIDDEN);
        }
        ProjectReviewCandidate project = repository.lockCandidate(projectId, reviewerId)
                .orElseThrow(() -> new ProjectReviewException(ProjectReviewException.Reason.NOT_FOUND));
        ProjectReviewResult result = ProjectReview.decide(
                project, reviewerId, decision, verificationStatus, note, Instant.now(clock), properties.noteMaxLength());
        if (repository.updateReview(result) != 1) {
            throw new ProjectReviewException(ProjectReviewException.Reason.STATE_CHANGED);
        }
        repository.appendAudit(result);
        return result;
    }
}
