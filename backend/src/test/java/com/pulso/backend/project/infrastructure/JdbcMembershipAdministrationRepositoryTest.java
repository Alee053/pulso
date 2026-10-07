package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.pulso.backend.project.domain.ProjectMember;
import com.pulso.backend.project.domain.ManagedProjectMember;

@Testcontainers(disabledWithoutDocker = true)
class JdbcMembershipAdministrationRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID OWNER_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MEMBER_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OWNER_MEMBERSHIP_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER_MEMBERSHIP_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID INVITED_MEMBERSHIP_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcMembershipAdministrationRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcMembershipAdministrationRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.profiles cascade")
                .update();
        jdbcClient.sql("create table public.profiles (id uuid primary key, display_name text)")
                .update();
        jdbcClient.sql("create table public.projects (id uuid primary key)")
                .update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null,
                  profile_id uuid,
                  invitee_email text,
                  role text not null,
                  membership_status text not null,
                  created_by_profile_id uuid,
                  created_at timestamptz not null default timezone('utc', now()),
                  updated_at timestamptz not null default timezone('utc', now())
                )
                """).update();
        jdbcClient.sql("insert into public.profiles (id, display_name) values (:ownerId, 'Owner'), (:memberId, 'Ada')")
                .param("ownerId", OWNER_ID)
                .param("memberId", MEMBER_ID)
                .update();
        jdbcClient.sql("insert into public.projects (id) values (:projectId)")
                .param("projectId", PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                    id, project_id, profile_id, invitee_email, role, membership_status, created_by_profile_id
                ) values
                    (:ownerMembershipId, :projectId, :ownerId, null, 'OWNER', 'ACTIVE', :ownerId),
                    (:memberMembershipId, :projectId, :memberId, null, 'MEMBER', 'ACTIVE', :ownerId),
                    (:invitedMembershipId, :projectId, null, 'invitee@example.test', 'MEMBER', 'INVITED', :ownerId)
                """)
                .param("ownerMembershipId", OWNER_MEMBERSHIP_ID)
                .param("memberMembershipId", MEMBER_MEMBERSHIP_ID)
                .param("invitedMembershipId", INVITED_MEMBERSHIP_ID)
                .param("projectId", PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .param("memberId", MEMBER_ID)
                .update();
    }

    @Test
    void loadsActorTargetAndProjectMembers() {
        repository.lockProject(PROJECT_ID);

        assertThat(repository.findActor(PROJECT_ID, OWNER_ID)).isPresent()
                .get()
                .satisfies(actor -> {
                    assertThat(actor.projectId()).isEqualTo(PROJECT_ID);
                    assertThat(actor.profileId()).isEqualTo(OWNER_ID);
                    assertThat(actor.role()).isEqualTo("OWNER");
                    assertThat(actor.membershipStatus()).isEqualTo("ACTIVE");
                });

        assertThat(repository.findMember(PROJECT_ID, MEMBER_MEMBERSHIP_ID))
                .contains(member("MEMBER", "ACTIVE"));
        assertThat(repository.findMembers(PROJECT_ID)).hasSize(3);
    }

    @Test
    void updatesOnlyTheExpectedMembershipAndPreservesIdentity() {
        int updated = repository.updateMembership(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                "MEMBER",
                "ACTIVE",
                "ADMIN",
                "ACTIVE");

        assertThat(updated).isEqualTo(1);
        ProjectMember member = repository.findMember(PROJECT_ID, MEMBER_MEMBERSHIP_ID).orElseThrow();
        assertThat(member.id()).isEqualTo(MEMBER_MEMBERSHIP_ID);
        assertThat(member.profileId()).isEqualTo(MEMBER_ID);
        assertThat(member.role()).isEqualTo("ADMIN");
        assertThat(member.membershipStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void refusesAStaleExpectedMembershipState() {
        int updated = repository.updateMembership(
                PROJECT_ID,
                MEMBER_MEMBERSHIP_ID,
                "MEMBER",
                "LEFT",
                "ADMIN",
                "ACTIVE");

        assertThat(updated).isZero();
        assertThat(repository.findMember(PROJECT_ID, MEMBER_MEMBERSHIP_ID))
                .contains(member("MEMBER", "ACTIVE"));
    }

    @Test
    void listsMemberDetailsAndInvitationAddressWithoutTokenMaterial() {
        List<ManagedProjectMember> members = repository.findManagedMembers(PROJECT_ID, 0, 2);

        assertThat(members).hasSize(2);
        assertThat(members).anySatisfy(member -> {
            assertThat(member.id()).isEqualTo(MEMBER_MEMBERSHIP_ID);
            assertThat(member.profileId()).isEqualTo(MEMBER_ID);
            assertThat(member.displayName()).isEqualTo("Ada");
            assertThat(member.inviteeEmail()).isNull();
        });

        List<ManagedProjectMember> invitations = repository.findManagedMembers(PROJECT_ID, 2, 2);
        assertThat(invitations).singleElement().satisfies(invitation -> {
            assertThat(invitation.id()).isEqualTo(INVITED_MEMBERSHIP_ID);
            assertThat(invitation.profileId()).isNull();
            assertThat(invitation.inviteeEmail()).isEqualTo("invitee@example.test");
            assertThat(invitation.membershipStatus()).isEqualTo("INVITED");
        });
    }

    private static ProjectMember member(String role, String membershipStatus) {
        return new ProjectMember(
                MEMBER_MEMBERSHIP_ID,
                PROJECT_ID,
                MEMBER_ID,
                role,
                membershipStatus,
                OWNER_ID);
    }
}
