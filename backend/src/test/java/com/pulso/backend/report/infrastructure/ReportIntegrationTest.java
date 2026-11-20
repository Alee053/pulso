package com.pulso.backend.report.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ReportIntegrationTest {
    private static final UUID REPORTER = new UUID(0, 1);
    private static final UUID PROFILE = new UUID(0, 2);
    private static final UUID OUTSIDER = new UUID(0, 3);
    private static final UUID SECTOR = new UUID(0, 4);
    private static final UUID PROJECT = new UUID(0, 5);
    private static final UUID CONVERSATION = new UUID(0, 6);
    private static final UUID MESSAGE = new UUID(0, 7);
    private static final String PATH = "/api/v1/reports";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void seedDisposableDatabase(@Autowired DataSource dataSource, @Autowired JdbcClient jdbc) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
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
                for (var migration : migrations.filter(path -> path.toString().endsWith(".sql")).sorted().toList()) {
                    statement.execute(Files.readString(migration));
                }
            }
        }
        jdbc.sql("insert into auth.users (id) values (:reporter), (:profile), (:outsider)")
                .param("reporter", REPORTER).param("profile", PROFILE).param("outsider", OUTSIDER).update();
        jdbc.sql("insert into public.sectors (id, slug, name) values (:id, 'test-sector', 'Test sector')")
                .param("id", SECTOR).update();
        jdbc.sql("""
                insert into public.projects (id, slug, name, tagline, sector_id, city, stage, status, created_by_profile_id)
                values (:id, 'test-project', 'Test project', 'Test tagline', :sector, 'La Paz', 'MVP', 'PUBLISHED', :profile)
                """).param("id", PROJECT).param("sector", SECTOR).param("profile", PROFILE).update();
        jdbc.sql("""
                insert into public.conversations (id, conversation_type, created_by_profile_id)
                values (:id, 'DIRECT', :creator)
                """).param("id", CONVERSATION).param("creator", REPORTER).update();
        jdbc.sql("""
                insert into public.conversation_participants (conversation_id, profile_id)
                values (:conversation, :reporter), (:conversation, :profile)
                """).param("conversation", CONVERSATION).param("reporter", REPORTER).param("profile", PROFILE).update();
        jdbc.sql("""
                insert into public.messages (id, conversation_id, sender_profile_id, body)
                values (:id, :conversation, :sender, 'Private text')
                """).param("id", MESSAGE).param("conversation", CONVERSATION).param("sender", PROFILE).update();
    }

    @Test
    void storesAllThreeTargetsAndKeepsTheModerationQueuePrivate() throws Exception {
        submit(REPORTER, "targetProfileId", PROFILE, "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
        submit(REPORTER, "targetProjectId", PROJECT, "").andExpect(status().isCreated());
        submit(REPORTER, "targetMessageId", MESSAGE, ",\"description\":\" Context \"")
                .andExpect(status().isCreated());

        assertThat(jdbc.sql("""
                select coalesce(target_profile_id, target_project_id, target_message_id)
                from public.reports where reporter_profile_id = :reporter and reason = 'ABUSE' and status = 'OPEN'
                """).param("reporter", REPORTER).query(UUID.class).list()).containsExactlyInAnyOrder(PROFILE, PROJECT, MESSAGE);
        assertThat(jdbc.sql("select count(*) from public.reports where description is null").query(Integer.class).single())
                .isEqualTo(2);
        assertThat(jdbc.sql("select description from public.reports where target_message_id = :message")
                .param("message", MESSAGE).query(String.class).single()).isEqualTo("Context");

        mvc.perform(get("/api/v1/admin/project-reviews")
                        .with(jwt().jwt(token -> token.subject(REPORTER.toString()).claim("platformRole", "ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingAndInaccessibleTargetsHaveTheSameResponse() throws Exception {
        jdbc.sql("update public.profiles set profile_visibility = 'PRIVATE' where id = :id").param("id", PROFILE).update();
        jdbc.sql("update public.projects set status = 'DRAFT' where id = :id").param("id", PROJECT).update();
        submit(REPORTER, "targetProfileId", PROFILE, "").andExpect(status().isNotFound());
        submit(REPORTER, "targetProjectId", PROJECT, "").andExpect(status().isNotFound());
        submit(OUTSIDER, "targetMessageId", MESSAGE, "").andExpect(status().isNotFound());
        for (String field : new String[]{"targetProfileId", "targetProjectId", "targetMessageId"}) {
            submit(REPORTER, field, new UUID(0, 99), "").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Report target is unavailable."));
        }
        assertThat(jdbc.sql("select count(*) from public.reports").query(Integer.class).single()).isZero();
    }

    @Test
    void acceptsAProjectVisibleToItsActiveTeamAndDeniesAFormerParticipant() throws Exception {
        jdbc.sql("update public.projects set status = 'DRAFT' where id = :id").param("id", PROJECT).update();
        jdbc.sql("""
                insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
                values (:project, :profile, 'OWNER', 'ACTIVE', :profile)
                """).param("project", PROJECT).param("profile", PROFILE).update();
        submit(PROFILE, "targetProjectId", PROJECT, "").andExpect(status().isCreated());

        jdbc.sql("""
                update public.conversation_participants set left_at = now()
                where conversation_id = :conversation and profile_id = :reporter
                """).param("conversation", CONVERSATION).param("reporter", REPORTER).update();
        submit(REPORTER, "targetMessageId", MESSAGE, "").andExpect(status().isNotFound());
    }

    private ResultActions submit(UUID actor, String field, UUID target, String extra) throws Exception {
        return mvc.perform(post(PATH).with(jwt().jwt(token -> token.subject(actor.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"%s\":\"%s\",\"reason\":\"ABUSE\"%s}".formatted(field, target, extra)));
    }
}
