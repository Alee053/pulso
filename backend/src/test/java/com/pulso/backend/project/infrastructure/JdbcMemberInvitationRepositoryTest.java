package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.config.ProjectProperties;
import com.pulso.backend.project.application.CreateMemberInvitation;
import com.pulso.backend.project.domain.DuplicateMemberInvitationException;

@Testcontainers(disabledWithoutDocker = true)
class JdbcMemberInvitationRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final ProjectProperties PROPERTIES =
            new ProjectProperties(2, 120, 160, 240, 5000, 120, 40, 254);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcMemberInvitationRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcMemberInvitationRepository(jdbcClient, PROPERTIES);

        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.profiles cascade")
                .update();
        jdbcClient.sql("create table public.profiles (id uuid primary key)").update();
        jdbcClient.sql("create table public.projects (id uuid primary key)").update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key default gen_random_uuid(),
                  project_id uuid not null references public.projects (id),
                  invitee_email text,
                  profile_id uuid references public.profiles (id),
                  invite_token_digest text unique,
                  role text not null,
                  membership_status text not null,
                  created_by_profile_id uuid not null references public.profiles (id),
                  updated_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("""
                create unique index project_members_active_invite_email_key
                on public.project_members (project_id, invitee_email)
                where membership_status = 'INVITED' and invitee_email is not null
                """).update();
        jdbcClient.sql("insert into public.profiles (id) values (:ownerId)")
                .param("ownerId", OWNER_ID)
                .update();
        jdbcClient.sql("insert into public.projects (id) values (:projectId)")
                .param("projectId", PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                    project_id, profile_id, role, membership_status, created_by_profile_id
                ) values (:projectId, :ownerId, 'OWNER', 'ACTIVE', :ownerId)
                """)
                .param("projectId", PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .update();
    }

    @Test
    void createsAnInvitationWithCanonicalFieldsAndDigestOnly() {
        var result = new CreateMemberInvitation(
                repository,
                () -> "accept-me",
                PROPERTIES).execute(PROJECT_ID, OWNER_ID, " Ada@Example.COM ");

        assertThat(result.id()).isNotNull();
        assertThat(result.invitation().inviteeEmail()).isEqualTo("ada@example.com");
        assertThat(result.invitation().profileId()).isNull();
        assertThat(result.invitation().role()).isEqualTo("MEMBER");
        assertThat(result.invitation().membershipStatus()).isEqualTo("INVITED");
        assertThat(jdbcClient.sql("""
                select invite_token_digest
                from public.project_members
                where id = :id
                """)
                .param("id", result.id())
                .query(String.class)
                .single())
                .isEqualTo("69a24810112e8f3e257dfd85e240bcc8e8a60155787eaa0ec9526f584f0deb7d");
    }

    @Test
    void rejectsASecondActiveInvitationForTheSameEmail() {
        CreateMemberInvitation service = new CreateMemberInvitation(
                repository,
                () -> "accept-me",
                PROPERTIES);
        service.execute(PROJECT_ID, OWNER_ID, "ada@example.com");

        assertThatThrownBy(() -> service.execute(
                PROJECT_ID,
                OWNER_ID,
                " ADA@example.com "))
                .isInstanceOf(DuplicateMemberInvitationException.class);

        assertThat(countInvitations()).isEqualTo(1);
    }

    private int countInvitations() {
        return jdbcClient.sql("select count(*) from public.project_members where membership_status = 'INVITED'")
                .query(Integer.class)
                .single();
    }
}
