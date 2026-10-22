package com.pulso.backend.opportunity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.pulso.backend.config.SecurityConfig;
import com.pulso.backend.opportunity.application.ManageOpportunitySkills;
import com.pulso.backend.opportunity.application.OpportunitySkillRepository;
import com.pulso.backend.opportunity.application.ProjectOpportunityRepository;
import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.OpportunitySkill;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

@WebMvcTest(ProjectOpportunitySkillsController.class)
@Import({ SecurityConfig.class, ProjectOpportunitySkillsSecurityTest.TestBeans.class })
class ProjectOpportunitySkillsSecurityTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-local-tests";
    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID OPPORTUNITY_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID SKILL_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");

    @Autowired
    private CapturingSkillRepository skills;

    @Autowired
    private MockMvc mockMvc;
    @Test
    void requiresValidatedAuthenticationAndPersistedOwnerOrAdminMembership() throws Exception {
        skills.rows.clear();
        mockMvc.perform(get(path()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(path())
                .header("Authorization", "Bearer " + token("authenticated", MEMBER_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path())
                .header("Authorization", "Bearer " + token("service_role", OWNER_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsTheJwtSubjectOwnerToAddAnActiveSkillAndRejectsDuplicates() throws Exception {
        skills.rows.clear();
        String bearer = "Bearer " + token("authenticated", OWNER_ID.toString());
        mockMvc.perform(post(path())
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skillId").value(SKILL_ID.toString()))
                .andExpect(jsonPath("$.importance").value("REQUIRED"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post(path())
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Opportunity skill conflict"));
    }

    @Test
    void exposesListUpdateAndDeleteOperationsThroughTheSameProtectedRoute() throws Exception {
        skills.rows.clear();
        String bearer = "Bearer " + token("authenticated", OWNER_ID.toString());
        mockMvc.perform(post(path())
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(path()).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].importance").value("REQUIRED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(path() + "/" + SKILL_ID)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importance\":\"PREFERRED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance").value("PREFERRED"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(path() + "/" + SKILL_ID)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());
        assertThat(skills.rows).isEmpty();
    }

    private static String path() {
        return "/api/v1/projects/" + PROJECT_ID + "/opportunities/" + OPPORTUNITY_ID + "/skills";
    }

    private static String request() {
        return "{\"skillId\":\"" + SKILL_ID + "\",\"importance\":\"REQUIRED\"}";
    }

    private static String token(String role, String subject) throws Exception {
        Date issuedAt = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test.supabase.local/auth/v1")
                .subject(subject)
                .audience("authenticated")
                .claim("role", role)
                .issueTime(issuedAt)
                .expirationTime(new Date(issuedAt.getTime() + 300_000))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return signedJwt.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CapturingProjectOpportunityRepository projectOpportunityRepository() {
            return new CapturingProjectOpportunityRepository();
        }

        @Bean
        CapturingSkillRepository opportunitySkillRepository() {
            return new CapturingSkillRepository();
        }

        @Bean
        ManageOpportunitySkills manageOpportunitySkills(
                CapturingProjectOpportunityRepository opportunities,
                CapturingSkillRepository skills) {
            return new ManageOpportunitySkills(opportunities, skills);
        }
    }

    static final class CapturingProjectOpportunityRepository implements ProjectOpportunityRepository {

        @Override
        public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
            String role = OWNER_ID.equals(profileId) ? "OWNER" : "MEMBER";
            return Optional.of(new OpportunityActor(projectId, profileId,
                    "PUBLISHED", null, role, "ACTIVE"));
        }

        @Override
        public boolean lockActiveSector(UUID sectorId) {
            return false;
        }

        @Override
        public List<ProjectOpportunity> list(UUID projectId, int offset, int limit) {
            return List.of();
        }

        @Override
        public Optional<ProjectOpportunity> find(UUID projectId, UUID opportunityId) {
            if (!PROJECT_ID.equals(projectId) || !OPPORTUNITY_ID.equals(opportunityId)) {
                return Optional.empty();
            }
            return Optional.of(new ProjectOpportunity(
                    OPPORTUNITY_ID, PROJECT_ID, "TALENT", "Frontend developer",
                    "Build the frontend.", "OPEN", null, null, true, OWNER_ID,
                    null, null, null, OffsetDateTime.now(), OffsetDateTime.now()));
        }

        @Override
        public Optional<ProjectOpportunity> create(
                UUID projectId, UUID profileId, OpportunityContent content, String status) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectOpportunity> updateContent(
                UUID projectId, UUID opportunityId, UUID profileId,
                OffsetDateTime expectedUpdatedAt, OpportunityContent content) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectOpportunity> updateStatus(
                UUID projectId, UUID opportunityId, UUID profileId, String expectedStatus,
                OffsetDateTime expectedUpdatedAt, String nextStatus, OffsetDateTime closedAt) {
            return Optional.empty();
        }
    }

    static final class CapturingSkillRepository implements OpportunitySkillRepository {
        private final List<OpportunitySkill> rows = new ArrayList<>();

        @Override
        public List<OpportunitySkill> list(UUID projectId, UUID opportunityId) {
            return rows.stream()
                    .filter(row -> row.opportunityId().equals(opportunityId))
                    .toList();
        }

        @Override
        public boolean isActiveSkill(UUID skillId) {
            return SKILL_ID.equals(skillId);
        }

        @Override
        public Optional<OpportunitySkill> create(
                UUID projectId, UUID opportunityId, UUID skillId, String importance, UUID profileId) {
            if (!rows.isEmpty()) return Optional.empty();
            OpportunitySkill row = new OpportunitySkill(
                    opportunityId, skillId, "frontend", "Frontend", importance, true,
                    OffsetDateTime.now());
            rows.add(row);
            return Optional.of(row);
        }

        @Override
        public Optional<OpportunitySkill> updateImportance(
                UUID projectId, UUID opportunityId, UUID skillId, String importance, UUID profileId) {
            Optional<OpportunitySkill> current = rows.stream()
                    .filter(row -> row.opportunityId().equals(opportunityId))
                    .filter(row -> row.skillId().equals(skillId))
                    .findFirst();
            current.ifPresent(row -> rows.set(rows.indexOf(row), new OpportunitySkill(
                    row.opportunityId(), row.skillId(), row.skillSlug(), row.skillName(),
                    importance, row.active(), row.createdAt())));
            return rows.stream()
                    .filter(row -> row.opportunityId().equals(opportunityId))
                    .filter(row -> row.skillId().equals(skillId))
                    .findFirst();
        }

        @Override
        public boolean delete(UUID projectId, UUID opportunityId, UUID skillId, UUID profileId) {
            return rows.removeIf(row -> row.opportunityId().equals(opportunityId)
                    && row.skillId().equals(skillId));
        }
    }
}
