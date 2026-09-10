package com.pulso.backend.preference.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OnboardingPreferencesTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Test
    void normalizesSupportedValuesAndKeepsTheSelectionsImmutable() {
        OnboardingPreferences preferences = new OnboardingPreferences(
                PROFILE_ID,
                List.of(" discover ", "collaborate"),
                List.of(
                        " SOSTENIBILIDAD ",
                        "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA"),
                List.of("mvp", " early_stage "));

        assertThat(preferences.intentions())
                .containsExactly("DISCOVER", "COLLABORATE");
        assertThat(preferences.preferredSectors())
                .containsExactly(
                        "sostenibilidad",
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(preferences.preferredStages())
                .containsExactly("MVP", "EARLY_STAGE");
    }

    @Test
    void rejectsUnknownIntentions() {
        assertThatThrownBy(() -> new OnboardingPreferences(
                PROFILE_ID,
                List.of("UNKNOWN_INTENTION"),
                List.of(),
                List.of()))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("intentions contains unknown value 'UNKNOWN_INTENTION'.");
    }

    @Test
    void rejectsUnknownStages() {
        assertThatThrownBy(() -> new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of(),
                List.of("LAUNCHED")))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredStages contains unknown value 'LAUNCHED'.");
    }

    @Test
    void rejectsDuplicatesAfterNormalization() {
        assertThatThrownBy(() -> new OnboardingPreferences(
                PROFILE_ID,
                List.of("discover", "DISCOVER"),
                List.of(),
                List.of()))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("intentions must not contain duplicates: 'DISCOVER'.");
    }

    @Test
    void rejectsSelectionsThatExceedTheConfiguredLimit() {
        assertThatThrownBy(() -> new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of(
                        "agro",
                        "comercio",
                        "educacion",
                        "fintech",
                        "salud",
                        "tecnologia",
                        "turismo",
                        "sostenibilidad",
                        "alimentacion",
                        "movilidad",
                        "manufactura"),
                List.of()))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors must not contain more than 10 items.");
    }

    @Test
    void rejectsMalformedSectorReferences() {
        assertThatThrownBy(() -> new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of("sector with spaces"),
                List.of()))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors contains an invalid identifier 'sector with spaces'.");
    }
}
