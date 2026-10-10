package com.pulso.backend.opportunity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpportunityLifecycleTest {

    @Test
    void defaultsToTheCanonicalOpenStateAndAllowsDraft() {
        assertThat(OpportunityLifecycle.initialStatus(null)).isEqualTo("OPEN");
        assertThat(OpportunityLifecycle.initialStatus(" draft ")).isEqualTo("DRAFT");
        assertThat(OpportunityLifecycle.initialStatus("open")).isEqualTo("OPEN");
    }

    @Test
    void rejectsStatusesOutsideTheCreationContract() {
        assertThatThrownBy(() -> OpportunityLifecycle.initialStatus("PAUSED"))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("status must be one of DRAFT, OPEN.");
    }

    @Test
    void supportsTheDocumentedOpenPauseCloseAndArchiveTransitions() {
        assertThat(OpportunityLifecycle.transition("DRAFT", "OPEN")).isEqualTo("OPEN");
        assertThat(OpportunityLifecycle.transition("OPEN", "PAUSE")).isEqualTo("PAUSED");
        assertThat(OpportunityLifecycle.transition("PAUSED", "OPEN")).isEqualTo("OPEN");
        assertThat(OpportunityLifecycle.transition("OPEN", "CLOSE")).isEqualTo("CLOSED");
        assertThat(OpportunityLifecycle.transition("PAUSED", "CLOSE")).isEqualTo("CLOSED");
        assertThat(OpportunityLifecycle.transition("CLOSED", "ARCHIVE")).isEqualTo("ARCHIVED");
        assertThat(OpportunityLifecycle.transition("FILLED", "ARCHIVE")).isEqualTo("ARCHIVED");
    }

    @Test
    void rejectsActionsThatDoNotMatchTheCurrentState() {
        assertThatThrownBy(() -> OpportunityLifecycle.transition("CLOSED", "OPEN"))
                .isInstanceOf(OpportunityConflictException.class)
                .hasMessage("cannot change opportunity from CLOSED to OPEN.");
        assertThatThrownBy(() -> OpportunityLifecycle.transition("ARCHIVED", "PAUSE"))
                .isInstanceOf(OpportunityConflictException.class);
        assertThatThrownBy(() -> OpportunityLifecycle.transition("DRAFT", "PAUSE"))
                .isInstanceOf(OpportunityConflictException.class);
        assertThatThrownBy(() -> OpportunityLifecycle.transition("OPEN", "DELETE"))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("action must be one of OPEN, PAUSE, CLOSE, ARCHIVE.");
    }
}
