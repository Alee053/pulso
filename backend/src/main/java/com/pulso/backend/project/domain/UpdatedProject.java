package com.pulso.backend.project.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The canonical project row returned after an information update.
 *
 * The complete row is kept at the application boundary so persistence tests
 * can prove that server-managed review and publication data survived the
 * update. HTTP mapping decides which fields belong to the edit response.
 */
public record UpdatedProject(
        UUID id,
        String slug,
        String name,
        String tagline,
        String description,
        String valueProposition,
        UUID sectorId,
        String stage,
        String city,
        String department,
        String status,
        String verificationStatus,
        UUID createdByProfileId,
        UUID reviewedByProfileId,
        String reviewNote,
        OffsetDateTime reviewedAt,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime archivedAt) {
}
