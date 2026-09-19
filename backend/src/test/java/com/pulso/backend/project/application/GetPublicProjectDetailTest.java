package com.pulso.backend.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.pulso.backend.project.domain.PublicProjectDetail;

class GetPublicProjectDetailTest {

    @Test
    void returnsThePublishedProjectFoundByItsReference() {
        PublicProjectDetail expected = new PublicProjectDetail(
                null,
                "eco-pack",
                "EcoPack Bolivia",
                "Envases",
                null,
                null,
                null,
                "MVP",
                null,
                null,
                null,
                null,
                null);
        GetPublicProjectDetail useCase = new GetPublicProjectDetail(
                reference -> Optional.of(expected));

        assertThat(useCase.execute("eco-pack")).isSameAs(expected);
    }

    @Test
    void hidesMissingAndUnpublishedProjectsBehindTheSameException() {
        GetPublicProjectDetail useCase = new GetPublicProjectDetail(
                reference -> Optional.empty());

        assertThatThrownBy(() -> useCase.execute("draft-project"))
                .isInstanceOf(PublicProjectNotFoundException.class)
                .hasMessage("The project does not exist or is not publicly available.");
    }

    @Test
    void rejectsAnEmptyReferenceAsNotFound() {
        GetPublicProjectDetail useCase = new GetPublicProjectDetail(
                reference -> Optional.empty());

        assertThatThrownBy(() -> useCase.execute("  "))
                .isInstanceOf(PublicProjectNotFoundException.class);
    }
}
