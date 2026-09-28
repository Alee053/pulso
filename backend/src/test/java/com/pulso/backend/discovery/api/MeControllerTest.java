package com.pulso.backend.discovery.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.config.ProfileProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.profile.application.GetCurrentProfile;
import com.pulso.backend.profile.application.ProfileRepository;
import com.pulso.backend.profile.application.UpdateCurrentProfile;
import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@WebMvcTest(MeController.class)
@Import({ SecurityConfig.class, ApiExceptionHandler.class, MeControllerTest.TestBeans.class })
class MeControllerTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesTheAuthenticatedProductProfile() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("email", "developer@example.test")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.email").value("developer@example.test"))
                .andExpect(jsonPath("$.role").value("authenticated"))
                .andExpect(jsonPath("$.profile.id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.profile.displayName").value("Perfil Uno"))
                .andExpect(jsonPath("$.profile.profileVisibility").value("PUBLIC"))
                .andExpect(jsonPath("$.profile.platformRole").value("USER"));
    }

    @Test
    void updatesTheAuthenticatedProductProfile() throws Exception {
        Jwt jwt = authenticatedJwt();

        mockMvc.perform(patch("/api/v1/me")
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "displayName": "Perfil Uno actualizado",
                          "shortBio": "Una biografía válida."
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("Perfil Uno actualizado"))
                .andExpect(jsonPath("$.profile.shortBio").value("Una biografía válida."));
    }

    @Test
    void rejectsInvalidProfileValues() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                .with(jwt().jwt(authenticatedJwt()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "profileVisibility": "HIDDEN"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid profile"));
    }

    @Test
    void rejectsServerManagedProfileFields() throws Exception {
        mockMvc.perform(patch("/api/v1/me")
                .with(jwt().jwt(authenticatedJwt()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "platformRole": "ADMIN"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsServiceRoleTokens() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token("service_role")))
                .andExpect(status().isUnauthorized());
    }

    private static Jwt authenticatedJwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("email", "developer@example.test")
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();
    }

    private static String token(String role) throws Exception {
        java.util.Date issuedAt = new java.util.Date();
        java.util.Date expiresAt = new java.util.Date(issuedAt.getTime() + 300_000);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(PROFILE_ID.toString())
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
        ProfileRepository profileRepository() {
            return new ProfileRepository() {
                @Override
                public Optional<Profile> findById(UUID profileId) {
                    return profileId.equals(PROFILE_ID)
                            ? Optional.of(profile())
                            : Optional.empty();
                }

                @Override
                public Optional<Profile> update(UUID profileId, ProfilePatch patch) {
                    if (!PROFILE_ID.equals(profileId)) {
                        return Optional.empty();
                    }
                    Profile current = profile();
                    return Optional.of(new Profile(
                            current.id(),
                            patch.profileKind() != null ? patch.profileKind() : current.profileKind(),
                            patch.displayName() != null ? patch.displayName() : current.displayName(),
                            patch.avatarStoragePath() != null
                                    ? patch.avatarStoragePath()
                                    : current.avatarStoragePath(),
                            patch.shortBio() != null ? patch.shortBio() : current.shortBio(),
                            patch.city() != null ? patch.city() : current.city(),
                            patch.department() != null ? patch.department() : current.department(),
                            patch.profileVisibility() != null
                                    ? patch.profileVisibility()
                                    : current.profileVisibility(),
                            current.verificationStatus(),
                            current.accountStatus(),
                            current.platformRole(),
                            patch.onboardingCompletedAt() != null
                                    ? patch.onboardingCompletedAt()
                                    : current.onboardingCompletedAt(),
                            current.createdAt(),
                            current.updatedAt()));
                }
            };
        }

        @Bean
        GetCurrentProfile getCurrentProfile(ProfileRepository repository) {
            return new GetCurrentProfile(
                    repository,
                    new ProfileProperties(120, 500, 500, 120, 120));
        }

        @Bean
        UpdateCurrentProfile updateCurrentProfile(ProfileRepository repository) {
            return new UpdateCurrentProfile(
                    repository,
                    new ProfileProperties(120, 500, 500, 120, 120));
        }

        private static Profile profile() {
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-08T12:00:00Z");
            return new Profile(
                    PROFILE_ID,
                    "PERSON",
                    "Perfil Uno",
                    null,
                    "Biografía inicial.",
                    "Cochabamba",
                    "Cochabamba",
                    "PUBLIC",
                    "UNVERIFIED",
                    "ACTIVE",
                    "USER",
                    null,
                    timestamp,
                    timestamp);
        }
    }
}
