package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.pulso.backend.project.application.CreateDraftProject;
import com.pulso.backend.project.application.ProjectConflictException;
import com.pulso.backend.project.domain.ProjectDraft;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTIVE_SECTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INACTIVE_SECTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private DataSource dataSource;
    private JdbcClient jdbcClient;
    private JdbcProjectRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key
                )
                """).update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key default gen_random_uuid(),
                  slug text not null unique,
                  name text not null,
                  tagline text not null,
                  description text,
                  sector_id uuid not null references public.sectors (id),
                  city text not null,
                  stage text not null,
                  status text not null,
                  created_by_profile_id uuid not null references public.profiles (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key default gen_random_uuid(),
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  role text not null,
                  membership_status text not null,
                  created_by_profile_id uuid not null references public.profiles (id),
                  joined_at timestamptz
                )
                """).update();
        jdbcClient.sql("insert into public.profiles (id) values (:id)")
                .param("id", PROFILE_ID)
                .update();
        jdbcClient.sql("""
                insert into public.sectors (id, is_active) values
                  (:activeId, true),
                  (:inactiveId, false)
                """)
                .param("activeId", ACTIVE_SECTOR_ID)
                .param("inactiveId", INACTIVE_SECTOR_ID)
                .update();
    }

    @Test
    void readsOnlyActiveSectorsForProjectCreation() {
        assertThat(repository.hasActiveSector(ACTIVE_SECTOR_ID)).isTrue();
        assertThat(repository.hasActiveSector(INACTIVE_SECTOR_ID)).isFalse();
        assertThat(repository.hasActiveSector(
                UUID.fromString("44444444-4444-4444-4444-444444444444")))
                .isFalse();
    }

    @Test
    void persistsDraftAndOwnerWithCanonicalNamesAndRoles() {
        var result = new CreateDraftProject(repository).execute(PROFILE_ID, draft());

        assertThat(result.project().status()).isEqualTo("DRAFT");
        assertThat(result.project().createdByProfileId()).isEqualTo(PROFILE_ID);
        assertThat(result.owner().projectId()).isEqualTo(result.project().id());
        assertThat(result.owner().profileId()).isEqualTo(PROFILE_ID);
        assertThat(result.owner().role()).isEqualTo("OWNER");
        assertThat(result.owner().membershipStatus()).isEqualTo("ACTIVE");
        assertThat(count("public.projects")).isEqualTo(1);
        assertThat(count("public.project_members")).isEqualTo(1);
    }

    @Test
    void translatesDuplicateSlugIntoAProjectConflict() {
        new CreateDraftProject(repository).execute(PROFILE_ID, draft());

        assertThatThrownBy(() -> new CreateDraftProject(repository).execute(
                PROFILE_ID,
                draft()))
                .isInstanceOf(ProjectConflictException.class)
                .hasMessage("A project with the same slug already exists.");

        assertThat(count("public.projects")).isEqualTo(1);
        assertThat(count("public.project_members")).isEqualTo(1);
    }

    @Test
    void rollsBackTheProjectWhenOwnerCreationFails() {
        jdbcClient.sql("""
                create function reject_project_owner()
                returns trigger
                language plpgsql
                as $$
                begin
                  raise exception 'forced owner failure';
                end;
                $$
                """).update();
        jdbcClient.sql("""
                create trigger reject_project_owner_trigger
                before insert on public.project_members
                for each row execute function reject_project_owner()
                """).update();

        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource()));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                new CreateDraftProject(repository).execute(PROFILE_ID, draft())))
                .hasMessageContaining("forced owner failure");

        assertThat(count("public.projects")).isZero();
        assertThat(count("public.project_members")).isZero();
    }

    private DataSource dataSource() {
        return dataSource;
    }

    private int count(String table) {
        return jdbcClient.sql("select count(*) from " + table)
                .query((resultSet, rowNumber) -> resultSet.getInt(1))
                .single();
    }

    private static ProjectDraft draft() {
        return new ProjectDraft(
                "eco-pack",
                "EcoPack Bolivia",
                "Envases sostenibles",
                "Proyecto de prueba.",
                ACTIVE_SECTOR_ID,
                "Cochabamba",
                "MVP");
    }
}
