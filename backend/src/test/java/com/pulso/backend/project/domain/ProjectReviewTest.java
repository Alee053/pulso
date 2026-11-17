package com.pulso.backend.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectReviewTest {
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-16T18:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"VERIFIED", "UNVERIFIED"})
    void approvalPublishesWithExplicitBasicVerificationAndServerAuditValues(String verification) {
        var result = ProjectReview.decide(candidate("PENDING_REVIEW", true, true, 1, null),
                ACTOR, "APPROVE", verification, "  Revisado  ", NOW, 2000);
        assertThat(result).isEqualTo(new ProjectReviewResult(PROJECT, "PUBLISHED", verification,
                "Revisado", ACTOR, NOW, NOW));
    }

    @Test
    void rejectionPreservesVerificationAndDoesNotInventAPublicationDate() {
        var result = ProjectReview.decide(candidate("PENDING_REVIEW", true, true, 1, null),
                ACTOR, "REJECT", null, "  Completar descripción  ", NOW, 2000);
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.reviewNote()).isEqualTo("Completar descripción");
        assertThat(result.verificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(result.publishedAt()).isNull();
    }

    @Test
    void preservesTheFirstPublicationDate() {
        Instant firstPublication = NOW.minusSeconds(3600);
        assertThat(ProjectReview.decide(candidate("PENDING_REVIEW", true, true, 1, firstPublication),
                ACTOR, "APPROVE", "VERIFIED", null, NOW, 2000).publishedAt()).isEqualTo(firstPublication);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED", "REJECTED", "ARCHIVED", "SUSPENDED"})
    void onlyPendingProjectsCanReceiveADecision(String status) {
        assertThatThrownBy(() -> ProjectReview.decide(candidate(status, true, true, 1, null),
                ACTOR, "APPROVE", "VERIFIED", null, NOW, 2000))
                .isInstanceOf(ProjectReviewException.class)
                .extracting(error -> ((ProjectReviewException) error).reason())
                .isEqualTo(ProjectReviewException.Reason.STATE_CHANGED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVE", "REJECT"})
    void cannotReviewOwnProjectEvenWithAPlatformRole(String decision) {
        assertThatThrownBy(() -> ProjectReview.decide(candidate("PENDING_REVIEW", false, true, 1, null),
                ACTOR, decision, "VERIFIED", "Nota", NOW, 2000))
                .isInstanceOf(ProjectReviewException.class)
                .extracting(error -> ((ProjectReviewException) error).reason())
                .isEqualTo(ProjectReviewException.Reason.OWN_PROJECT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void rejectionRequiresANote(String note) {
        assertThatThrownBy(() -> ProjectReview.decide(candidate("PENDING_REVIEW", true, true, 1, null),
                ACTOR, "REJECT", null, note, NOW, 2000)).isInstanceOf(ProjectValidationException.class);
    }

    @Test
    void approvalNeedsAnActiveSectorAndAnActiveOwnerButRejectionRemainsPossible() {
        for (var candidate : new ProjectReviewCandidate[] {
                candidate("PENDING_REVIEW", true, false, 1, null),
                candidate("PENDING_REVIEW", true, true, 0, null)}) {
            assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "APPROVE", "VERIFIED", null, NOW, 2000))
                    .isInstanceOf(ProjectReviewException.class);
            assertThat(ProjectReview.decide(candidate, ACTOR, "REJECT", null, "Falta información", NOW, 2000).status())
                    .isEqualTo("REJECTED");
        }
    }

    @Test
    void validatesDecisionNoteLimitAndApprovalVerification() {
        var candidate = candidate("PENDING_REVIEW", true, true, 1, null);
        assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "PUBLISH", "VERIFIED", null, NOW, 2000))
                .isInstanceOf(ProjectValidationException.class);
        assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "REJECT", null, "too long", NOW, 3))
                .isInstanceOf(ProjectValidationException.class);
        assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "APPROVE", "PENDING", null, NOW, 2000))
                .isInstanceOf(ProjectValidationException.class);
        assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "APPROVE", null, null, NOW, 2000))
                .isInstanceOf(ProjectValidationException.class);
        assertThatThrownBy(() -> ProjectReview.decide(candidate, ACTOR, "REJECT", "VERIFIED", "Nota", NOW, 2000))
                .isInstanceOf(ProjectValidationException.class);
    }

    private static ProjectReviewCandidate candidate(String status, boolean canReview, boolean sectorActive,
            int owners, Instant publishedAt) {
        return new ProjectReviewCandidate(PROJECT, "Proyecto", "Propuesta", "Descripción", "Valor",
                "Tecnología", sectorActive, "MVP", "La Paz", "La Paz", status, "UNVERIFIED", "Creador",
                owners, canReview, NOW.minusSeconds(7200), publishedAt);
    }
}
