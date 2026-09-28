package com.pulso.backend.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProjectUpdatePatchTest {

    private static final UUID SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final ProjectUpdatePatch.Limits LIMITS = new ProjectUpdatePatch.Limits(
            160,
            240,
            5000,
            120,
            40);

    @Test
    void normalizesOnlyTheEditableFields() {
        ProjectUpdatePatch patch = new ProjectUpdatePatch(
                "  EcoPack actualizado  ",
                "  Envases nuevos  ",
                "  Descripción  ",
                "  Menos residuos  ",
                "mvp",
                SECTOR_ID,
                "  Cochabamba  ",
                "  Cochabamba  ",
                LIMITS);

        assertThat(patch.name()).isEqualTo("EcoPack actualizado");
        assertThat(patch.tagline()).isEqualTo("Envases nuevos");
        assertThat(patch.description()).isEqualTo("Descripción");
        assertThat(patch.valueProposition()).isEqualTo("Menos residuos");
        assertThat(patch.stage()).isEqualTo("MVP");
        assertThat(patch.sectorId()).isEqualTo(SECTOR_ID);
        assertThat(patch.city()).isEqualTo("Cochabamba");
        assertThat(patch.department()).isEqualTo("Cochabamba");
    }

    @Test
    void allowsOmittedFieldsSoTheRepositoryCanPreserveThem() {
        ProjectUpdatePatch patch = new ProjectUpdatePatch(
                "Nuevo nombre",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LIMITS);

        assertThat(patch.name()).isEqualTo("Nuevo nombre");
        assertThat(patch.tagline()).isNull();
        assertThat(patch.valueProposition()).isNull();
        assertThat(patch.sectorId()).isNull();
    }

    @Test
    void rejectsBlankRequiredValuesWhenTheyAreProvided() {
        assertThatThrownBy(() -> new ProjectUpdatePatch(
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("name must not be blank.");

        assertThatThrownBy(() -> new ProjectUpdatePatch(
                null,
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("tagline must not be blank.");
    }

    @Test
    void distinguishesAnExplicitNullableValueFromAnOmittedRequiredValue() {
        ProjectUpdatePatch patch = new ProjectUpdatePatch(
                null,
                false,
                null,
                false,
                null,
                true,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                LIMITS);

        assertThat(patch.descriptionProvided()).isTrue();
        assertThat(patch.description()).isNull();

        assertThatThrownBy(() -> new ProjectUpdatePatch(
                null,
                true,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("name is required.");

        assertThatThrownBy(() -> new ProjectUpdatePatch(
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                true,
                null,
                false,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("city is required.");
    }

    @Test
    void rejectsUnknownStagesAndValuesBeyondContractLimits() {
        assertThatThrownBy(() -> new ProjectUpdatePatch(
                null,
                null,
                null,
                null,
                "LAUNCHED",
                null,
                null,
                null,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessageContaining("stage must be one of");

        assertThatThrownBy(() -> new ProjectUpdatePatch(
                null,
                null,
                "x".repeat(5001),
                null,
                null,
                null,
                null,
                null,
                LIMITS))
                .isInstanceOf(ProjectValidationException.class)
                .hasMessage("description must not exceed 5000 characters.");
    }
}
