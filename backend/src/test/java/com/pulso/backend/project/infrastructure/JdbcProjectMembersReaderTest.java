package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.project.application.ProjectMembersSnapshot;
import com.pulso.backend.project.domain.ProjectMemberSummary;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectMembersReaderTest {

    private static final UUID PROJECT_ID = id(100);
    private static final UUID ACTOR_PROFILE_ID = id(1001);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbc;
    private JdbcProjectMembersReader reader;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        reader = new JdbcProjectMembersReader(jdbc);
        jdbc.sql("drop table if exists public.project_members, public.profiles cascade").update();
        jdbc.sql("""
                create table public.profiles (
                    id uuid primary key,
                    display_name text not null,
                    account_status text not null default 'ACTIVE',
                    profile_visibility text not null default 'PUBLIC',
                    deleted_at timestamptz
                )
                """).update();
        jdbc.sql("""
                create table public.project_members (
                    id uuid primary key,
                    project_id uuid not null,
                    profile_id uuid,
                    role text not null,
                    membership_status text not null,
                    is_public boolean not null,
                    display_title text,
                    invitee_email text,
                    invite_token_digest text,
                    created_by_profile_id uuid not null,
                    unique (project_id, profile_id)
                )
                """).update();
        for (int row = 1; row <= 9; row++) {
            jdbc.sql("insert into public.profiles (id, display_name) values (:id, :name)")
                    .param("id", id(1000 + row)).param("name", "Profile " + row).update();
            addMembership(row, id(1000 + row), PROJECT_ID,
                    row == 1 ? "OWNER" : "MEMBER", row == 8 ? "LEFT" : row == 9 ? "REMOVED" : "ACTIVE");
        }
        jdbc.sql("update public.project_members set is_public = false where id = :id")
                .param("id", id(3)).update();
        jdbc.sql("update public.profiles set profile_visibility = 'PRIVATE' where id = :id")
                .param("id", id(1004)).update();
        jdbc.sql("update public.profiles set profile_visibility = 'MEMBERS_ONLY' where id = :id")
                .param("id", id(1005)).update();
        jdbc.sql("update public.profiles set account_status = 'SUSPENDED' where id = :id")
                .param("id", id(1006)).update();
        jdbc.sql("update public.profiles set deleted_at = now() where id = :id")
                .param("id", id(1007)).update();
        addMembership(10, null, PROJECT_ID, "MEMBER", "INVITED");
        addMembership(11, null, PROJECT_ID, "MEMBER", "REMOVED");
        addMembership(12, id(1002), id(200), "OWNER", "ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN"})
    void administratorsReadAllStatesButNamesAndTitlesOnlyForPublicActiveProfiles(String role) {
        updateActor(role, "ACTIVE");
        ProjectMembersSnapshot snapshot = read(PROJECT_ID, ACTOR_PROFILE_ID, 0, 51);

        assertThat(snapshot.actor().role()).isEqualTo(role);
        assertThat(snapshot.actor().membershipStatus()).isEqualTo("ACTIVE");
        assertThat(snapshot.members()).extracting(ProjectMemberSummary::id)
                .containsExactlyElementsOf(ids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
        assertThat(snapshot.members().subList(0, 2)).allSatisfy(member -> {
            assertThat(member.displayName()).startsWith("Profile ");
            assertThat(member.displayTitle()).isEqualTo("Public title");
        });
        assertThat(snapshot.members().subList(2, 11)).allSatisfy(member -> {
            assertThat(member.displayName()).isNull();
            assertThat(member.displayTitle()).isNull();
        });
        assertThat(snapshot.members().get(9).profileId()).isNull();
        assertThat(snapshot.members().get(9).membershipStatus()).isEqualTo("INVITED");
    }

    @Test
    void membersReadOnlyPublicActiveMembershipsAndProfiles() {
        updateActor("MEMBER", "ACTIVE");
        ProjectMembersSnapshot snapshot = read(PROJECT_ID, ACTOR_PROFILE_ID, 0, 51);
        assertThat(snapshot.actor().role()).isEqualTo("MEMBER");
        assertThat(snapshot.members()).extracting(ProjectMemberSummary::id).containsExactly(id(1), id(2));
    }

    @Test
    void retainsActorForAnEmptyVisiblePage() {
        updateActor("MEMBER", "ACTIVE");
        jdbc.sql("update public.project_members set is_public = false where project_id = :projectId")
                .param("projectId", PROJECT_ID).update();
        ProjectMembersSnapshot snapshot = read(PROJECT_ID, ACTOR_PROFILE_ID, 0, 21);
        assertThat(snapshot.members()).isEmpty();
        assertThat(snapshot.actor().role()).isEqualTo("MEMBER");
    }

    @Test
    void paginatesInStableIdOrderWithoutRowsFromAnotherProject() {
        ProjectMembersSnapshot snapshot = read(PROJECT_ID, ACTOR_PROFILE_ID, 2, 3);
        assertThat(snapshot.members()).extracting(ProjectMemberSummary::id).containsExactly(id(3), id(4), id(5));
        assertThat(read(PROJECT_ID, ACTOR_PROFILE_ID, 50, 21).members()).isEmpty();
    }

    @Test
    void filtersBeforeApplyingTheMemberWindow() {
        updateActor("MEMBER", "ACTIVE");
        assertThat(read(PROJECT_ID, ACTOR_PROFILE_ID, 1, 1).members())
                .extracting(ProjectMemberSummary::id).containsExactly(id(2));
        assertThat(read(PROJECT_ID, ACTOR_PROFILE_ID, 2, 1).members()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"OWNER,INVITED", "OWNER,LEFT", "OWNER,REMOVED",
            "ADMIN,INVITED", "ADMIN,LEFT", "ADMIN,REMOVED",
            "MEMBER,INVITED", "MEMBER,LEFT", "MEMBER,REMOVED"})
    void rejectsEveryInactiveActorRegardlessOfRole(String role, String status) {
        updateActor(role, status);
        assertThat(reader.findAuthorizedPage(PROJECT_ID, ACTOR_PROFILE_ID, 0, 21)).isEmpty();
    }

    @Test
    void rejectsOutsidersUnknownProjectsAndMembershipsOnAnotherProject() {
        assertThat(reader.findAuthorizedPage(PROJECT_ID, id(9999), 0, 21)).isEmpty();
        assertThat(reader.findAuthorizedPage(id(9999), ACTOR_PROFILE_ID, 0, 21)).isEmpty();
        assertThat(reader.findAuthorizedPage(id(200), ACTOR_PROFILE_ID, 0, 21)).isEmpty();
    }

    @Test
    void readsFreshPersistedRoleAndStatusForEveryPage() {
        assertThat(read(PROJECT_ID, ACTOR_PROFILE_ID, 0, 51).members()).hasSize(11);
        updateActor("MEMBER", "ACTIVE");
        assertThat(read(PROJECT_ID, ACTOR_PROFILE_ID, 0, 51).members()).hasSize(2);
        updateActor("MEMBER", "LEFT");
        assertThat(reader.findAuthorizedPage(PROJECT_ID, ACTOR_PROFILE_ID, 0, 21)).isEmpty();
    }

    private ProjectMembersSnapshot read(UUID projectId, UUID profileId, int offset, int limit) {
        return reader.findAuthorizedPage(projectId, profileId, offset, limit).orElseThrow();
    }

    private void updateActor(String role, String status) {
        jdbc.sql("update public.project_members set role = :role, membership_status = :status where id = :id")
                .param("role", role).param("status", status).param("id", id(1)).update();
    }

    private void addMembership(int row, UUID profileId, UUID projectId, String role, String status) {
        jdbc.sql("""
                insert into public.project_members (
                    id, project_id, profile_id, role, membership_status, is_public,
                    display_title, invitee_email, invite_token_digest, created_by_profile_id
                ) values (:id, :projectId, :profileId, :role, :status, true,
                    'Public title', 'fixture@example.test', 'private-digest', :actor)
                """)
                .param("id", id(row)).param("projectId", projectId).param("profileId", profileId)
                .param("role", role).param("status", status).param("actor", ACTOR_PROFILE_ID).update();
    }

    private static List<UUID> ids(int... values) {
        return java.util.Arrays.stream(values).mapToObj(JdbcProjectMembersReaderTest::id).toList();
    }

    private static UUID id(int value) {
        return new UUID(0, value);
    }
}
