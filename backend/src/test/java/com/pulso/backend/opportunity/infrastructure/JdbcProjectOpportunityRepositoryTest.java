package com.pulso.backend.opportunity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.opportunity.domain.OpportunityContent;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectOpportunityRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
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
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProjectOpportunityRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectOpportunityRepository(jdbcClient);
        jdbcClient.sql("drop table if exists public.project_opportunities, public.project_members, public.projects, public.sectors, public.profiles cascade")
                .update();
        createSchema();
        insertFixtures();
    }

    @Test
    void persistsAnOwnerCreatedOpportunityInTheCanonicalTable() {
        var actor = repository.lockActor(PROJECT_ID, OWNER_ID).orElseThrow();
        assertThat(actor.canManage()).isTrue();
        assertThat(repository.lockActiveSector(SECTOR_ID)).isTrue();

        var created = repository.create(
                PROJECT_ID, OWNER_ID, content("TALENT", "Founder support"), "OPEN").orElseThrow();

        assertThat(created.id()).isNotNull();
        assertThat(created.projectId()).isEqualTo(PROJECT_ID);
        assertThat(created.createdByProfileId()).isEqualTo(OWNER_ID);
        assertThat(created.opportunityType()).isEqualTo("TALENT");
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(count("public.project_opportunities")).isEqualTo(1);
    }

    @Test
    void allowsAnActiveAdminAndRejectsMembersInactiveProfilesAndOtherProjects() {
        assertThat(repository.create(
                PROJECT_ID, ADMIN_ID, content("MENTOR", "Mentoring"), "DRAFT")).isPresent();
        assertThat(repository.create(
                PROJECT_ID, MEMBER_ID, content("TALENT", "Member attempt"), "DRAFT")).isEmpty();
        assertThat(repository.create(
                PROJECT_ID, INACTIVE_ID, content("TALENT", "Inactive attempt"), "DRAFT")).isEmpty();
        assertThat(repository.create(
                PROJECT_ID, EXTERNAL_ID, content("TALENT", "External attempt"), "DRAFT")).isEmpty();
        assertThat(repository.create(
                OTHER_PROJECT_ID, OWNER_ID, content("TALENT", "Cross project attempt"), "DRAFT"))
                .isEmpty();

        assertThat(count("public.project_opportunities")).isEqualTo(1);
    }

    @Test
    void editsContentWithoutChangingProtectedOpportunityFields() {
        var before = repository.create(
                PROJECT_ID, OWNER_ID, content("TALENT", "Original"), "DRAFT").orElseThrow();
        assertThat(repository.updateContent(
                PROJECT_ID,
                before.id(),
                MEMBER_ID,
                before.updatedAt(),
                content("CUSTOMER", "Member attempt"))).isEmpty();
        assertThat(repository.updateContent(
                PROJECT_ID,
                before.id(),
                INACTIVE_ID,
                before.updatedAt(),
                content("CUSTOMER", "Inactive attempt"))).isEmpty();
        assertThat(repository.find(PROJECT_ID, before.id()).orElseThrow().title()).isEqualTo("Original");
        jdbcClient.sql("update public.project_opportunities set updated_at = '2000-01-01 00:00:00+00' where id = :id")
                .param("id", before.id())
                .update();
        var staleBefore = repository.find(PROJECT_ID, before.id()).orElseThrow();

        var updated = repository.updateContent(
                PROJECT_ID,
                before.id(),
                OWNER_ID,
                staleBefore.updatedAt(),
                content("CUSTOMER", "Updated"))
                .orElseThrow();

        assertThat(updated.id()).isEqualTo(before.id());
        assertThat(updated.projectId()).isEqualTo(PROJECT_ID);
        assertThat(updated.createdByProfileId()).isEqualTo(OWNER_ID);
        assertThat(updated.status()).isEqualTo("DRAFT");
        assertThat(updated.createdAt()).isEqualTo(before.createdAt());
        assertThat(updated.opportunityType()).isEqualTo("CUSTOMER");
        assertThat(updated.title()).isEqualTo("Updated");
        assertThat(updated.updatedAt()).isAfter(staleBefore.updatedAt());
        assertThat(repository.find(PROJECT_ID, before.id()).orElseThrow())
                .usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void persistsStateAndCloseTimestampOnlyForTheExpectedAuthorizedRow() {
        var created = repository.create(
                PROJECT_ID, OWNER_ID, content("TALENT", "Open role"), "OPEN").orElseThrow();
        var paused = repository.updateStatus(
                PROJECT_ID,
                created.id(),
                OWNER_ID,
                "OPEN",
                created.updatedAt(),
                "PAUSED",
                null).orElseThrow();
        OffsetDateTime closedAt = OffsetDateTime.parse("2026-10-10T12:00:00Z");
        var closed = repository.updateStatus(
                PROJECT_ID,
                created.id(),
                ADMIN_ID,
                "PAUSED",
                paused.updatedAt(),
                "CLOSED",
                closedAt).orElseThrow();

        assertThat(paused.status()).isEqualTo("PAUSED");
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.closedAt()).isEqualTo(closedAt);
        assertThat(repository.updateStatus(
                PROJECT_ID,
                created.id(),
                MEMBER_ID,
                "CLOSED",
                closed.updatedAt(),
                "ARCHIVED",
                null)).isEmpty();
        assertThat(repository.updateStatus(
                PROJECT_ID,
                created.id(),
                OWNER_ID,
                "OPEN",
                closed.updatedAt(),
                "PAUSED",
                null)).isEmpty();
        assertThat(repository.updateStatus(
                OTHER_PROJECT_ID,
                created.id(),
                OWNER_ID,
                "CLOSED",
                closed.updatedAt(),
                "ARCHIVED",
                null)).isEmpty();
        assertThat(repository.find(PROJECT_ID, created.id()).orElseThrow().status()).isEqualTo("CLOSED");
    }

    @Test
    void returnsStablePagesAndRequiresTheRequestedProjectAndActor() {
        var first = repository.create(
                PROJECT_ID, OWNER_ID, content("TALENT", "First"), "DRAFT").orElseThrow();
        repository.create(PROJECT_ID, OWNER_ID, content("MENTOR", "Second"), "DRAFT");

        assertThat(repository.lockActor(PROJECT_ID, MEMBER_ID).orElseThrow().canManage()).isFalse();
        assertThat(repository.lockActor(PROJECT_ID, EXTERNAL_ID)).isEmpty();
        assertThat(repository.find(OTHER_PROJECT_ID, first.id())).isEmpty();
        assertThat(repository.list(PROJECT_ID, 0, 1)).hasSize(1);
        assertThat(repository.list(PROJECT_ID, 1, 1)).hasSize(1);
    }

    private void createSchema() {
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("create table public.sectors (id uuid primary key, is_active boolean not null)").update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  status text not null,
                  sector_id uuid not null references public.sectors (id)
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
                  id uuid primary key default gen_random_uuid(),
                  project_id uuid not null references public.projects (id) on delete cascade,
                  opportunity_type text not null,
                  title text not null,
                  description text not null,
                  status text not null default 'OPEN',
                  city text,
                  department text,
                  remote_allowed boolean not null default false,
                  created_by_profile_id uuid not null references public.profiles (id),
                  starts_at timestamptz,
                  ends_at timestamptz,
                  closed_at timestamptz,
                  created_at timestamptz not null default timezone('utc', now()),
                  updated_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("""
                create or replace function public.set_updated_at()
                returns trigger
                language plpgsql
                set search_path = public
                as $$
                begin
                  new.updated_at = timezone('utc', now());
                  return new;
                end;
                $$
                """).update();
        jdbcClient.sql("""
                create trigger project_opportunities_set_updated_at
                before update on public.project_opportunities
                for each row execute function public.set_updated_at()
                """).update();
    }

    private void insertFixtures() {
        jdbcClient.sql("insert into public.profiles (id) values (:ownerId), (:adminId), (:memberId), (:inactiveId), (:externalId)")
                .param("ownerId", OWNER_ID)
                .param("adminId", ADMIN_ID)
                .param("memberId", MEMBER_ID)
                .param("inactiveId", INACTIVE_ID)
                .param("externalId", EXTERNAL_ID)
                .update();
        jdbcClient.sql("insert into public.sectors (id, is_active) values (:sectorId, true)")
                .param("sectorId", SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.projects (id, status, sector_id) values
                  (:projectId, 'PUBLISHED', :sectorId),
                  (:otherProjectId, 'DRAFT', :sectorId)
                """)
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .param("sectorId", SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (id, project_id, profile_id, role, membership_status) values
                  ('10000000-0000-0000-0000-000000000001', :projectId, :ownerId, 'OWNER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000002', :projectId, :adminId, 'ADMIN', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000003', :projectId, :memberId, 'MEMBER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000004', :projectId, :inactiveId, 'OWNER', 'LEFT'),
                  ('10000000-0000-0000-0000-000000000005', :otherProjectId, :memberId, 'OWNER', 'ACTIVE')
                """)
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .param("adminId", ADMIN_ID)
                .param("memberId", MEMBER_ID)
                .param("inactiveId", INACTIVE_ID)
                .update();
    }

    private static OpportunityContent content(String type, String title) {
        return OpportunityContent.validate(
                type, title, "Project opportunity details.", "La Paz", "La Paz", false);
    }

    private int count(String table) {
        return jdbcClient.sql("select count(*) from " + table)
                .query((resultSet, rowNumber) -> resultSet.getInt(1))
                .single();
    }
}
