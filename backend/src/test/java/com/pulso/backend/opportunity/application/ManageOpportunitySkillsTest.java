package com.pulso.backend.opportunity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.pulso.backend.opportunity.domain.OpportunityActor;
import com.pulso.backend.opportunity.domain.OpportunityContent;
import com.pulso.backend.opportunity.domain.OpportunitySkill;
import com.pulso.backend.opportunity.domain.OpportunityValidationException;
import com.pulso.backend.opportunity.domain.ProjectOpportunity;

class ManageOpportunitySkillsTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OPPORTUNITY_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID SKILL_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID SECOND_SKILL_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-21T12:00:00Z");

    @Test
    void associatesChangesImportanceAndRemovesWithoutDuplicating() {
        FakeSkills skills = new FakeSkills();
        skills.active.addAll(Set.of(SKILL_ID, SECOND_SKILL_ID));
        FakeProjectOpportunities opportunities = new FakeProjectOpportunities("ADMIN", "ACTIVE", true);
        ManageOpportunitySkills service = new ManageOpportunitySkills(opportunities, skills);

        OpportunitySkill preferred = service.associate(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, " preferred ", PROFILE_ID);
        assertThat(preferred.importance()).isEqualTo("PREFERRED");
        assertThat(skills.rows).containsExactly(preferred);

        assertThatThrownBy(() -> service.associate(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "REQUIRED", PROFILE_ID))
                .isInstanceOf(OpportunitySkillConflictException.class);
        assertThat(skills.rows).hasSize(1);

        OpportunitySkill required = service.changeImportance(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "REQUIRED", PROFILE_ID);
        assertThat(required.importance()).isEqualTo("REQUIRED");
        assertThat(service.list(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID))
                .containsExactly(required);

        service.remove(PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, PROFILE_ID);
        assertThat(skills.rows).isEmpty();
        assertThat(skills.active).contains(SKILL_ID);
    }

    @Test
    void requiresAnActiveSkillAndValidImportanceAndManagerPermission() {
        FakeSkills skills = new FakeSkills();
        FakeProjectOpportunities opportunities = new FakeProjectOpportunities("OWNER", "ACTIVE", true);
        ManageOpportunitySkills service = new ManageOpportunitySkills(opportunities, skills);

        assertThatThrownBy(() -> service.associate(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "REQUIRED", PROFILE_ID))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("skillId must reference an active skill.");
        skills.active.add(SKILL_ID);
        assertThatThrownBy(() -> service.associate(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "OPTIONAL", PROFILE_ID))
                .isInstanceOf(OpportunityValidationException.class)
                .hasMessage("importance must be REQUIRED or PREFERRED.");
        assertThat(skills.rows).isEmpty();

        FakeProjectOpportunities member = new FakeProjectOpportunities("MEMBER", "ACTIVE", true);
        ManageOpportunitySkills memberService = new ManageOpportunitySkills(member, skills);
        assertThatThrownBy(() -> memberService.list(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID))
                .isInstanceOf(ProjectOpportunityPermissionException.class);
        assertThatThrownBy(() -> memberService.associate(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "REQUIRED", PROFILE_ID))
                .isInstanceOf(ProjectOpportunityPermissionException.class);
        assertThat(skills.rows).isEmpty();
    }

    @Test
    void rejectsMissingOpportunityAndAssociationWithoutExposingCrossProjectRows() {
        FakeSkills skills = new FakeSkills();
        FakeProjectOpportunities missing = new FakeProjectOpportunities("OWNER", "ACTIVE", false);
        ManageOpportunitySkills service = new ManageOpportunitySkills(missing, skills);

        assertThatThrownBy(() -> service.list(PROJECT_ID, OPPORTUNITY_ID, PROFILE_ID))
                .isInstanceOf(ProjectOpportunityNotFoundException.class);
        assertThatThrownBy(() -> service.changeImportance(
                PROJECT_ID, OPPORTUNITY_ID, SKILL_ID, "REQUIRED", PROFILE_ID))
                .isInstanceOf(ProjectOpportunityNotFoundException.class);
    }

    private static ProjectOpportunity opportunity() {
        return new ProjectOpportunity(
                OPPORTUNITY_ID, PROJECT_ID, "TALENT", "Frontend developer",
                "Build the frontend.", "OPEN", null, null, true, PROFILE_ID,
                null, null, null, NOW, NOW);
    }

    private static OpportunitySkill association(UUID skillId, String importance) {
        return new OpportunitySkill(
                OPPORTUNITY_ID, skillId, "frontend-skill", "Frontend skill",
                importance, true, NOW);
    }

    private static final class FakeProjectOpportunities implements ProjectOpportunityRepository {
        private final OpportunityActor actor;
        private final boolean hasOpportunity;

        private FakeProjectOpportunities(String role, String status, boolean hasOpportunity) {
            this.actor = new OpportunityActor(
                    PROJECT_ID, PROFILE_ID, "PUBLISHED", null, role, status);
            this.hasOpportunity = hasOpportunity;
        }

        @Override
        public Optional<OpportunityActor> lockActor(UUID projectId, UUID profileId) {
            return PROJECT_ID.equals(projectId) && PROFILE_ID.equals(profileId)
                    ? Optional.of(actor) : Optional.empty();
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
            return hasOpportunity && PROJECT_ID.equals(projectId) && OPPORTUNITY_ID.equals(opportunityId)
                    ? Optional.of(opportunity()) : Optional.empty();
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

    private static final class FakeSkills implements OpportunitySkillRepository {
        private final Set<UUID> active = new HashSet<>();
        private final List<OpportunitySkill> rows = new ArrayList<>();

        @Override
        public List<OpportunitySkill> list(UUID projectId, UUID opportunityId) {
            return rows.stream().filter(row -> row.opportunityId().equals(opportunityId)).toList();
        }

        @Override
        public boolean isActiveSkill(UUID skillId) {
            return active.contains(skillId);
        }

        @Override
        public Optional<OpportunitySkill> create(
                UUID projectId, UUID opportunityId, UUID skillId, String importance, UUID profileId) {
            if (rows.stream().anyMatch(row -> row.skillId().equals(skillId))) return Optional.empty();
            OpportunitySkill row = association(skillId, importance);
            rows.add(row);
            return Optional.of(row);
        }

        @Override
        public Optional<OpportunitySkill> updateImportance(
                UUID projectId, UUID opportunityId, UUID skillId, String importance, UUID profileId) {
            Optional<OpportunitySkill> existing = rows.stream()
                    .filter(row -> row.skillId().equals(skillId)).findFirst();
            existing.ifPresent(row -> rows.set(rows.indexOf(row), association(skillId, importance)));
            return rows.stream().filter(row -> row.skillId().equals(skillId)).findFirst();
        }

        @Override
        public boolean delete(UUID projectId, UUID opportunityId, UUID skillId, UUID profileId) {
            return rows.removeIf(row -> row.skillId().equals(skillId));
        }
    }
}
