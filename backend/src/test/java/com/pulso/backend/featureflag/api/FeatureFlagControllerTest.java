package com.pulso.backend.featureflag.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.featureflag.application.FeatureFlagRepository;
import com.pulso.backend.featureflag.application.GetFeatureFlags;
import com.pulso.backend.featureflag.application.SetFeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;
import com.pulso.backend.profile.application.ProfileRepository;
import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@WebMvcTest(FeatureFlagController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, FeatureFlagControllerTest.TestBeans.class })
class FeatureFlagControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERATOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeFeatureFlagRepository flags;

    @BeforeEach
    void resetFlags() {
        flags.reset();
    }

    @Test
    void exposesOnlyTheKnownBooleanFlagsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/feature-flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.length()").value(2))
                .andExpect(jsonPath("$.flags[0].key").value("language_switcher"))
                .andExpect(jsonPath("$.flags[0].enabled").value(true))
                .andExpect(jsonPath("$.flags[0].secret").doesNotExist())
                .andExpect(jsonPath("$.flags[1].key").value("project_creation"))
                .andExpect(jsonPath("$.flags[1].enabled").value(true));
    }

    @Test
    void rejectsUnauthenticatedUpdates() throws Exception {
        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRegularUsersEvenWhenTheJwtClaimsAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(USER_ID, "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("feature_flag_admin_required"));
        org.assertj.core.api.Assertions.assertThat(flags.isEnabled(FeatureFlagKey.PROJECT_CREATION)).isTrue();
    }

    @Test
    void rejectsModeratorsBecauseTheyCannotChangeGlobalFlags() throws Exception {
        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(MODERATOR_ID, "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsPersistedPlatformAdminsToUpdateAFlag() throws Exception {
        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(ADMIN_ID, "authenticated")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("project_creation"))
                .andExpect(jsonPath("$.enabled").value(false));

        org.assertj.core.api.Assertions.assertThat(flags.isEnabled(FeatureFlagKey.PROJECT_CREATION)).isFalse();
    }

    @Test
    void reportsPersistenceFailuresWithoutReturningDatabaseDetails() throws Exception {
        flags.failUpdates = true;

        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(ADMIN_ID, "authenticated")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The data store is temporarily unavailable."));
    }

    @Test
    void rejectsUnknownKeysAndInvalidBooleanValues() throws Exception {
        mockMvc.perform(put("/api/v1/feature-flags/unknown")
                        .with(jwt().jwt(authenticatedJwt(ADMIN_ID, "authenticated")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("feature_flag_not_found"));

        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(ADMIN_ID, "authenticated")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_feature_flag"));

        mockMvc.perform(put("/api/v1/feature-flags/project_creation")
                        .with(jwt().jwt(authenticatedJwt(ADMIN_ID, "authenticated")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":\"false\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_feature_flag"));
    }

    private static Jwt authenticatedJwt(UUID profileId, String claimedRole) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(profileId.toString())
                .claim("role", claimedRole)
                .audience(List.of("authenticated"))
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        FakeFeatureFlagRepository featureFlagRepository() {
            return new FakeFeatureFlagRepository();
        }

        @Bean
        GetFeatureFlags getFeatureFlags(FakeFeatureFlagRepository repository) {
            return new GetFeatureFlags(repository);
        }

        @Bean
        SetFeatureFlag setFeatureFlag(
                FakeFeatureFlagRepository repository,
                ProfileRepository profiles) {
            return new SetFeatureFlag(repository, profiles);
        }

        @Bean
        ProfileRepository profileRepository() {
            return new ProfileRepository() {
                @Override
                public Optional<Profile> findById(UUID profileId) {
                    String role = switch (profileId.toString()) {
                        case "33333333-3333-3333-3333-333333333333" -> "ADMIN";
                        case "22222222-2222-2222-2222-222222222222" -> "MODERATOR";
                        default -> "USER";
                    };
                    return Optional.of(new Profile(
                            profileId, "PERSON", "Tester", null, null, null, null,
                            "PUBLIC", "UNVERIFIED", "ACTIVE", role, null, null, null));
                }

                @Override
                public Optional<Profile> update(UUID profileId, ProfilePatch patch) {
                    return Optional.empty();
                }
            };
        }
    }

    static class FakeFeatureFlagRepository implements FeatureFlagRepository {

        private final Map<FeatureFlagKey, Boolean> values = new EnumMap<>(FeatureFlagKey.class);
        private boolean failUpdates;

        void reset() {
            failUpdates = false;
            values.clear();
            for (FeatureFlagKey key : FeatureFlagKey.values()) {
                values.put(key, key.defaultEnabled());
            }
        }

        @Override
        public List<FeatureFlag> findAll() {
            return values.entrySet().stream()
                    .map(entry -> new FeatureFlag(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public Optional<FeatureFlag> findByKey(FeatureFlagKey key) {
            return Optional.ofNullable(values.get(key)).map(enabled -> new FeatureFlag(key, enabled));
        }

        @Override
        public Optional<FeatureFlag> update(FeatureFlagKey key, boolean enabled) {
            if (failUpdates) {
                throw new DataAccessResourceFailureException("private database detail");
            }
            return Optional.ofNullable(values.computeIfPresent(key, (ignored, current) -> enabled))
                    .map(value -> new FeatureFlag(key, value));
        }

        @Override
        public boolean isEnabled(FeatureFlagKey key) {
            return values.getOrDefault(key, key.defaultEnabled());
        }
    }
}
