package com.pulso.backend.opportunity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.pulso.backend.config.ProjectOpportunityProperties;
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.opportunity.application.ManageProjectOpportunities;
import com.pulso.backend.opportunity.application.ProjectOpportunityRepository;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

/** Exercises the authenticated JWT boundary without connecting to Supabase Auth. */
@WebMvcTest(ProjectOpportunityController.class)
@Import({ SecurityConfig.class, ProjectOpportunitySecurityTest.TestBeans.class })
class ProjectOpportunitySecurityTest {

    private static final String ISSUER = "http://test.supabase.local/auth/v1";
    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID MEMBER_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID INACTIVE_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID EXTERNAL_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingRepository repository;

    @BeforeEach
    void resetRepository() {
        repository.lastProfileId = null;
        repository.createdByProfileId = null;
        repository.listCalls = 0;
    }

    @Test
    void requiresAnAccessTokenForProjectOpportunities() throws Exception {
        mockMvc.perform(get(path()))
                .andExpect(status().isUnauthorized());

        assertThat(repository.lastProfileId).isNull();
    }

    @Test
    void acceptsAnOwnerAndAnAdminUsingTheJwtSubjectAsIdentity() throws Exception {
        mockMvc.perform(post(path())
                .header("Authorization", "Bearer " + token("authenticated", OWNER_ID.toString(), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated());

        assertThat(repository.lastProfileId).isEqualTo(OWNER_ID);
        assertThat(repository.createdByProfileId).isEqualTo(OWNER_ID);

        repository.createdByProfileId = null;
        mockMvc.perform(post(path())
                .header("Authorization", "Bearer " + token("authenticated", ADMIN_ID.toString(), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
                .andExpect(status().isCreated());

        assertThat(repository.createdByProfileId).isEqualTo(ADMIN_ID);
    }

    @Test
    void rejectsServiceRoleAndMalformedSubjectTokensBeforePersistence() throws Exception {
        mockMvc.perform(get(path())
                .header("Authorization", "Bearer " + token("service_role", OWNER_ID.toString(), null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(path())
                .header("Authorization", "Bearer " + token("authenticated", "not-a-uuid", null)))
                .andExpect(status().isUnauthorized());

        assertThat(repository.listCalls).isZero();
    }

    @Test
    void resolvesAuthorizationFromPersistedMembershipAndRejectsClientIdentityFields() throws Exception {
        mockMvc.perform(get(path())
                .header("Authorization", "Bearer " + token(
                        "authenticated", MEMBER_ID.toString(), "OWNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path())
                .header("Authorization", "Bearer " + token(
                        "authenticated", INACTIVE_ID.toString(), "ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path())
                .header("Authorization", "Bearer " + token(
                        "authenticated", EXTERNAL_ID.toString(), "OWNER")))
                .andExpect(status().isForbidden());

        repository.lastProfileId = null;
        mockMvc.perform(post(path())
                .header("Authorization", "Bearer " + token("authenticated", OWNER_ID.toString(), null))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest().replace("}", ",\"createdByProfileId\":\""
                        + EXTERNAL_ID + "\"}")))
                .andExpect(status().isBadRequest());

        assertThat(repository.createdByProfileId).isNull();
        assertThat(repository.lastProfileId).isNull();
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/opportunities";
    }

    private static String validRequest() {
        return """
                {
                  "opportunityType": "TALENT",
                  "title": "Backend opportunity",
                  "description": "Help us deliver the next release.",
                  "city": "La Paz",
                  "department": "La Paz",
                  "remoteAllowed": false,
                  "status": "DRAFT"
                }
                """;
    }

    private static String token(String role, String subject, String projectRole) throws Exception {
        Date issuedAt = new Date();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .audience("authenticated")
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(new Date(issuedAt.getTime() + 300_000));
        if (projectRole != null) {
            claims.claim("projectRole", projectRole);
        }
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return signedJwt.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CapturingRepository projectOpportunityRepository() {
            return new CapturingRepository();
        }

        @Bean
        ManageProjectOpportunities manageProjectOpportunities(
                CapturingRepository repository,
                ProjectOpportunityProperties properties) {
            return new ManageProjectOpportunities(repository, properties);
        }
    }

    static final class CapturingRepository implements ProjectOpportunityRepository {

        private static final UUID SECTOR_ID = UUID.fromString(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private UUID lastProfileId;
        private UUID createdByProfileId;
        private int listCalls;
        private ProjectOpportunity created;

        @Override
        public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
            lastProfileId = profileId;
            String role = profileId.equals(OWNER_ID) ? "OWNER"
                    : profileId.equals(ADMIN_ID) ? "ADMIN"
                    : profileId.equals(INACTIVE_ID) ? "OWNER" : "MEMBER";
            String status = profileId.equals(INACTIVE_ID) ? "LEFT" : "ACTIVE";
            if (profileId.equals(EXTERNAL_ID)) {
                return Optional.empty();
            }
            return Optional.of(new OpportunityActor(
                    projectId, profileId, "PUBLISHED", SECTOR_ID, role, status));
        }

        @Override
        public boolean lockActiveSector(UUID sectorId) {
            return SECTOR_ID.equals(sectorId);
        }

        @Override
        public List<ProjectOpportunity> list(UUID projectId, int offset, int limit) {
            listCalls++;
            return List.of();
        }

        @Override
        public Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectOpportunity> create(
                UUID projectId,
                UUID profileId,
                OpportunityContent content,
                String status) {
            createdByProfileId = profileId;
            OffsetDateTime now = OffsetDateTime.now();
            created = new ProjectOpportunity(
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), projectId,
                    content.opportunityType(), content.title(), content.description(), status,
                    content.city(), content.department(), content.remoteAllowed(), profileId,
                    null, null, null, now, now);
            return Optional.of(created);
        }

        @Override
        public Optional<ProjectOpportunity> updateContent(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                OffsetDateTime expectedUpdatedAt,
                OpportunityContent content) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectOpportunity> updateStatus(
                UUID projectId,
                UUID opportunityId,
                UUID profileId,
                String expectedStatus,
                OffsetDateTime expectedUpdatedAt,
                String nextStatus,
                OffsetDateTime closedAt) {
            return Optional.empty();
        }
    }
}
