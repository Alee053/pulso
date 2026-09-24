package com.pulso.backend.project.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
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
import com.pulso.backend.project.application.CreateDraftProject;
import com.pulso.backend.project.application.ProjectRepository;
import com.pulso.backend.project.domain.Project;
import com.pulso.backend.project.domain.ProjectDraft;
import com.pulso.backend.project.domain.ProjectMember;

@WebMvcTest(ProjectController.class)
@Import({ SecurityConfig.class, ProjectSecurityTest.TestBeans.class })
class ProjectSecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAnAccessTokenToCreateProjects() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAnAuthenticatedUserToCreateProjects() throws Exception {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(PROFILE_ID.toString())
                .claim("role", "authenticated")
                .audience(List.of("authenticated"))
                .build();

        mockMvc.perform(post("/api/v1/projects")
                .with(jwt().jwt(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsAServiceRoleToken() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + token("service_role"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    private static String validRequest() {
        return """
                {
                  "slug": "eco-pack",
                  "name": "EcoPack Bolivia",
                  "tagline": "Envases sostenibles",
                  "description": "Proyecto de prueba.",
                  "sectorId": "%s",
                  "city": "Cochabamba",
                  "stage": "MVP"
                }
                """.formatted(SECTOR_ID);
    }

    private static String token(String role) throws Exception {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + 300_000);
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
        ProjectRepository projectRepository() {
            return new ProjectRepository() {
                @Override
                public boolean hasActiveSector(UUID sectorId) {
                    return SECTOR_ID.equals(sectorId);
                }

                @Override
                public Project createDraft(ProjectDraft draft, UUID createdByProfileId) {
                    return new Project(
                            UUID.fromString("33333333-3333-3333-3333-333333333333"),
                            draft.slug(),
                            draft.name(),
                            draft.tagline(),
                            draft.description(),
                            draft.sectorId(),
                            draft.city(),
                            draft.stage(),
                            "DRAFT",
                            createdByProfileId);
                }

                @Override
                public ProjectMember createOwner(UUID projectId, UUID profileId) {
                    return new ProjectMember(
                            UUID.fromString("44444444-4444-4444-4444-444444444444"),
                            projectId,
                            profileId,
                            "OWNER",
                            "ACTIVE",
                            profileId);
                }
            };
        }

        @Bean
        CreateDraftProject createDraftProject(ProjectRepository repository) {
            return new CreateDraftProject(
                    repository,
                    new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254));
        }
    }
}
