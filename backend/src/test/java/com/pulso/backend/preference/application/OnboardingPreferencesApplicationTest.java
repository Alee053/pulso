package com.pulso.backend.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;

class OnboardingPreferencesApplicationTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAG_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SKILL_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final PreferenceProperties PROPERTIES =
            new PreferenceProperties(10, 40, 120);
    private static final OnboardingPreferences.Limits LIMITS =
            new OnboardingPreferences.Limits(10, 40, 120);

    @Test
    void savesPreferencesForTheAuthenticatedProfile() {
        CapturingRepository repository = new CapturingRepository();
        ActiveSectorLookup activeSectorLookup = identifier -> identifier.equals("sostenibilidad")
                ? Optional.of(SECTOR_ID)
                : Optional.empty();
        OnboardingPreferences preferences = preferences(PROFILE_ID);

        OnboardingPreferences result = new SaveOnboardingPreferences(
                repository,
                activeSectorLookup,
                activePreferenceCatalogLookup(),
                PROPERTIES)
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

                    @Override
                    public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
                        throw new UnsupportedOperationException();
                    }
                },
                PROPERTIES)
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
                List.of(),
                LIMITS);

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                repository,
                identifier -> Optional.empty(),
                activePreferenceCatalogLookup(),
                PROPERTIES)
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors contains unknown or inactive sector 'desconocido'.");
        assertThat(repository.saved).isNull();
    }

    @Test
    void rejectsInactiveTagsAndSkillsBeforeWriting() {
        ActivePreferenceCatalogLookup inactiveCatalog = new ActivePreferenceCatalogLookup() {
            @Override
            public Set<UUID> findActiveTagIds(List<UUID> ids) {
                return Set.of();
            }

            @Override
            public Set<UUID> findActiveSkillIds(List<UUID> ids) {
                return Set.of();
            }
        };
        CapturingRepository repository = new CapturingRepository();
        OnboardingPreferences preferences = new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(TAG_ID.toString()),
                List.of(SKILL_ID.toString()),
                Map.of(),
                LIMITS);

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                repository,
                identifier -> Optional.of(SECTOR_ID),
                inactiveCatalog,
                PROPERTIES)
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredTags contains an unknown or inactive tag.");
        assertThat(repository.saved).isNull();

        OnboardingPreferences skillOnly = new OnboardingPreferences(
                PROFILE_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(SKILL_ID.toString()),
                Map.of(),
                LIMITS);
        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                repository,
                identifier -> Optional.of(SECTOR_ID),
                inactiveCatalog,
                PROPERTIES)
                .execute(PROFILE_ID, skillOnly))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSkills contains an unknown or inactive skill.");
        assertThat(repository.saved).isNull();
    }

    @Test
    void rejectsAMissingAuthenticatedProfileIdBeforeWriting() {
        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID),
                activePreferenceCatalogLookup(),
                PROPERTIES)
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

            @Override
            public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
                throw new UnsupportedOperationException();
            }
        };

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                unavailableRepository,
                identifier -> Optional.of(SECTOR_ID),
                activePreferenceCatalogLookup(),
                PROPERTIES)
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
                List.of(),
                LIMITS);

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID),
                activePreferenceCatalogLookup(),
                PROPERTIES)
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferredSectors must not contain duplicates: '" + SECTOR_ID + "'.");
    }

    @Test
    void cannotSaveASelectionBelongingToAnotherProfile() {
        OnboardingPreferences preferences = preferences(OTHER_PROFILE_ID);

        assertThatThrownBy(() -> new SaveOnboardingPreferences(
                new CapturingRepository(),
                identifier -> Optional.of(SECTOR_ID),
                activePreferenceCatalogLookup(),
                PROPERTIES)
                .execute(PROFILE_ID, preferences))
                .isInstanceOf(OnboardingPreferencesValidationException.class)
                .hasMessage("preferences do not belong to the authenticated profile.");
    }

    private static OnboardingPreferences preferences(UUID profileId) {
        return new OnboardingPreferences(
                profileId,
                List.of("discover", "collaborate"),
                List.of("sostenibilidad"),
                List.of("mvp"),
                LIMITS);
    }

    private static ActivePreferenceCatalogLookup activePreferenceCatalogLookup() {
        return new ActivePreferenceCatalogLookup() {
            @Override
            public Set<UUID> findActiveTagIds(List<UUID> ids) {
                return Set.copyOf(ids);
            }

            @Override
            public Set<UUID> findActiveSkillIds(List<UUID> ids) {
                return Set.copyOf(ids);
            }
        };
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

        @Override
        public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
            throw new UnsupportedOperationException();
        }
    }
}
