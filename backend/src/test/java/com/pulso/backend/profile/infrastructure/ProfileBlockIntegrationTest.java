package com.pulso.backend.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.connection.application.ConnectionIntentRejectedException;
import com.pulso.backend.connection.application.CreateConnectionIntent;
import com.pulso.backend.profile.application.ManageProfileBlocks;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ProfileBlockIntegrationTest {

    private static final UUID SENDER = new UUID(0, 1);
    private static final UUID OWNER = new UUID(0, 2);
    private static final UUID ADMIN = new UUID(0, 3);
    private static final UUID SECTOR = new UUID(0, 4);
    private static final UUID PROJECT = new UUID(0, 5);
    private static final UUID SENDER_PROJECT = new UUID(0, 6);
    private static final UUID CONVERSATION = new UUID(0, 7);
    private static final UUID OPPORTUNITY = new UUID(0, 8);
    private static final String BLOCK = "/api/v1/profiles/{profileId}/block";
    private static final String LIST = "/api/v1/me/profile-blocks";
    private static final String PENDING = "/api/v1/connection-intents/pending";
    private static final String MESSAGES = "/api/v1/conversations/{conversationId}/messages";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ManageProfileBlocks profileBlocks;
    @Autowired private CreateConnectionIntent createConnectionIntent;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void seedDatabase(@Autowired DataSource dataSource, @Autowired JdbcClient jdbc) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    create schema auth;
                    create role anon nologin;
                    create role authenticated nologin;
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
        jdbc.sql("""
                insert into auth.users (id, raw_user_meta_data) values
                  (:sender, '{"display_name":"Sender"}'),
                  (:owner, '{"display_name":"Project owner"}'),
                  (:admin, '{"display_name":"Team admin"}')
                """).param("sender", SENDER).param("owner", OWNER).param("admin", ADMIN).update();
        jdbc.sql("insert into public.sectors (id, slug, name) values (:id, 'contact', 'Contact')")
                .param("id", SECTOR).update();
        jdbc.sql("""
                insert into public.projects (id, slug, name, tagline, sector_id, city, stage, status, created_by_profile_id)
                values (:project, 'contact-project', 'Contact', 'Team contact', :sector, 'La Paz', 'MVP', 'PUBLISHED', :owner),
                       (:senderProject, 'sender-project', 'Sender project', 'Team contact', :sector, 'La Paz', 'MVP', 'PUBLISHED', :sender)
                """).param("project", PROJECT).param("senderProject", SENDER_PROJECT).param("sector", SECTOR)
                .param("owner", OWNER).param("sender", SENDER).update();
        jdbc.sql("""
                insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
                values (:project, :owner, 'OWNER', 'ACTIVE', :owner),
                       (:senderProject, :sender, 'OWNER', 'ACTIVE', :sender)
                """).param("project", PROJECT).param("senderProject", SENDER_PROJECT)
                .param("owner", OWNER).param("sender", SENDER).update();
        jdbc.sql("""
                insert into public.project_opportunities (
                    id, project_id, opportunity_type, status, title, description, created_by_profile_id
                ) values (:id, :project, 'COLLABORATION', 'OPEN', 'Join the team', 'A test opportunity', :owner)
                """).param("id", OPPORTUNITY).param("project", PROJECT).param("owner", OWNER).update();
        jdbc.sql("""
                insert into public.conversations (id, conversation_type, created_by_profile_id)
                values (:id, 'DIRECT', :sender)
                """).param("id", CONVERSATION).param("sender", SENDER).update();
        jdbc.sql("""
                insert into public.conversation_participants (conversation_id, profile_id)
                values (:id, :sender), (:id, :owner)
                """).param("id", CONVERSATION).param("sender", SENDER).param("owner", OWNER).update();
    }

    @Test
    void keepsBlocksPrivateDirectionalAndIdempotent() throws Exception {
        block(SENDER, OWNER).andExpect(status().isNoContent());
        block(SENDER, OWNER).andExpect(status().isNoContent());
        block(SENDER, ADMIN).andExpect(status().isNoContent());
        jdbc.sql("update public.profiles set profile_visibility = 'PRIVATE' where id = :id")
                .param("id", ADMIN).update();

        mvc.perform(get(LIST).queryParam("limit", "1").with(actor(SENDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].profileId").value(OWNER.toString()))
                .andExpect(jsonPath("$.data[0].displayName").value("Project owner"))
                .andExpect(jsonPath("$.meta.hasMore").value(true));
        mvc.perform(get(LIST).queryParam("limit", "1").queryParam("offset", "1").with(actor(SENDER)))
                .andExpect(jsonPath("$.data[0].profileId").value(ADMIN.toString()))
                .andExpect(jsonPath("$.data[0].displayName").value(nullValue()))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
        mvc.perform(get(LIST).with(actor(OWNER))).andExpect(jsonPath("$.data").isEmpty());

        unblock(OWNER, SENDER).andExpect(status().isNoContent());
        assertThat(blockedTargets(SENDER)).containsExactlyInAnyOrder(OWNER, ADMIN);
        unblock(SENDER, OWNER).andExpect(status().isNoContent());
        unblock(SENDER, OWNER).andExpect(status().isNoContent());
        assertThat(blockedTargets(SENDER)).containsExactly(ADMIN);
    }

    @Test
    void checksAuthenticationAndValidatesInput() throws Exception {
        mvc.perform(post(BLOCK, OWNER)).andExpect(status().isUnauthorized());
        mvc.perform(delete(BLOCK, OWNER)).andExpect(status().isUnauthorized());
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(get(PENDING)).andExpect(status().isUnauthorized());
        mvc.perform(post(BLOCK, OWNER).with(jwt().jwt(token -> token.subject("0-0-0-0-1"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BLOCK, "invalid").with(actor(SENDER))).andExpect(status().isBadRequest());
        block(SENDER, SENDER).andExpect(status().isBadRequest());
        block(SENDER, new UUID(0, 99)).andExpect(status().isNotFound());
        mvc.perform(get(LIST).queryParam("limit", "51").with(actor(SENDER))).andExpect(status().isBadRequest());
        assertThat(blockedTargets(SENDER)).isEmpty();
    }

    @Test
    void stopsNewRequestsAndMessagesInBothDirectionsUntilAllBlocksAreRemoved() throws Exception {
        block(SENDER, OWNER).andExpect(status().isNoContent());
        request(SENDER, PROJECT, OPPORTUNITY).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("connection_intent_forbidden"));
        request(OWNER, SENDER_PROJECT, null).andExpect(status().isForbidden());
        request(SENDER, PROJECT, new UUID(0, 99)).andExpect(status().isNotFound());
        message(SENDER).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("messaging_blocked"));
        message(OWNER).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("messaging_blocked"));
        mvc.perform(get(MESSAGES, CONVERSATION).with(actor(SENDER))).andExpect(status().isOk());
        assertThat(jdbc.sql("select count(*) from public.notifications").query(Integer.class).single()).isZero();

        block(OWNER, SENDER).andExpect(status().isNoContent());
        unblock(SENDER, OWNER).andExpect(status().isNoContent());
        request(SENDER, PROJECT, null).andExpect(status().isForbidden());
        message(SENDER).andExpect(status().isForbidden());
        unblock(OWNER, SENDER).andExpect(status().isNoContent());
        request(SENDER, PROJECT, OPPORTUNITY).andExpect(status().isCreated());
        request(OWNER, SENDER_PROJECT, null).andExpect(status().isCreated());
        message(SENDER).andExpect(status().isCreated());
        message(OWNER).andExpect(status().isCreated());
    }

    @Test
    void keepsPendingRequestsAvailableForUnblockedTeamMembers() throws Exception {
        jdbc.sql("""
                insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
                values (:project, :admin, 'ADMIN', 'ACTIVE', :owner)
                """).param("project", PROJECT).param("admin", ADMIN).param("owner", OWNER).update();
        block(OWNER, SENDER).andExpect(status().isNoContent());
        request(SENDER, PROJECT, null).andExpect(status().isCreated());
        UUID intent = jdbc.sql("select id from public.connection_intents").query(UUID.class).single();
        assertThat(jdbc.sql("select recipient_profile_id from public.notifications").query(UUID.class).list())
                .containsExactly(ADMIN);
        mvc.perform(get(PENDING).with(actor(OWNER))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(PENDING).with(actor(ADMIN))).andExpect(jsonPath("$[0].senderProfileId").value(SENDER.toString()));
        mvc.perform(get(PENDING).queryParam("offset", "1").with(actor(ADMIN))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/v1/connection-intents/{id}/accept", intent).with(actor(OWNER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/connection-intents/{id}/decline", intent).with(actor(OWNER)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.sql("select status from public.connection_intents").query(String.class).single())
                .isEqualTo("PENDING");
        unblock(OWNER, SENDER).andExpect(status().isNoContent());
        mvc.perform(get(PENDING).with(actor(OWNER))).andExpect(jsonPath("$[0].id").value(intent.toString()));
        mvc.perform(post("/api/v1/connection-intents/{id}/accept", intent).with(actor(ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requestWaitsForAConcurrentBlockAndChecksTheCommittedState() throws Exception {
        CountDownLatch blockWritten = new CountDownLatch(1);
        CountDownLatch mayCommit = new CountDownLatch(1);
        var transactions = new TransactionTemplate(transactionManager);
        var workers = Executors.newFixedThreadPool(2);
        var block = workers.submit(() -> transactions.executeWithoutResult(status -> {
            profileBlocks.block(OWNER, SENDER);
            blockWritten.countDown();
            try {
                if (!mayCommit.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to commit the block");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while committing the block", exception);
            }
        }));

        try {
            assertThat(blockWritten.await(10, TimeUnit.SECONDS)).isTrue();
            var request = workers.submit(() -> transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertThatThrownBy(() -> createConnectionIntent.execute(PROJECT, SENDER, null, "OTHER", null))
                        .isInstanceOfSatisfying(ConnectionIntentRejectedException.class,
                                rejection -> assertThat(rejection.reason())
                                        .isEqualTo(ConnectionIntentRejectedException.Reason.RECIPIENTS_BLOCKED));
            }));
            assertThatThrownBy(() -> request.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            mayCommit.countDown();
            request.get(10, TimeUnit.SECONDS);
        } finally {
            mayCommit.countDown();
            try {
                block.get(10, TimeUnit.SECONDS);
                profileBlocks.unblock(OWNER, SENDER);
            } finally {
                workers.shutdownNow();
            }
        }
    }

    private ResultActions block(UUID blocker, UUID target) throws Exception {
        return mvc.perform(post(BLOCK, target).with(actor(blocker)));
    }

    private ResultActions unblock(UUID blocker, UUID target) throws Exception {
        return mvc.perform(delete(BLOCK, target).with(actor(blocker)));
    }

    private List<UUID> blockedTargets(UUID blocker) {
        return jdbc.sql("select blocked_profile_id from public.profile_blocks where blocker_profile_id = :blocker")
                .param("blocker", blocker).query(UUID.class).list();
    }

    private ResultActions request(UUID sender, UUID project, UUID opportunity) throws Exception {
        String body = opportunity == null
                ? "{\"reason\":\"OTHER\"}"
                : "{\"reason\":\"OTHER\",\"opportunityId\":\"%s\"}".formatted(opportunity);
        return mvc.perform(post("/api/v1/projects/{projectId}/connection-intents", project)
                .with(actor(sender)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions message(UUID sender) throws Exception {
        return mvc.perform(post(MESSAGES, CONVERSATION).with(actor(sender))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hello\"}"));
    }

    private static RequestPostProcessor actor(UUID id) {
        return jwt().jwt(token -> token.subject(id.toString()));
    }
}
