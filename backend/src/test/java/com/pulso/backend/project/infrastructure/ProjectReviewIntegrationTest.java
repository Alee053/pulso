package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.project.application.ReviewProjects;
import com.pulso.backend.project.domain.ProjectReviewException;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectReviewIntegrationTest {
    private static final UUID OWNER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MODERATOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID USER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PROJECT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECTOR = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String PATH = "/api/v1/admin/project-reviews";
    private static final String APPROVE = "{\"decision\":\"APPROVE\",\"verificationStatus\":\"VERIFIED\"}";
    private static final String REJECT = "{\"decision\":\"REJECT\",\"note\":\"Completar descripción\"}";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired private JdbcClient jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private MockMvc mvc;
    @Autowired private ReviewProjects reviews;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() throws Exception {
        // This container is disposable. Replay the real schema with only Auth's external seam stubbed.
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    drop schema if exists public cascade;
                    drop schema if exists auth cascade;
                    create schema public;
                    create schema auth;
                    do $$ begin
                      if not exists (select from pg_roles where rolname = 'anon') then create role anon nologin; end if;
                      if not exists (select from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
                    end $$;
                    create table auth.users (id uuid primary key, raw_user_meta_data jsonb default '{}');
                    create function auth.uid() returns uuid language sql stable as
                      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
                    grant usage on schema auth to anon, authenticated;
                    """);
            try (var migrations = Files.list(Path.of("..", "database", "supabase", "migrations"))) {
                for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).sorted().toList()) {
                    statement.execute(Files.readString(migration));
                }
            }
        }
        for (UUID profile : List.of(OWNER, MODERATOR, ADMIN, USER)) {
            jdbc.sql("insert into auth.users (id) values (:id)").param("id", profile).update();
        }
        jdbc.sql("update public.profiles set platform_role = 'MODERATOR' where id = :id").param("id", MODERATOR).update();
        jdbc.sql("update public.profiles set platform_role = 'ADMIN' where id = :id").param("id", ADMIN).update();
        jdbc.sql("insert into public.sectors (id, slug, name) values (:id, 'tecnologia', 'Tecnología')").param("id", SECTOR).update();
        insertProject(PROJECT, "PENDING_REVIEW", OWNER);
    }

    @Test
    void requiresAValidatedUserTokenAndAPersistedActivePlatformRole() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(decisionPath()).contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", token(USER, "service_role"))).andExpect(status().isUnauthorized());
        // The token deliberately claims platformRole ADMIN; the stored USER must still be denied.
        mvc.perform(get(PATH).header("Authorization", token(USER))).andExpect(status().isForbidden());
        mvc.perform(post(decisionPath()).header("Authorization", token(OWNER))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isForbidden());
        jdbc.sql("update public.profiles set account_status = 'SUSPENDED' where id = :id").param("id", MODERATOR).update();
        mvc.perform(get(PATH).header("Authorization", token(MODERATOR))).andExpect(status().isForbidden());
        mvc.perform(post(decisionPath()).header("Authorization", token(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON).content(REJECT)).andExpect(status().isForbidden());
        assertThat(auditCount()).isZero();
    }

    @Test
    void listsOnlyPendingProjectsWithReviewInformationAndBoundedStablePages() throws Exception {
        insertProject(new UUID(0, 2), "DRAFT", OWNER);
        insertProject(new UUID(0, 3), "PENDING_REVIEW", OWNER);
        for (UUID actor : List.of(MODERATOR, ADMIN)) {
            mvc.perform(get(PATH).param("limit", "1").header("Authorization", token(actor)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(PROJECT.toString()))
                    .andExpect(jsonPath("$.data[0].description").value("Descripción para revisar"))
                    .andExpect(jsonPath("$.data[0].valueProposition").value("Valor del proyecto"))
                    .andExpect(jsonPath("$.data[0].sector").value("Tecnología"))
                    .andExpect(jsonPath("$.data[0].activeOwnerCount").value(1))
                    .andExpect(jsonPath("$.data[0].canReview").value(true))
                    .andExpect(jsonPath("$.meta.hasMore").value(true))
                    .andExpect(jsonPath("$.meta.noteMaxLength").value(2000));
        }
        mvc.perform(get(PATH).param("offset", "1").param("limit", "1").header("Authorization", token(ADMIN)))
                .andExpect(jsonPath("$.data[0].id").value(new UUID(0, 3).toString()))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
        for (String query : List.of("limit=0", "limit=51", "offset=-1", "offset=100001", "limit=oops")) {
            mvc.perform(get(PATH + "?" + query).header("Authorization", token(ADMIN))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void approvalPublishesAndRecordsTheSameActorTimestampAndTargetInTheAudit() throws Exception {
        mvc.perform(get("/api/v1/projects/" + PROJECT)).andExpect(status().isNotFound());
        mvc.perform(post(decisionPath()).header("Authorization", token(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.reviewedByProfileId").value(MODERATOR.toString()))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        assertThat(jdbc.sql("""
                select count(*) from public.projects p join public.admin_audit_logs a on a.target_id = p.id
                where p.id = :id and p.status = 'PUBLISHED' and p.verification_status = 'VERIFIED'
                  and a.actor_profile_id = p.reviewed_by_profile_id and a.created_at = p.reviewed_at
                  and p.published_at = p.reviewed_at and a.action = 'PROJECT_APPROVED'
                  and a.target_type = 'PROJECT' and a.metadata->>'toStatus' = 'PUBLISHED'
                """).param("id", PROJECT).query(Integer.class).single()).isEqualTo(1);
        mvc.perform(get(PATH).header("Authorization", token(ADMIN))).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/projects/" + PROJECT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewNote").doesNotExist());
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isConflict());
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void rejectionKeepsTheProjectPrivateAndMakesTheNoteVisibleOnlyToItsActiveTeam() throws Exception {
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(REJECT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
        mvc.perform(get("/api/v1/me/projects").header("Authorization", token(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].reviewNote").value("Completar descripción"))
                .andExpect(jsonPath("$.data[0].reviewedAt").isNotEmpty());
        mvc.perform(get("/api/v1/me/projects").header("Authorization", token(USER)))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/projects/" + PROJECT)).andExpect(status().isNotFound());
        assertThat(jdbc.sql("select action from public.admin_audit_logs").query(String.class).single())
                .isEqualTo("PROJECT_REJECTED");
        assertThat(jdbc.sql("select metadata::text from public.admin_audit_logs").query(String.class).single())
                .doesNotContain("Completar descripción");
    }

    @Test
    void elevatedOwnersAndCreatorsCannotReviewTheirOwnProject() throws Exception {
        jdbc.sql("update public.profiles set platform_role = 'ADMIN' where id = :id").param("id", OWNER).update();
        mvc.perform(get(PATH).header("Authorization", token(OWNER))).andExpect(jsonPath("$.data[0].canReview").value(false));
        for (String decision : List.of(APPROVE, REJECT)) {
            mvc.perform(post(decisionPath()).header("Authorization", token(OWNER))
                    .contentType(MediaType.APPLICATION_JSON).content(decision))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("project_review_own_project"));
        }
        jdbc.sql("update public.project_members set profile_id = :other where project_id = :id")
                .param("other", MODERATOR).param("id", PROJECT).update();
        // The creator is still excluded after ownership changes, and the new owner is also excluded.
        for (UUID actor : List.of(OWNER, MODERATOR)) {
            mvc.perform(post(decisionPath()).header("Authorization", token(actor))
                    .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isForbidden());
        }
        assertThat(auditCount()).isZero();
    }

    @Test
    void rejectsInvalidDecisionsAndClientControlledReviewIdentityOrDates() throws Exception {
        for (String body : List.of("{}", "null", "{\"decision\":\"REJECT\",\"note\":\"  \"}",
                "{\"decision\":\"PUBLISHED\"}", "{\"decision\":\"APPROVE\"}",
                "{\"decision\":\"APPROVE\",\"verificationStatus\":\"REVOKED\"}",
                "{\"decision\":\"REJECT\",\"note\":\"Nota\",\"verificationStatus\":\"VERIFIED\"}",
                "{\"decision\":\"REJECT\",\"note\":\"" + "x".repeat(2001) + "\"}",
                APPROVE.substring(0, APPROVE.length() - 1) + ",\"reviewedByProfileId\":\"" + OWNER + "\"}",
                APPROVE.substring(0, APPROVE.length() - 1) + ",\"reviewedAt\":\"2020-01-01T00:00:00Z\"}")) {
            mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertThat(auditCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED", "REJECTED", "ARCHIVED", "SUSPENDED"})
    void rejectsDecisionsOutsidePendingReview(String currentStatus) throws Exception {
        jdbc.sql("update public.projects set status = :status where id = :id")
                .param("status", currentStatus).param("id", PROJECT).update();
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isConflict());
        assertThat(auditCount()).isZero();
    }

    @Test
    void approvalRequiresAnActiveSectorAndOwner() throws Exception {
        jdbc.sql("update public.sectors set is_active = false where id = :id").param("id", SECTOR).update();
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isConflict());
        jdbc.sql("update public.sectors set is_active = true where id = :id").param("id", SECTOR).update();
        jdbc.sql("update public.project_members set membership_status = 'LEFT' where project_id = :id")
                .param("id", PROJECT).update();
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isConflict());
        assertThat(auditCount()).isZero();
    }

    @Test
    void rollsBackPublicationWhenTheAuditCannotBeWritten() throws Exception {
        jdbc.sql("""
                create function public.fail_review_audit() returns trigger language plpgsql as
                $$ begin raise exception 'Audit storage unavailable'; end $$;
                create trigger fail_review_audit before insert on public.admin_audit_logs
                  for each row execute function public.fail_review_audit();
                """).update();
        mvc.perform(post(decisionPath()).header("Authorization", token(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(APPROVE)).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.sql("select status from public.projects where id = :id").param("id", PROJECT)
                .query(String.class).single()).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.sql("select count(*) from public.projects where id = :id and reviewed_at is not null")
                .param("id", PROJECT).query(Integer.class).single()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void concurrentDecisionsCommitExactlyOneOutcomeAndOneAuditEntry() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approve = executor.submit(() -> concurrentDecision(start, MODERATOR, "APPROVE"));
            var reject = executor.submit(() -> concurrentDecision(start, ADMIN, "REJECT"));
            start.countDown();
            assertThat(List.of(approve.get(10, TimeUnit.SECONDS), reject.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMMITTED", "STATE_CHANGED");
        }
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void teamEndpointsCannotPublishOrSetVerificationDirectly() throws Exception {
        jdbc.sql("update public.projects set status = 'DRAFT' where id = :id").param("id", PROJECT).update();
        mvc.perform(post("/api/v1/projects/" + PROJECT + "/publication-request")
                .header("Authorization", token(OWNER)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/projects/" + PROJECT).header("Authorization", token(OWNER))
                .contentType(MediaType.APPLICATION_JSON).content("{\"verificationStatus\":\"VERIFIED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/projects/" + PROJECT + "/publication-request").header("Authorization", token(OWNER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditStorageIsBackendOnly() {
        assertThat(jdbc.sql("select relrowsecurity from pg_class where oid = 'public.admin_audit_logs'::regclass")
                .query(Boolean.class).single()).isTrue();
        for (String role : List.of("anon", "authenticated")) {
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
                assertThat(jdbc.sql("select has_table_privilege(:role, 'public.admin_audit_logs', :privilege)")
                        .param("role", role).param("privilege", privilege).query(Boolean.class).single()).isFalse();
            }
        }
    }

    private String concurrentDecision(CountDownLatch start, UUID actor, String decision) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try {
            reviews.decide(PROJECT, actor, decision, "APPROVE".equals(decision) ? "VERIFIED" : null,
                    "REJECT".equals(decision) ? "Completar información" : null);
            return "COMMITTED";
        } catch (ProjectReviewException exception) {
            return exception.reason().name();
        }
    }

    private void insertProject(UUID id, String projectStatus, UUID creator) {
        jdbc.sql("""
                insert into public.projects (id, slug, name, tagline, description, value_proposition,
                    sector_id, city, stage, status, created_by_profile_id)
                values (:id, :slug, 'Proyecto', 'Propuesta', 'Descripción para revisar', 'Valor del proyecto',
                    :sector, 'La Paz', 'MVP', :status, :creator)
                """).param("id", id).param("slug", "project-" + id).param("sector", SECTOR)
                .param("status", projectStatus).param("creator", creator).update();
        jdbc.sql("""
                insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
                values (:project, :profile, 'OWNER', 'ACTIVE', :profile)
                """).param("project", id).param("profile", creator).update();
    }

    private int auditCount() {
        return jdbc.sql("select count(*) from public.admin_audit_logs").query(Integer.class).single();
    }

    private static String decisionPath() { return PATH + "/" + PROJECT + "/decision"; }

    private static String token(UUID profile) throws Exception { return token(profile, "authenticated"); }

    private static String token(UUID profile, String role) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(profile.toString()).issuer("http://test.supabase.local/auth/v1")
                .audience("authenticated").issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(300))).claim("role", role)
                .claim("platformRole", "ADMIN").build());
        jwt.sign(new MACSigner("test-secret-that-is-long-enough-for-local-tests"));
        return "Bearer " + jwt.serialize();
    }
}
