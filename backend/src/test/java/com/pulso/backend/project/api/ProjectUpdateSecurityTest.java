package com.pulso.backend.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.ProjectUpdateRepository;
import com.pulso.backend.project.application.UpdateProjectInformation;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

/** Tests the backend JWT boundary with signed fixtures in local HMAC mode, without live Supabase Auth. */
@WebMvcTest(ProjectUpdateController.class)
@Import({ SecurityConfig.class, ProjectUpdateSecurityTest.TestBeans.class })
class ProjectUpdateSecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingRepository repository;

    @BeforeEach
    void resetRepository() {
        repository.projectId = null;
        repository.authenticatedProfileId = null;
    }

    @Test
    void requiresAnAccessTokenToUpdateAProject() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());

        assertThat(repository.projectId).isNull();
    }

    @Test
    void acceptsAnAuthenticatedUserTokenAndPassesItsSubjectToTheUseCase() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .header("Authorization", "Bearer " + token("authenticated"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isOk());

        assertThat(repository.projectId).isEqualTo(PROJECT_ID);
        assertThat(repository.authenticatedProfileId).isEqualTo(PROFILE_ID);
    }

    @Test
    void rejectsAServiceRoleToken() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .header("Authorization", "Bearer " + token("service_role"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());

        assertThat(repository.projectId).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "signature", "issuer", "audience", "expired", "malformed" })
    void rejectsInvalidBearerTokensBeforePersistence(String invalidClaim) throws Exception {
        String bearerToken = "malformed".equals(invalidClaim)
                ? "not-a-jwt"
                : token(
                        "authenticated",
                        PROFILE_ID.toString(),
                        "issuer".equals(invalidClaim) ? "http://untrusted.local/auth/v1" : ISSUER,
                        "audience".equals(invalidClaim) ? "other-audience" : "authenticated",
                        "expired".equals(invalidClaim) ? -300_000 : 300_000,
                        "signature".equals(invalidClaim) ? "incorrect-signing-secret-for-local-tests" : SECRET);

        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());

        assertThat(repository.projectId).isNull();
        assertThat(repository.authenticatedProfileId).isNull();
    }

    @Test
    void rejectsASignedTokenWithAMalformedProfileSubjectBeforePersistence() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .header("Authorization", "Bearer " + token(
                        "authenticated", "not-a-uuid", ISSUER, "authenticated", 300_000, SECRET))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());

        assertThat(repository.projectId).isNull();
    }

    private static String validRequest() {
        return """
                {
                  "name": "EcoPack actualizado"
                }
                """;
    }

    private static String token(String role) throws Exception {
        return token(role, PROFILE_ID.toString(), ISSUER, "authenticated", 300_000, SECRET);
    }

    private static String token(
            String role, String subject, String issuer, String audience,
            long expiresInMillis, String signingSecret) throws Exception {
        Date issuedAt = new Date(System.currentTimeMillis() - 600_000);
        Date expiresAt = new Date(System.currentTimeMillis() + expiresInMillis);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                signingSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        return signedJwt.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CapturingRepository projectUpdateRepository() {
            return new CapturingRepository();
        }

        @Bean
        UpdateProjectInformation updateProjectInformation(ProjectUpdateRepository repository) {
            return new UpdateProjectInformation(
                    repository,
                    new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254));
        }

        private static UpdatedProject project() {
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-08T12:00:00Z");
            return new UpdatedProject(
                    PROJECT_ID,
                    "eco-pack",
                    "EcoPack actualizado",
                    "Envases",
                    null,
                    null,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    "MVP",
                    "Cochabamba",
                    null,
                    "DRAFT",
                    "UNVERIFIED",
                    PROFILE_ID,
                    null,
                    null,
                    null,
                    null,
                    timestamp,
                    timestamp,
                    null);
        }
    }

    private static final class CapturingRepository implements ProjectUpdateRepository {

        private UUID projectId;
        private UUID authenticatedProfileId;

        @Override
        public boolean hasActiveSector(UUID sectorId) {
            return true;
        }

        @Override
        public Optional<UpdatedProject> updateInformation(
                UUID projectId, UUID authenticatedProfileId, ProjectUpdatePatch patch) {
            this.projectId = projectId;
            this.authenticatedProfileId = authenticatedProfileId;
            return Optional.of(TestBeans.project());
        }
    }
}
