package com.pulso.backend.project.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.project.application.ProjectUpdateRepository;
import com.pulso.backend.project.application.UpdateProjectInformation;
import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

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

    @Test
    void requiresAnAccessTokenToUpdateAProject() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAnAuthenticatedUserTokenAndPassesItsSubjectToTheUseCase() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAServiceRoleToken() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/{projectId}", PROJECT_ID)
                .header("Authorization", "Bearer " + token("service_role"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    private static String validRequest() {
        return """
                {
                  "name": "EcoPack actualizado"
                }
                """;
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
        ProjectUpdateRepository projectUpdateRepository() {
            return new ProjectUpdateRepository() {
                @Override
                public boolean hasActiveSector(UUID sectorId) {
                    return true;
                }

                @Override
                public Optional<UpdatedProject> updateInformation(
                        UUID projectId,
                        UUID authenticatedProfileId,
                        ProjectUpdatePatch patch) {
                    return Optional.of(project());
                }
            };
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
}
