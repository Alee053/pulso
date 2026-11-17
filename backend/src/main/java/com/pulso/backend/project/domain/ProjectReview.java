package com.pulso.backend.project.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.pulso.backend.config.ApiMessages;

public final class ProjectReview {
    private ProjectReview() { }

    public static ProjectReviewResult decide(
            ProjectReviewCandidate project, UUID reviewerId, String decision,
            String verificationStatus, String note, Instant now, int noteMaxLength) {
        if (!project.canReview()) {
            throw new ProjectReviewException(ProjectReviewException.Reason.OWN_PROJECT);
        }
        if (!"PENDING_REVIEW".equals(project.status())) {
            throw new ProjectReviewException(ProjectReviewException.Reason.STATE_CHANGED);
        }
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new ProjectValidationException(ApiMessages.PROJECT_REVIEW_INVALID_DECISION);
        }
        String reviewNote = note == null || note.isBlank() ? null : note.strip();
        if (reviewNote != null && reviewNote.length() > noteMaxLength) {
            throw new ProjectValidationException(
                    String.format(ApiMessages.PROJECT_REVIEW_NOTE_TOO_LONG, noteMaxLength));
        }
        boolean approve = "APPROVE".equals(decision);
        if (approve) {
            if (verificationStatus == null || !Set.of("VERIFIED", "UNVERIFIED").contains(verificationStatus)) {
                throw new ProjectValidationException(ApiMessages.PROJECT_REVIEW_INVALID_VERIFICATION);
            }
            if (!project.sectorActive() || project.activeOwnerCount() < 1) {
                throw new ProjectReviewException(ProjectReviewException.Reason.UNAVAILABLE);
            }
        } else {
            if (reviewNote == null) {
                throw new ProjectValidationException(ApiMessages.PROJECT_REVIEW_NOTE_REQUIRED);
            }
            if (verificationStatus != null) {
                throw new ProjectValidationException(ApiMessages.PROJECT_REVIEW_REJECTION_VERIFICATION);
            }
        }
        return new ProjectReviewResult(
                project.id(), approve ? "PUBLISHED" : "REJECTED",
                approve ? verificationStatus : project.verificationStatus(), reviewNote,
                reviewerId, now, approve && project.publishedAt() == null ? now : project.publishedAt());
    }
}
