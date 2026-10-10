package com.pulso.backend.opportunity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpportunityContentTest {

    @Test
    void normalizesTheTypeAndTrimsRequiredAndOptionalFields() {
        OpportunityContent content = OpportunityContent.validate(
                " mentor ", "  Mentoría  ", "  Acompañamiento mensual  ",
                "  La Paz ", "   ", false);

        assertThat(content.opportunityType()).isEqualTo("MENTOR");
        assertThat(content.title()).isEqualTo("Mentoría");
        assertThat(content.description()).isEqualTo("Acompañamiento mensual");
        assertThat(content.city()).isEqualTo("La Paz");
        assertThat(content.department()).isNull();
        assertThat(content.remoteAllowed()).isFalse();
    }

    @Test
    void acceptsRemoteOpportunitiesWithoutALocation() {
        OpportunityContent content = OpportunityContent.validate(
                "TALENT", "Diseño de producto", "Apoyo para entrevistas", null, null, true);

        assertThat(content.remoteAllowed()).isTrue();
        assertThat(content.city()).isNull();
        assertThat(content.department()).isNull();
    }

    @Test
    void requiresTheDocumentedTypeAndRequiredFields() {
        assertThatThrownBy(() -> OpportunityContent.validate(
                "UNKNOWN", "Título", "Descripción", "La Paz", null, false))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessageContaining("opportunityType");
        assertThatThrownBy(() -> OpportunityContent.validate(
                "TALENT", " ", "Descripción", "La Paz", null, false))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("title is required.");
        assertThatThrownBy(() -> OpportunityContent.validate(
                "TALENT", "Título", null, "La Paz", null, false))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("description is required.");
    }

    @Test
    void requiresLocationForNonRemoteOpportunitiesAndAnExplicitRemoteOption() {
        assertThatThrownBy(() -> OpportunityContent.validate(
                "TALENT", "Título", "Descripción", null, "", false))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("city or department is required when remoteAllowed is false.");
        assertThatThrownBy(() -> OpportunityContent.validate(
                "TALENT", "Título", "Descripción", "La Paz", null, null))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("remoteAllowed is required.");
    }
}
