package com.pulso.backend.interaction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pulso.backend.config.DiscoveryProperties;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.interaction.application.ListDiscoveryFeed;
import com.pulso.backend.interaction.application.ListProjectCollection;
import com.pulso.backend.interaction.application.ProjectInteractionRepository;
import com.pulso.backend.interaction.application.SetProjectFeedback;
import com.pulso.backend.interaction.application.SetSavedProject;
import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.OnboardingPreferences;

class ProjectInteractionControllerTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Test
    void readsAndMutatesTheAuthenticatedProfileCollections() throws Exception {
        CapturingRepository repository = new CapturingRepository();
        CapturingPreferencesRepository preferencesRepository = new CapturingPreferencesRepository();
        MockMvc mockMvc = mockMvc(repository, preferencesRepository);

        mockMvc.perform(get("/api/v1/me/discovery-feed").param("offset", "0").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.data[0].saved").value(false))
                .andExpect(jsonPath("$.meta.totalCount").value(1));
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);

        mockMvc.perform(put("/api/v1/me/saved-projects/{projectId}", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        assertThat(repository.lastSaved).isTrue();

        mockMvc.perform(delete("/api/v1/me/saved-projects/{projectId}", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false));
        assertThat(repository.lastSaved).isFalse();

        mockMvc.perform(put("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "feedbackType": "INTERESTED",
                          "recommendationWeights": {"impact": 2.5}
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackType").value("INTERESTED"));
        assertThat(repository.lastFeedbackType).isEqualTo(ProjectFeedbackType.INTERESTED);
        assertThat(preferencesRepository.savedWeights).containsEntry("impact", 2.5);
    }

    private static MockMvc mockMvc(
            CapturingRepository repository,
            CapturingPreferencesRepository preferencesRepository) {
        return MockMvcBuilders.standaloneSetup(new ProjectInteractionController(
                new ListDiscoveryFeed(repository),
                new ListProjectCollection(repository),
                new SetSavedProject(repository),
                new SetProjectFeedback(repository, preferencesRepository),
                new DiscoveryProperties(0, 20, 50, 100_000)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new JwtArgumentResolver())
                .build();
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

    private static final class CapturingRepository implements ProjectInteractionRepository {

        private final ProjectSummary summary = new ProjectSummary(
                PROJECT_ID,
                "eco-pack",
                "EcoPack Bolivia",
                "Envases compostables.",
                "Sostenibilidad",
                "Cochabamba",
                "MVP");
        private UUID lastProfileId;
        private boolean lastSaved;
        private ProjectFeedbackType lastFeedbackType;

        @Override
        public List<DiscoveryFeedItem> findDiscoveryFeed(UUID profileId, int offset, int limit) {
            lastProfileId = profileId;
            return List.of(new DiscoveryFeedItem(summary, false, null));
        }

        @Override
        public long countDiscoveryFeed(UUID profileId) {
            lastProfileId = profileId;
            return 1;
        }

        @Override
        public List<ProjectSummary> findSavedProjects(UUID profileId, int offset, int limit) {
            return List.of(summary);
        }

        @Override
        public long countSavedProjects(UUID profileId) {
            return 1;
        }

        @Override
        public List<ProjectSummary> findInterestedProjects(UUID profileId, int offset, int limit) {
            return List.of(summary);
        }

        @Override
        public long countInterestedProjects(UUID profileId) {
            return 1;
        }

        @Override
        public void setSaved(UUID profileId, UUID projectId, boolean saved) {
            lastProfileId = profileId;
            lastSaved = saved;
        }

        @Override
        public void setFeedback(UUID profileId, UUID projectId, ProjectFeedbackType feedbackType) {
            lastProfileId = profileId;
            lastFeedbackType = feedbackType;
        }
    }

    private static final class CapturingPreferencesRepository implements OnboardingPreferencesRepository {

        private Map<String, Double> savedWeights = Map.of();

        @Override
        public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
            return Optional.empty();
        }

        @Override
        public OnboardingPreferences upsert(OnboardingPreferences preferences) {
            return preferences;
        }

        @Override
        public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
            savedWeights = weights;
        }
    }
}
