package com.pulso.backend.interaction.api;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.interaction.application.GetProjectFeedback;
import com.pulso.backend.interaction.application.ListDiscoveryFeed;
import com.pulso.backend.interaction.application.ListProjectCollection;
import com.pulso.backend.interaction.application.ProjectInteractionRepository;
import com.pulso.backend.interaction.application.SetProjectFeedback;
import com.pulso.backend.interaction.application.SetSavedProject;
import com.pulso.backend.interaction.domain.DiscoveryFeedCursor;
import com.pulso.backend.interaction.domain.DiscoveryFeedItem;
import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.domain.OnboardingPreferences;
import com.pulso.backend.project.domain.ProjectShareOrigin;

@WebMvcTest(ProjectInteractionController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, ProjectInteractionSecurityTest.TestBeans.class })
class ProjectInteractionSecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID PROJECT_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestBeans.CapturingRepository repository;

    @Test
    void requiresAuthenticationForSavedProjectReadsAndMutations() throws Exception {
        mockMvc.perform(get("/api/v1/me/saved-projects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/me/saved-projects/{projectId}", PROJECT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me/saved-projects/{projectId}", PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsServiceRoleTokensForSavedProjectMutations() throws Exception {
        mockMvc.perform(put("/api/v1/me/saved-projects/{projectId}", PROJECT_ID)
                        .header("Authorization", "Bearer " + token(PROFILE_ID, "service_role")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopesSavedProjectReadsAndMutationsToTheValidatedJwtSubject() throws Exception {
        String accessToken = token(PROFILE_ID, "authenticated");

        mockMvc.perform(get("/api/v1/me/saved-projects")
                        .param("profileId", OTHER_PROFILE_ID.toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);

        mockMvc.perform(put("/api/v1/me/saved-projects/{projectId}", PROJECT_ID)
                        .param("profileId", OTHER_PROFILE_ID.toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);

        mockMvc.perform(delete("/api/v1/me/saved-projects/{projectId}", PROJECT_ID)
                        .param("profileId", OTHER_PROFILE_ID.toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);
    }

    @Test
    void feedbackOperationsRequireAuthenticationAndUseOnlyTheJwtSubject() throws Exception {
        mockMvc.perform(get("/api/v1/me/project-feedback/{projectId}", PROJECT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"feedbackType\":\"INTERESTED\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me/project-feedback/{projectId}", PROJECT_ID))
                .andExpect(status().isUnauthorized());

        String accessToken = token(PROFILE_ID, "authenticated");
        mockMvc.perform(put("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("profileId", OTHER_PROFILE_ID.toString())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"feedbackType\":\"DISMISSED\"}"))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);
        mockMvc.perform(get("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("profileId", OTHER_PROFILE_ID.toString()))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);
        mockMvc.perform(delete("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("profileId", OTHER_PROFILE_ID.toString()))
                .andExpect(status().isOk());
        assertThat(repository.lastProfileId).isEqualTo(PROFILE_ID);
        mockMvc.perform(delete("/api/v1/me/project-feedback/{projectId}", PROJECT_ID)
                        .header("Authorization", "Bearer " + token(PROFILE_ID, "service_role")))
                .andExpect(status().isUnauthorized());
    }

    private static String token(UUID subject, String role) throws Exception {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + 300_000);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject.toString())
                .audience("authenticated")
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        return signedJwt.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CapturingRepository projectInteractionRepository() {
            return new CapturingRepository();
        }

        @Bean
        OnboardingPreferencesRepository onboardingPreferencesRepository() {
            return new OnboardingPreferencesRepository() {
                @Override
                public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
                    return Optional.empty();
                }

                @Override
                public OnboardingPreferences upsert(
                        OnboardingPreferences preferences,
                        boolean completeOnboarding) {
                    return preferences;
                }

                @Override
                public void saveRecommendationWeights(UUID profileId, Map<String, Double> weights) {
                }
            };
        }

        @Bean
        ListDiscoveryFeed listDiscoveryFeed(
                ProjectInteractionRepository repository,
                OnboardingPreferencesRepository preferencesRepository) {
            return new ListDiscoveryFeed(repository, preferencesRepository);
        }

        @Bean
        ListProjectCollection listProjectCollection(ProjectInteractionRepository repository) {
            return new ListProjectCollection(repository);
        }

        @Bean
        SetSavedProject setSavedProject(ProjectInteractionRepository repository) {
            return new SetSavedProject(repository);
        }

        @Bean
        GetProjectFeedback getProjectFeedback(ProjectInteractionRepository repository) {
            return new GetProjectFeedback(repository);
        }

        @Bean
        SetProjectFeedback setProjectFeedback(
                ProjectInteractionRepository repository,
                OnboardingPreferencesRepository preferencesRepository) {
            return new SetProjectFeedback(repository, preferencesRepository);
        }

        static final class CapturingRepository implements ProjectInteractionRepository {

            private UUID lastProfileId;

            @Override
            public List<DiscoveryFeedItem> findDiscoveryFeed(
                    UUID profileId,
                    List<String> preferredSectors,
                    List<String> preferredStages,
                    int offset,
                    DiscoveryFeedCursor cursor,
                    int limit) {
                lastProfileId = profileId;
                return List.of();
            }

            @Override
            public long countDiscoveryFeed(UUID profileId) {
                lastProfileId = profileId;
                return 0;
            }

            @Override
            public List<ProjectSummary> findSavedProjects(UUID profileId, int offset, int limit) {
                lastProfileId = profileId;
                return List.of();
            }

            @Override
            public long countSavedProjects(UUID profileId) {
                lastProfileId = profileId;
                return 0;
            }

            @Override
            public List<ProjectSummary> findInterestedProjects(UUID profileId, int offset, int limit) {
                lastProfileId = profileId;
                return List.of();
            }

            @Override
            public long countInterestedProjects(UUID profileId) {
                lastProfileId = profileId;
                return 0;
            }

            @Override
            public void setSaved(UUID profileId, UUID projectId, boolean saved) {
                lastProfileId = profileId;
            }

            @Override
            public ProjectFeedbackType findFeedback(UUID profileId, UUID projectId) {
                lastProfileId = profileId;
                return null;
            }

            @Override
            public void clearFeedback(UUID profileId, UUID projectId) {
                lastProfileId = profileId;
            }

            @Override
            public void setFeedback(UUID profileId, UUID projectId, ProjectFeedbackType feedbackType) {
                lastProfileId = profileId;
            }

            @Override
            public void insertSharedEvent(UUID profileId, UUID projectId, ProjectShareOrigin origin) {
                // No-op para tests
            }
        }
    }
}