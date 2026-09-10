package com.pulso.backend.preference.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.pulso.backend.catalog.application.ActiveSectorLookup;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.discovery.api.ApiExceptionHandler;
import com.pulso.backend.preference.application.GetOnboardingPreferences;
import com.pulso.backend.preference.application.OnboardingPreferencesRepository;
import com.pulso.backend.preference.application.SaveOnboardingPreferences;
import com.pulso.backend.preference.domain.OnboardingPreferences;

@WebMvcTest(OnboardingPreferencesController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, OnboardingPreferencesSecurityTest.TestBeans.class })
class OnboardingPreferencesSecurityTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessTokenToReadPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAnAccessTokenToSavePreferences() throws Exception {
        mockMvc.perform(put("/api/v1/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUserToReadPreferences() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get("/api/v1/me/preferences").with(jwt().jwt(jwt)))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ActiveSectorLookup activeSectorLookup() {
            return identifier -> Optional.of(UUID.fromString(
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        }

        @Bean
        OnboardingPreferencesRepository onboardingPreferencesRepository() {
            return new OnboardingPreferencesRepository() {
                @Override
                public Optional<OnboardingPreferences> findByProfileId(UUID profileId) {
                    return Optional.empty();
                }

                @Override
                public OnboardingPreferences upsert(OnboardingPreferences preferences) {
                    return preferences;
                }
            };
        }

        @Bean
        GetOnboardingPreferences getOnboardingPreferences(
                OnboardingPreferencesRepository repository) {
            return new GetOnboardingPreferences(repository);
        }

        @Bean
        SaveOnboardingPreferences saveOnboardingPreferences(
                OnboardingPreferencesRepository repository,
                ActiveSectorLookup activeSectorLookup) {
            return new SaveOnboardingPreferences(repository, activeSectorLookup);
        }
    }
}
