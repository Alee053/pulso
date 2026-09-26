package com.pulso.backend.preference.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.application.SaveOnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferences;

class OnboardingPreferencesControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
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
                .andExpect(jsonPath("$.preferredStages[0]").value("MVP"));
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
                          "preferredStages": ["mvp"]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.intentions[0]").value("DISCOVER"))
                .andExpect(jsonPath("$.preferredSectors[0]").value("sostenibilidad"))
                .andExpect(jsonPath("$.preferredStages[0]").value("MVP"));

        assertThat(repository.saved.profileId())
                .isEqualTo(PROFILE_ID);
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
            public OnboardingPreferences upsert(OnboardingPreferences preferences) {
                throw new DataAccessResourceFailureException("password=secret host=database");
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
        return MockMvcBuilders
                .standaloneSetup(new OnboardingPreferencesController(
                        new GetOnboardingPreferences(repository, PROPERTIES),
                        new SaveOnboardingPreferences(repository, activeSectorLookup, PROPERTIES)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
    }

    private static OnboardingPreferences preferences(
            List<String> intentions,
            List<String> sectors,
            List<String> stages) {
        return new OnboardingPreferences(PROFILE_ID, intentions, sectors, stages, LIMITS);
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
        public OnboardingPreferences upsert(OnboardingPreferences preferences) {
            return preferences;
        }
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
