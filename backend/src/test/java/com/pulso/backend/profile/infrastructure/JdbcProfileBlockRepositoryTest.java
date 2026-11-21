package com.pulso.backend.profile.infrastructure;

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

import com.pulso.backend.profile.domain.ProfileBlock;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProfileBlockRepositoryTest {
    private static final UUID BLOCKER_ID = id("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = id("22222222-2222-2222-2222-222222222222");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbc;
    private JdbcProfileBlockRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        repository = new JdbcProfileBlockRepository(jdbc);
        jdbc.sql("drop table if exists public.profile_blocks, public.profiles cascade").update();
        jdbc.sql("create table public.profiles (id uuid primary key)").update();
        jdbc.sql("""
                create table public.profile_blocks (
                  blocker_profile_id uuid not null references public.profiles (id) on delete cascade,
                  blocked_profile_id uuid not null references public.profiles (id) on delete cascade,
                  created_at timestamptz not null default now(),
                  primary key (blocker_profile_id, blocked_profile_id),
                  check (blocker_profile_id <> blocked_profile_id)
                )
                """).update();
        jdbc.sql("insert into public.profiles (id) values (:blocker), (:target)")
                .param("blocker", BLOCKER_ID).param("target", TARGET_ID).update();
    }

    @Test
    void createsIdempotentlyReadsAndDeletesOnlyTheDirectedBlock() {
        ProfileBlock first = repository.createIfAbsent(BLOCKER_ID, TARGET_ID);
        ProfileBlock repeated = repository.createIfAbsent(BLOCKER_ID, TARGET_ID);

        assertThat(first.targetProfileId()).isEqualTo(TARGET_ID);
        assertThat(first.blocked()).isTrue();
        assertThat(first.createdAt()).isNotNull();
        assertThat(repeated).isEqualTo(first);
        assertThat(repository.find(BLOCKER_ID, TARGET_ID)).contains(first);
        assertThat(repository.find(TARGET_ID, BLOCKER_ID)).isEmpty();

        repository.delete(BLOCKER_ID, TARGET_ID);

        assertThat(repository.find(BLOCKER_ID, TARGET_ID)).isEmpty();
    }

    private static UUID id(String value) { return UUID.fromString(value); }
}
