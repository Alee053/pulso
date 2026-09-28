package com.pulso.backend.project.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.ChangeProjectLifecycle;
import com.pulso.backend.project.application.ProjectLifecycleRepository;
import com.pulso.backend.project.domain.ProjectLifecycleActor;
import com.pulso.backend.project.domain.ProjectLifecycleSnapshot;
import com.pulso.backend.project.domain.ProjectLifecycleState;

@WebMvcTest(ProjectLifecycleController.class)
@Import({ SecurityConfig.class, ProjectLifecycleSecurityTest.TestBeans.class })
class ProjectLifecycleSecurityTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID EXTERNAL_PROFILE_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final Instant ARCHIVED_AT = Instant.parse("2026-09-20T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessTokenForLifecycleActions() throws Exception {
        mockMvc.perform(post(path("publication-request")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAnAuthenticatedUserTokenForAnAuthorizedOwner() throws Exception {
        mockMvc.perform(post(path("publication-request"))
                .with(jwt().jwt(authenticatedJwt(PROFILE_ID, null))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAServiceRoleToken() throws Exception {
        mockMvc.perform(post(path("publication-request"))
                .header("Authorization", "Bearer " + token("service_role", PROFILE_ID)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usesTheJwtSubjectAndNotAClientOrJwtRoleClaimForAuthorization() throws Exception {
        mockMvc.perform(post(path("publication-request"))
                .with(jwt().jwt(authenticatedJwt(EXTERNAL_PROFILE_ID, "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\",\"profileId\":\""
                        + PROFILE_ID + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(path("archive"))
                .with(jwt().jwt(authenticatedJwt(EXTERNAL_PROFILE_ID, "OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotAuthorizeAnUnmappedDirectStatusTransition() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + PROJECT_ID + "/status")
                .with(jwt().jwt(authenticatedJwt(PROFILE_ID, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    private static String path(String action) {
        return "/api/v1/projects/" + PROJECT_ID + "/" + action;
    }

    private static Jwt authenticatedJwt(UUID profileId, String projectRole) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(profileId.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"));
        if (projectRole != null) {
            builder.claim("projectRole", projectRole);
        }
        return builder.build();
    }

    private static String token(String role, UUID profileId) throws Exception {
        return JwtTestTokens.signed(role, profileId.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        ProjectLifecycleRepository projectLifecycleRepository() {
            return new ProjectLifecycleRepository() {
                @Override
                public Optional<ProjectLifecycleSnapshot> findForActor(
                        UUID projectId,
                        UUID profileId) {
                    if (!PROJECT_ID.equals(projectId)) {
                        return Optional.empty();
                    }
                    if (PROFILE_ID.equals(profileId)) {
                        return Optional.of(snapshot("OWNER", "ACTIVE", "DRAFT", profileId));
                    }
                    if (EXTERNAL_PROFILE_ID.equals(profileId)) {
                        return Optional.of(snapshot("MEMBER", "ACTIVE", "DRAFT", profileId));
                    }
                    return Optional.empty();
                }

                @Override
                public int updateLifecycle(
                        UUID projectId,
                        UUID profileId,
                        String expectedStatus,
                        ProjectLifecycleState nextState) {
                    return 1;
                }
            };
        }

        @Bean
        ChangeProjectLifecycle changeProjectLifecycle(ProjectLifecycleRepository repository) {
            return new ChangeProjectLifecycle(
                    repository,
                    Clock.fixed(ARCHIVED_AT, ZoneOffset.UTC));
        }
    }

    private static ProjectLifecycleSnapshot snapshot(
            String role,
            String membershipStatus,
            String status,
            UUID profileId) {
        return new ProjectLifecycleSnapshot(
                PROJECT_ID,
                profileId,
                new ProjectLifecycleState(status, null, null),
                new ProjectLifecycleActor(role, membershipStatus));
    }

    private static final class JwtTestTokens {

        private JwtTestTokens() {
        }

        private static String signed(String role, String subject) throws Exception {
            java.util.Date issuedAt = new java.util.Date();
            java.util.Date expiresAt = new java.util.Date(issuedAt.getTime() + 300_000);
            com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                    .issuer("http://test.supabase.local/auth/v1")
                    .subject(subject)
                    .audience("authenticated")
                    .claim("role", role)
                    .issueTime(issuedAt)
                    .expirationTime(expiresAt)
                    .build();
            com.nimbusds.jwt.SignedJWT signedJwt = new com.nimbusds.jwt.SignedJWT(
                    new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                    claims);
            signedJwt.sign(new com.nimbusds.jose.crypto.MACSigner(
                    new javax.crypto.spec.SecretKeySpec(
                            "test-secret-that-is-long-enough-for-local-tests"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "HmacSHA256")));
            return signedJwt.serialize();
        }
    }
}
