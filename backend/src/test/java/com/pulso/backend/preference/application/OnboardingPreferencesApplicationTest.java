package com.pulso.backend.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

class OnboardingPreferencesApplicationTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void savesPreferencesForTheAuthenticatedProfile() {
        CapturingRepository repository = new CapturingRepository();
        ActiveSectorLookup activeSectorLookup = identifier -> identifier.equals("sostenibilidad")
                ? Optional.of(SECTOR_ID)
                : Optional.empty();
        OnboardingPreferences preferences = preferences(PROFILE_ID);

        OnboardingPreferences result = new SaveOnboardingPreferences(
                repository,
                activeSectorLookup)
                .execute(PROFILE_ID, preferences);

        assertThat(repository.saved).isSameAs(preferences);
        assertThat(result).isSameAs(preferences);
    }

    @Test
    void returnsEmptyPreferencesWhenTheProfileHasNotSavedAnyYet() {
        OnboardingPreferences result = new GetOnboardingPreferences(
                new OnboardingPreferencesRepository() {
                    @Override
                    public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
                        return Optional.empty();
                    }

                    @Override
                    public OnboardingPreferences upsert(OnboardingPreferences preferences) {
                        throw new UnsupportedOperationException();
                    }
                })
                .execute(PROFILE_ID);

        assertThat(result.profileId()).isEqualTo(PROFILE_ID);
        assertThat(result.intentions()).isEmpty();
        assertThat(result.preferredSectors()).isEmpty();
        assertThat(result.preferredStages()).isEmpty();
    }

    @Test
    void rejectsAnInactiveOrUnknownSectorBeforeWriting() {
        CapturingRepository repository = new CapturingRepository();
        OnboardingPreferences preferences = new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of("desconocido"),
                List.of());

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                repository,
                identifier -> Optional.empty())
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors contains unknown or inactive sector 'desconocido'.");
        assertThat(repository.saved).isNull();
    }

    @Test
    void rejectsAMissingAuthenticatedProfileIdBeforeWriting() {
        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID))
                .execute(null, preferences(PROFILE_ID)))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("authenticatedProfileId is required.");
    }

    @Test
    void propagatesRepositoryFailuresWhenSaving() {
        OnboardingPreferencesRepository unavailableRepository = new OnboardingPreferencesRepository() {
            @Override
            public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
                return Optional.empty();
            }

            @Override
            public OnboardingPreferences upsert(OnboardingPreferences preferences) {
                throw new DataAccessResourceFailureException("database unavailable");
            }
        };

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                unavailableRepository,
                identifier -> Optional.of(SECTOR_ID))
                .execute(PROFILE_ID, preferences(PROFILE_ID)))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("database unavailable");
    }

    @Test
    void rejectsEquivalentSectorReferencesAsDuplicates() {
        OnboardingPreferences preferences = new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of("sostenibilidad", SECTOR_ID.toString()),
                List.of());

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID))
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors must not contain duplicates: '" + SECTOR_ID + "'.");
    }

    @Test
    void cannotSaveASelectionBelongingToAnotherProfile() {
        OnboardingPreferences preferences = preferences(OTHER_PROFILE_ID);

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID))
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferences do not belong to the authenticated profile.");
    }

    private static OnboardingPreferences preferences(UUID profileId) {
        return new OnboardingPreferences(
                profileId,
                List.of("discover", "collaborate"),
                List.of("sostenibilidad"),
                List.of("mvp"));
    }

    private static final class CapturingRepository implements OnboardingPreferencesRepository {

        private OnboardingPreferences saved;

        @Override
        public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
            return Optional.empty();
        }

        @Override
        public OnboardingPreferences upsert(OnboardingPreferences preferences) {
            saved = preferences;
            return preferences;
        }
    }
}
