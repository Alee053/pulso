package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

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

import com.pulso.backend.project.domain.ProjectUpdatePatch;
import com.pulso.backend.project.domain.UpdatedProject;

/**
 * Tests backend-owned JDBC writes and persisted membership in isolated PostgreSQL.
 * The minimal schema models the relevant Supabase columns and timestamp trigger;
 * it does not validate deployed migrations, RLS, or direct Angular database access.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectUpdateRepositoryTest {

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
    private static final UUID INVITED_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID LEFT_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");
    private static final UUID REMOVED_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID EXTERNAL_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACTIVE_SECTOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID INACTIVE_SECTOR_ID = UUID.fromString(
            "aaaaaaaa-1111-1111-1111-111111111111");
    private static final UUID SECOND_ACTIVE_SECTOR_ID = UUID.fromString(
            "cccccccc-2222-2222-2222-222222222222");
    private static final UUID REVIEWER_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse(
            "2026-09-01T12:00:00Z");
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse(
            "2026-09-02T12:00:00Z");
    private static final OffsetDateTime REVIEWED_AT = OffsetDateTime.parse(
            "2026-09-03T12:00:00Z");
    private static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse(
            "2026-09-04T12:00:00Z");
    private static final OffsetDateTime ARCHIVED_AT = OffsetDateTime.parse(
            "2026-09-05T12:00:00Z");

    private static final ProjectUpdatePatch.Limits LIMITS = new ProjectUpdatePatch.Limits(
            160,
            240,
            5000,
            120,
            40);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProjectRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.project_members, public.projects, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("drop function if exists public.set_updated_at() cascade").update();
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
                  id uuid primary key,
                  slug text not null unique,
                  name text not null,
                  tagline text not null,
                  description text,
                  value_proposition text,
                  sector_id uuid not null references public.sectors (id),
                  stage text not null,
                  city text not null,
                  department text,
                  status text not null,
                  verification_status text not null,
                  created_by_profile_id uuid references public.profiles (id),
                  reviewed_by_profile_id uuid references public.profiles (id),
                  review_note text,
                  reviewed_at timestamptz,
                  published_at timestamptz,
                  created_at timestamptz not null,
                  updated_at timestamptz not null,
                  archived_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  role text not null,
                  membership_status text not null
                )
                """).update();
        jdbcClient.sql("""
                create function public.set_updated_at()
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
                create trigger projects_set_updated_at
                before update on public.projects
                for each row execute function public.set_updated_at()
                """).update();
        jdbcClient.sql("""
                insert into public.profiles (id) values
                  (:ownerId), (:adminId), (:memberId), (:invitedId),
                  (:leftId), (:removedId), (:reviewerId)
                """)
                .param("ownerId", OWNER_ID)
                .param("adminId", ADMIN_ID)
                .param("memberId", MEMBER_ID)
                .param("invitedId", INVITED_ID)
                .param("leftId", LEFT_ID)
                .param("removedId", REMOVED_ID)
                .param("reviewerId", REVIEWER_ID)
                .update();
        jdbcClient.sql("""
                insert into public.sectors (id, is_active) values
                  (:activeSectorId, true),
                  (:secondActiveSectorId, true),
                  (:inactiveSectorId, false)
                """)
                .param("activeSectorId", ACTIVE_SECTOR_ID)
                .param("secondActiveSectorId", SECOND_ACTIVE_SECTOR_ID)
                .param("inactiveSectorId", INACTIVE_SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, description, value_proposition,
                  sector_id, stage, city, department, status, verification_status,
                  created_by_profile_id, reviewed_by_profile_id, review_note,
                  reviewed_at, published_at, created_at, updated_at, archived_at
                ) values (
                  :projectId, 'eco-pack', 'EcoPack Bolivia', 'Envases sostenibles',
                  'Descripción original', 'Valor original', :sectorId, 'MVP',
                  'Cochabamba', 'Cochabamba', 'PUBLISHED', 'VERIFIED',
                  :ownerId, :reviewerId, 'Aprobado por revisión', :reviewedAt,
                  :publishedAt, :createdAt, :updatedAt, :archivedAt
                ), (
                  :otherProjectId, 'other-project', 'Otro proyecto', 'Otro tagline',
                  'Otra descripción', 'Otra propuesta', :sectorId, 'IDEA',
                  'La Paz', 'La Paz', 'DRAFT', 'UNVERIFIED',
                  :ownerId, null, null, null, null, :createdAt, :updatedAt, null
                )
                """)
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .param("sectorId", ACTIVE_SECTOR_ID)
                .param("ownerId", OWNER_ID)
                .param("reviewerId", REVIEWER_ID)
                .param("reviewedAt", REVIEWED_AT)
                .param("publishedAt", PUBLISHED_AT)
                .param("createdAt", CREATED_AT)
                .param("updatedAt", UPDATED_AT)
                .param("archivedAt", ARCHIVED_AT)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (id, project_id, profile_id, role, membership_status) values
                  ('10000000-0000-0000-0000-000000000001', :projectId, :ownerId, 'OWNER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000002', :projectId, :adminId, 'ADMIN', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000003', :projectId, :memberId, 'MEMBER', 'ACTIVE'),
                  ('10000000-0000-0000-0000-000000000004', :projectId, :invitedId, 'ADMIN', 'INVITED'),
                  ('10000000-0000-0000-0000-000000000005', :projectId, :leftId, 'OWNER', 'LEFT'),
                  ('10000000-0000-0000-0000-000000000006', :projectId, :removedId, 'ADMIN', 'REMOVED'),
                  ('10000000-0000-0000-0000-000000000007', :otherProjectId, :memberId, 'OWNER', 'ACTIVE')
                """)
                .param("projectId", PROJECT_ID)
                .param("otherProjectId", OTHER_PROJECT_ID)
                .param("ownerId", OWNER_ID)
                .param("adminId", ADMIN_ID)
                .param("memberId", MEMBER_ID)
                .param("invitedId", INVITED_ID)
                .param("leftId", LEFT_ID)
                .param("removedId", REMOVED_ID)
                .update();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11111111-1111-1111-1111-111111111111",
            "55555555-5555-5555-5555-555555555555"
    })
    void updatesAllEditableFieldsAndPreservesServerManagedProjectData(UUID profileId) {
        ProjectRow before = project(PROJECT_ID);
        ProjectRow otherProjectBefore = project(OTHER_PROJECT_ID);
        List<MemberRow> membersBefore = members();
        assertThat(repository.hasActiveSector(SECOND_ACTIVE_SECTOR_ID)).isTrue();

        UpdatedProject updated = repository.updateInformation(
                PROJECT_ID,
                profileId,
                patch()).orElseThrow();

        assertThat(updated.id()).isEqualTo(before.id());
        assertThat(updated.slug()).isEqualTo(before.slug());
        assertThat(updated.name()).isEqualTo("EcoPack actualizado");
        assertThat(updated.tagline()).isEqualTo("Envases nuevos");
        assertThat(updated.description()).isEqualTo("Descripción nueva");
        assertThat(updated.valueProposition()).isEqualTo("Menos residuos");
        assertThat(updated.sectorId()).isEqualTo(SECOND_ACTIVE_SECTOR_ID);
        assertThat(updated.stage()).isEqualTo("EARLY_STAGE");
        assertThat(updated.city()).isEqualTo("Santa Cruz");
        assertThat(updated.department()).isEqualTo("Santa Cruz");
        assertThat(updated.status()).isEqualTo(before.status());
        assertThat(updated.verificationStatus()).isEqualTo(before.verificationStatus());
        assertThat(updated.createdByProfileId()).isEqualTo(before.createdByProfileId());
        assertThat(updated.reviewedByProfileId()).isEqualTo(before.reviewedByProfileId());
        assertThat(updated.reviewNote()).isEqualTo(before.reviewNote());
        assertThat(updated.reviewedAt()).isEqualTo(before.reviewedAt());
        assertThat(updated.publishedAt()).isEqualTo(before.publishedAt());
        assertThat(updated.createdAt()).isEqualTo(before.createdAt());
        assertThat(updated.archivedAt()).isEqualTo(before.archivedAt());
        assertThat(updated.updatedAt()).isAfter(before.updatedAt());

        ProjectRow persisted = project(PROJECT_ID);
        assertThat(persisted).usingRecursiveComparison().isEqualTo(updated);
        assertThat(project(OTHER_PROJECT_ID)).isEqualTo(otherProjectBefore);
        assertThat(members()).isEqualTo(membersBefore);
    }

    @Test
    void permitsAnActiveAdminAndPreservesOmittedColumns() {
        ProjectRow before = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();
        UpdatedProject updated = repository.updateInformation(
                PROJECT_ID,
                ADMIN_ID,
                new ProjectUpdatePatch(
                        "Solo nombre",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LIMITS)).orElseThrow();

        assertThat(updated.name()).isEqualTo("Solo nombre");
        assertThat(updated.tagline()).isEqualTo("Envases sostenibles");
        assertThat(updated.description()).isEqualTo("Descripción original");
        assertThat(updated.valueProposition()).isEqualTo("Valor original");
        assertThat(updated.stage()).isEqualTo("MVP");
        assertThat(updated.city()).isEqualTo("Cochabamba");
        assertThat(updated.department()).isEqualTo("Cochabamba");
        assertThat(project(PROJECT_ID)).usingRecursiveComparison()
                .ignoringFields("name", "updatedAt")
                .isEqualTo(before);
        assertThat(project(PROJECT_ID).updatedAt()).isAfter(before.updatedAt());
        assertThat(members()).isEqualTo(membersBefore);
    }

    @Test
    void clearsNullableEditableFieldsOnlyWhenTheyAreExplicitlyProvidedAsNull() {
        ProjectRow before = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();
        UpdatedProject updated = repository.updateInformation(
                PROJECT_ID,
                OWNER_ID,
                new ProjectUpdatePatch(
                        null,
                        false,
                        null,
                        false,
                        null,
                        true,
                        null,
                        true,
                        null,
                        false,
                        null,
                        false,
                        null,
                        false,
                        null,
                        true,
                        LIMITS)).orElseThrow();

        assertThat(updated.description()).isNull();
        assertThat(updated.valueProposition()).isNull();
        assertThat(updated.department()).isNull();
        assertThat(updated.city()).isEqualTo("Cochabamba");
        assertThat(updated.name()).isEqualTo("EcoPack Bolivia");
        assertThat(project(PROJECT_ID)).usingRecursiveComparison().isEqualTo(updated);
        assertThat(project(PROJECT_ID)).usingRecursiveComparison()
                .ignoringFields("description", "valueProposition", "department", "updatedAt")
                .isEqualTo(before);
        assertThat(updated.updatedAt()).isAfter(before.updatedAt());
        assertThat(members()).isEqualTo(membersBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "66666666-6666-6666-6666-666666666666",
            "77777777-7777-7777-7777-777777777777",
            "88888888-8888-8888-8888-888888888888",
            "99999999-9999-9999-9999-999999999999",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    })
    void rejectsMemberInactiveOrExternalProfiles(UUID profileId) {
        ProjectRow before = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();

        assertThat(repository.updateInformation(PROJECT_ID, profileId, patch())).isEmpty();
        assertThat(project(PROJECT_ID)).isEqualTo(before);
        assertThat(members()).isEqualTo(membersBefore);
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER,INVITED", "OWNER,LEFT", "OWNER,REMOVED",
            "ADMIN,INVITED", "ADMIN,LEFT", "ADMIN,REMOVED"
    })
    void rejectsEveryInactivePrivilegedMembership(String role, String membershipStatus) {
        jdbcClient.sql("""
                update public.project_members
                set role = :role, membership_status = :membershipStatus
                where project_id = :projectId and profile_id = :profileId
                """)
                .param("role", role)
                .param("membershipStatus", membershipStatus)
                .param("projectId", PROJECT_ID)
                .param("profileId", ADMIN_ID)
                .update();
        ProjectRow before = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();

        assertThat(repository.updateInformation(PROJECT_ID, ADMIN_ID, patch())).isEmpty();
        assertThat(project(PROJECT_ID)).isEqualTo(before);
        assertThat(members()).isEqualTo(membersBefore);
    }

    @Test
    void requiresTheRequestedProjectMembershipAndDoesNotUpdateAnotherProject() {
        ProjectRow before = project(OTHER_PROJECT_ID);
        ProjectRow authorizedProjectBefore = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();

        assertThat(repository.updateInformation(OTHER_PROJECT_ID, OWNER_ID, patch())).isEmpty();
        assertThat(project(OTHER_PROJECT_ID)).isEqualTo(before);
        assertThat(project(PROJECT_ID)).isEqualTo(authorizedProjectBefore);
        assertThat(members()).isEqualTo(membersBefore);
    }

    @Test
    void treatsAnUnknownProjectAsAnAuthorizationFailureWithoutInsertingRows() {
        UUID unknownProjectId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        int projectsBefore = count("public.projects");
        ProjectRow before = project(PROJECT_ID);
        ProjectRow otherProjectBefore = project(OTHER_PROJECT_ID);
        List<MemberRow> membersBefore = members();

        assertThat(repository.updateInformation(unknownProjectId, OWNER_ID, patch())).isEmpty();
        assertThat(count("public.projects")).isEqualTo(projectsBefore);
        assertThat(project(PROJECT_ID)).isEqualTo(before);
        assertThat(project(OTHER_PROJECT_ID)).isEqualTo(otherProjectBefore);
        assertThat(members()).isEqualTo(membersBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aaaaaaaa-1111-1111-1111-111111111111",
            "dddddddd-3333-3333-3333-333333333333"
    })
    void refusesInactiveOrNonexistentSectorsWithoutChangingTheProject(UUID sectorId) {
        ProjectRow before = project(PROJECT_ID);
        List<MemberRow> membersBefore = members();
        ProjectUpdatePatch invalidSectorPatch = new ProjectUpdatePatch(
                "No debe persistir",
                null,
                null,
                null,
                null,
                sectorId,
                null,
                null,
                LIMITS);

        assertThat(repository.hasActiveSector(sectorId)).isFalse();
        assertThat(repository.updateInformation(PROJECT_ID, OWNER_ID, invalidSectorPatch)).isEmpty();
        assertThat(project(PROJECT_ID)).isEqualTo(before);
        assertThat(members()).isEqualTo(membersBefore);
    }

    private static ProjectUpdatePatch patch() {
        return new ProjectUpdatePatch(
                "EcoPack actualizado",
                "Envases nuevos",
                "Descripción nueva",
                "Menos residuos",
                "EARLY_STAGE",
                SECOND_ACTIVE_SECTOR_ID,
                "Santa Cruz",
                "Santa Cruz",
                LIMITS);
    }

    private ProjectRow project(UUID projectId) {
        return jdbcClient.sql("""
                select id, slug, name, tagline, description, value_proposition, sector_id,
                       stage, city, department, status, verification_status,
                       created_by_profile_id, reviewed_by_profile_id, review_note,
                       reviewed_at, published_at, created_at, updated_at, archived_at
                from public.projects
                where id = :projectId
                """)
                .param("projectId", projectId)
                .query(JdbcProjectUpdateRepositoryTest::mapProject)
                .single();
    }

    private List<MemberRow> members() {
        return jdbcClient.sql("""
                select id, project_id, profile_id, role, membership_status
                from public.project_members
                order by id
                """)
                .query((resultSet, rowNumber) -> new MemberRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("project_id", UUID.class),
                        resultSet.getObject("profile_id", UUID.class),
                        resultSet.getString("role"),
                        resultSet.getString("membership_status")))
                .list();
    }

    private int count(String table) {
        return jdbcClient.sql("select count(*) from " + table)
                .query((resultSet, rowNumber) -> resultSet.getInt(1))
                .single();
    }

    private static ProjectRow mapProject(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("name"),
                resultSet.getString("tagline"),
                resultSet.getString("description"),
                resultSet.getString("value_proposition"),
                resultSet.getObject("sector_id", UUID.class),
                resultSet.getString("stage"),
                resultSet.getString("city"),
                resultSet.getString("department"),
                resultSet.getString("status"),
                resultSet.getString("verification_status"),
                resultSet.getObject("created_by_profile_id", UUID.class),
                resultSet.getObject("reviewed_by_profile_id", UUID.class),
                resultSet.getString("review_note"),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("archived_at", OffsetDateTime.class));
    }

    private record MemberRow(
            UUID id,
            UUID projectId,
            UUID profileId,
            String role,
            String membershipStatus) {
    }

    private record ProjectRow(
            UUID id,
            String slug,
            String name,
            String tagline,
            String description,
            String valueProposition,
            UUID sectorId,
            String stage,
            String city,
            String department,
            String status,
            String verificationStatus,
            UUID createdByProfileId,
            UUID reviewedByProfileId,
            String reviewNote,
            OffsetDateTime reviewedAt,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime archivedAt) {
    }
}
