package com.pulso.backend.preference.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.config.PreferenceProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.preference.application.GetOnboardingPreferences;
import com.pulso.backend.preference.application.ActivePreferenceCatalogLookup;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.application.SaveOnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.preference.domain.ProfileSkill;

class OnboardingPreferencesControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
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
    void returnsTheAuthenticatedPreferencesContract() throws Exception {
        OnboardingPreferences saved = preferences(
                List.of("DISCOVER", "COLLABORATE"),
                List.of("sostenibilidad"),
                List.of("MVP"));
        MockMvc mockMvc = mockMvc(new FixedRepository(saved), identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(get("/api/v1/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.intentions[0]").value("DISCOVER"))
                .andExpect(jsonPath("$.intentions[1]").value("COLLABORATE"))
                .andExpect(jsonPath("$.preferredSectors[0]").value("sostenibilidad"))
                .andExpect(jsonPath("$.preferredStages[0]").value("MVP"))
                .andExpect(jsonPath("$.preferredTags[0]").value(TAG_ID.toString()))
                .andExpect(jsonPath("$.preferredSkills[0]").value(SKILL_ID.toString()));
    }

    @Test
    void savesNormalizedPreferencesForTheJwtSubject() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        MockMvc mockMvc = mockMvc(repository, identifier -> identifier.equals("sostenibilidad")
                ? Optional.of(SECTOR_ID)
                : Optional.empty());

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["discover", "collaborate"],
                          "preferredSectors": [" SOSTENIBILIDAD "],
                          "preferredStages": ["mvp"],
                          "preferredTags": ["bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"],
                          "preferredSkills": ["cccccccc-cccc-cccc-cccc-cccccccccccc"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.intentions[0]").value("DISCOVER"))
                .andExpect(jsonPath("$.preferredSectors[0]").value("sostenibilidad"))
                .andExpect(jsonPath("$.preferredStages[0]").value("MVP"))
                .andExpect(jsonPath("$.preferredTags[0]").value(TAG_ID.toString()))
                .andExpect(jsonPath("$.preferredSkills[0]").value(SKILL_ID.toString()));

        assertThat(repository.saved.profileId())
                .isEqualTo(PROFILE_ID);
        assertThat(repository.saved.preferredTags()).containsExactly(TAG_ID.toString());
        assertThat(repository.saved.preferredSkills()).containsExactly(SKILL_ID.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"})
    void acceptsSkillLevelsAndReturnsTheAdditiveContract(String proficiency) throws Exception {
        CapturingRepository repository = new CapturingRepository();
        MockMvc mockMvc = mockMvc(repository, identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["collaborate"],
                          "preferredStages": [],
                          "skills": [{"skillId": "cccccccc-cccc-cccc-cccc-cccccccccccc", "proficiency": "%s"}],
                          "completeOnboarding": true
                        }
                        """.formatted(proficiency.toLowerCase(java.util.Locale.ROOT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSkills[0]").value(SKILL_ID.toString()))
                .andExpect(jsonPath("$.skills[0].skillId").value(SKILL_ID.toString()))
                .andExpect(jsonPath("$.skills[0].proficiency").value(proficiency));
        assertThat(repository.completeOnboarding).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"skills\": [], \"preferredSkills\": []",
            "\"skills\": [{\"skillId\": \"cccccccc-cccc-cccc-cccc-cccccccccccc\", \"proficiency\": \"ADVANCED\", \"isPrimary\": true}]",
            "\"skills\": [{\"skillId\": \"cccccccc-cccc-cccc-cccc-cccccccccccc\", \"proficiency\": null}]",
            "\"skills\": [{\"skillId\": \"cccccccc-cccc-cccc-cccc-cccccccccccc\", \"proficiency\": \"MASTER\"}]",
            "\"skills\": [{\"skillId\": \"invalid\", \"proficiency\": \"ADVANCED\"}]",
            "\"skills\": [null]",
            "\"skills\": [{\"skillId\": \"cccccccc-cccc-cccc-cccc-cccccccccccc\", \"proficiency\": \"ADVANCED\"}, {\"skillId\": \"CCCCCCCC-CCCC-CCCC-CCCC-CCCCCCCCCCCC\", \"proficiency\": \"EXPERT\"}]"
    })
    void rejectsInvalidSkillSelectionsBeforeWriting(String selection) throws Exception {
        CapturingRepository repository = new CapturingRepository();
        mockMvc(repository, identifier -> Optional.of(SECTOR_ID))
                .perform(put("/api/v1/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intentions\": [\"DISCOVER\"], \"preferredStages\": [], " + selection + "}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.saved).isNull();
    }

    @Test
    void returnsSavedSkillLevelsAndCompletionForEditing() throws Exception {
        OnboardingPreferences saved = new OnboardingPreferences(PROFILE_ID,
                List.of("COLLABORATE"), List.of(), List.of(), List.of(),
                List.of(new ProfileSkill(SKILL_ID.toString(), "EXPERT")), Map.of(),
                OffsetDateTime.parse("2026-10-09T12:00:00Z"), LIMITS);
        mockMvc(new FixedRepository(saved), identifier -> Optional.of(SECTOR_ID))
                .perform(get("/api/v1/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].proficiency").value("EXPERT"))
                .andExpect(jsonPath("$.onboardingCompletedAt").value("2026-10-09T12:00:00Z"));
    }

    @Test
    void cannotCompleteWithALegacySkillWithoutALevel() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        mockMvc(repository, identifier -> Optional.of(SECTOR_ID))
                .perform(put("/api/v1/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intentions": ["DISCOVER"], "preferredStages": [],
                                 "preferredSkills": ["cccccccc-cccc-cccc-cccc-cccccccccccc"], "completeOnboarding": true}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(repository.saved).isNull();
    }

    @Test
    void requiresAnIntentionToCompleteOnboarding() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        mockMvc(repository, identifier -> Optional.of(SECTOR_ID))
                .perform(put("/api/v1/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intentions": [], "preferredStages": [], "skills": [], "completeOnboarding": true}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(repository.saved).isNull();
    }

    @Test
    void treatsPreferredSectorsAsAnOptionalSelection() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        MockMvc mockMvc = mockMvc(repository, identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["discover"],
                          "preferredStages": ["mvp"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredSectors").isEmpty());

        assertThat(repository.saved.preferredSectors()).isEmpty();
    }

    @Test
    void rejectsUnknownSectorsWithAnExplicitProblem() throws Exception {
        MockMvc mockMvc = mockMvc(new CapturingRepository(), identifier -> Optional.empty());

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["discover"],
                          "preferredSectors": ["desconocido"],
                          "preferredStages": ["MVP"]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid preferences"))
                .andExpect(jsonPath("$.detail")
                        .value("preferredSectors contains unknown or inactive sector 'desconocido'."));
    }

    @Test
    void rejectsDuplicateValuesWithAnExplicitProblem() throws Exception {
        MockMvc mockMvc = mockMvc(new CapturingRepository(), identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["discover", "DISCOVER"],
                          "preferredSectors": [],
                          "preferredStages": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid preferences"))
                .andExpect(jsonPath("$.detail")
                        .value("intentions must not contain duplicates: 'DISCOVER'."));
    }

    @Test
    void rejectsAClientProvidedProfileId() throws Exception {
        MockMvc mockMvc = mockMvc(new CapturingRepository(), identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "profileId": "22222222-2222-2222-2222-222222222222",
                          "intentions": [],
                          "preferredSectors": [],
                          "preferredStages": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsAJsonNullBodyWithAnExplicitProblem() throws Exception {
        MockMvc mockMvc = mockMvc(new CapturingRepository(), identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid preferences"))
                .andExpect(jsonPath("$.detail").value("preferences are required."));
    }

    @Test
    void returnsSanitizedServiceUnavailableWhenTheRepositoryFails() throws Exception {
        OnboardingPreferencesRepository unavailableRepository = new OnboardingPreferencesRepository() {
            @Override
            public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
                throw new DataAccessResourceFailureException("password=secret host=database");
            }

            @Override
            public OnboardingPreferences upsert(OnboardingPreferences preferences, boolean completeOnboarding) {
                throw new DataAccessResourceFailureException("password=secret host=database");
            }

            @Override
            public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
                throw new UnsupportedOperationException();
            }
        };
        MockMvc mockMvc = mockMvc(unavailableRepository, identifier -> Optional.of(SECTOR_ID));

        mockMvc.perform(get("/api/v1/me/preferences"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));

        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "intentions": ["discover"],
                          "preferredSectors": [],
                          "preferredStages": ["mvp"]
                        }
                        """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Data store unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("The data store is temporarily unavailable."));
    }

    private static MockMvc mockMvc(
            OnboardingPreferencesRepository repository,
            ActiveSectorLookup activeSectorLookup) {
        ActivePreferenceCatalogLookup activePreferenceCatalogLookup = new ActivePreferenceCatalogLookup() {
            @Override
            public Set<UUID> findActiveTagIds(List<UUID> ids) {
                return Set.copyOf(ids);
            }

            @Override
            public Set<UUID> findActiveSkillIds(List<UUID> ids) {
                return Set.copyOf(ids);
            }
        };
        return MockMvcBuilders
                .standaloneSetup(new OnboardingPreferencesController(
                        new GetOnboardingPreferences(repository, PROPERTIES),
                        new SaveOnboardingPreferences(
                                repository,
                                activeSectorLookup,
                                activePreferenceCatalogLookup,
                                PROPERTIES)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static OnboardingPreferences preferences(
            List<String> intentions,
            List<String> sectors,
            List<String> stages) {
        return new OnboardingPreferences(
                PROFILE_ID,
                intentions,
                sectors,
                stages,
                List.of(TAG_ID.toString()),
                List.of(SKILL_ID.toString()),
                Map.of(),
                LIMITS);
    }

    private static Jwt authenticatedJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return authenticatedJwt();
        }
    }

    private static class FixedRepository implements OnboardingPreferencesRepository {

        private final OnboardingPreferences saved;

        private FixedRepository(OnboardingPreferences saved) {
            this.saved = saved;
        }

        @Override
        public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
            return Optional.of(saved);
        }

        @Override
        public OnboardingPreferences upsert(OnboardingPreferences preferences, boolean completeOnboarding) {
            return preferences;
        }

        @Override
        public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
        }
    }

    private static final class CapturingRepository implements OnboardingPreferencesRepository {

        private OnboardingPreferences saved;
        private boolean completeOnboarding;

        @Override
        public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
            return Optional.empty();
        }

        @Override
        public OnboardingPreferences upsert(OnboardingPreferences preferences, boolean completeOnboarding) {
            saved = preferences;
            this.completeOnboarding = completeOnboarding;
            return preferences;
        }

        @Override
        public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
        }
    }
}
