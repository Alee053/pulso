package com.pulso.backend.opportunity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOpportunitySkillRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID OWNER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID OPPORTUNITY_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID OTHER_OPPORTUNITY_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID ACTIVE_SKILL_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID INACTIVE_SKILL_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcOpportunitySkillRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcOpportunitySkillRepository(jdbcClient);
        jdbcClient.sql("drop table if exists public.opportunity_skills, "
                + "public.project_opportunities, public.project_members, public.skills, "
                + "public.projects, public.profiles cascade").update();
        createSchema();
        insertFixtures();
    }

    @Test
    void associatesUpdatesListsAndRemovesWithoutDeletingCatalogSkills() {
        assertThat(repository.isActiveSkill(ACTIVE_SKILL_ID)).isTrue();
        assertThat(repository.isActiveSkill(INACTIVE_SKILL_ID)).isFalse();
        var created = repository.create(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "REQUIRED", OWNER_ID).orElseThrow();

        assertThat(created.skillName()).isEqualTo("Angular");
        assertThat(created.importance()).isEqualTo("REQUIRED");
        assertThat(repository.list(PROJECT_ID, OPPORTUNITY_ID)).containsExactly(created);
        assertThat(repository.create(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "PREFERRED", OWNER_ID)).isEmpty();
        assertThat(repository.create(
                PROJECT_ID, OPPORTUNITY_ID, INACTIVE_SKILL_ID, "PREFERRED", OWNER_ID)).isEmpty();

        var updated = repository.updateImportance(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "PREFERRED", OWNER_ID).orElseThrow();
        assertThat(updated.importance()).isEqualTo("PREFERRED");
        assertThat(repository.updateImportance(
                PROJECT_ID, OPPORTUNITY_ID, INACTIVE_SKILL_ID, "REQUIRED", OWNER_ID)).isEmpty();
        assertThat(repository.delete(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, OWNER_ID)).isTrue();
        assertThat(repository.list(PROJECT_ID, OPPORTUNITY_ID)).isEmpty();
        assertThat(repository.isActiveSkill(ACTIVE_SKILL_ID)).isTrue();
    }

    @Test
    void repeatsProjectMembershipAndOpportunityPredicatesForEveryWrite() {
        assertThat(repository.create(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "REQUIRED", MEMBER_ID)).isEmpty();
        assertThat(repository.create(
                OTHER_PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "REQUIRED", OWNER_ID)).isEmpty();
        assertThat(repository.create(
                PROJECT_ID, OTHER_OPPORTUNITY_ID, ACTIVE_SKILL_ID, "REQUIRED", OWNER_ID)).isEmpty();

        assertThat(repository.create(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "REQUIRED", OWNER_ID)).isPresent();
        assertThat(repository.updateImportance(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, "PREFERRED", MEMBER_ID)).isEmpty();
        assertThat(repository.delete(
                PROJECT_ID, OPPORTUNITY_ID, ACTIVE_SKILL_ID, MEMBER_ID)).isFalse();
        assertThat(repository.list(PROJECT_ID, OPPORTUNITY_ID)).hasSize(1);
    }

    private void createSchema() {
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid not null references public.profiles (id),
                  role text not null,
                  membership_status text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_opportunities (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id) on delete cascade
                )
                """).update();
        jdbcClient.sql("""
                create table public.skills (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.opportunity_skills (
                  opportunity_id uuid not null references public.project_opportunities (id) on delete cascade,
                  skill_id uuid not null references public.skills (id) on delete restrict,
                  importance text not null check (importance in ('REQUIRED', 'PREFERRED')),
                  created_at timestamptz not null default timezone('utc', now()),
                  primary key (opportunity_id, skill_id)
                )
                """).update();
    }

    private void insertFixtures() {
        jdbcClient.sql("insert into public.profiles (id) values (:ownerId), (:memberId)")
                .param("ownerId", OWNER_ID)
                .param("memberId", MEMBER_ID)
                .update();
        jdbcClient.sql("insert into public.projects (id) values (:projectId), (:otherProjectId)")
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (id, project_id, profile_id, role, membership_status)
                values
                  ('10000000-0000-0000-0000-000000000001', :projectId, :ownerId, 'OWNER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000002', :projectId, :memberId, 'MEMBER', 'ACTIVE')
                """)
                .param("projectId", PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .param("memberId", MEMBER_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_opportunities (id, project_id)
                values (:opportunityId, :projectId), (:otherOpportunityId, :otherProjectId)
                """)
                .param("opportunityId", OPPORTUNITY_ID)
                .param("projectId", PROJECT_ID)
                .param("otherOpportunityId", OTHER_OPPORTUNITY_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.skills (id, slug, name, is_active)
                values
                  (:activeId, 'angular', 'Angular', true),
                  (:inactiveId, 'inactive', 'Inactive', false)
                """)
                .param("activeId", ACTIVE_SKILL_ID)
                .param("inactiveId", INACTIVE_SKILL_ID)
                .update();
    }
}
