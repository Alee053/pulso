package com.pulso.backend.featureflag.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.featureflag.domain.FeatureFlag;
import com.pulso.backend.featureflag.domain.FeatureFlagKey;

@Testcontainers(disabledWithoutDocker = true)
class JdbcFeatureFlagRepositoryTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbc;
    private JdbcFeatureFlagRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("drop table if exists public.feature_flags").update();
        createRoleIfMissing("anon");
        createRoleIfMissing("authenticated");
        new ResourceDatabasePopulator(new FileSystemResource(migrationPath().toFile()))
                .execute(dataSource);
        repository = new JdbcFeatureFlagRepository(jdbc);
    }

    private void createRoleIfMissing(String role) {
        boolean exists = jdbc.sql("select exists (select 1 from pg_roles where rolname = :role)")
                .param("role", role)
                .query(Boolean.class)
                .single();
        if (!exists) {
            jdbc.sql("create role " + role).update();
        }
    }

    private static Path migrationPath() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        Path fromRepositoryRoot = currentDirectory.resolve(
                "database/supabase/migrations/20261025024407_add_feature_flags.sql");
        if (Files.exists(fromRepositoryRoot)) {
            return fromRepositoryRoot;
        }
        return currentDirectory.resolve(
                "../database/supabase/migrations/20261025024407_add_feature_flags.sql").normalize();
    }

    @Test
    void readsThePersistedFlagsInStableOrder() {
        assertThat(repository.findAll()).containsExactly(
                new FeatureFlag(FeatureFlagKey.LANGUAGE_SWITCHER, true),
                new FeatureFlag(FeatureFlagKey.PROJECT_CREATION, true));
    }

    @Test
    void persistsAnUpdatedBooleanInPostgreSql() {
        assertThat(repository.update(FeatureFlagKey.PROJECT_CREATION, false))
                .contains(new FeatureFlag(FeatureFlagKey.PROJECT_CREATION, false));
        assertThat(repository.isEnabled(FeatureFlagKey.PROJECT_CREATION)).isFalse();
        assertThat(jdbc.sql("select enabled from public.feature_flags where flag_key = 'project_creation'")
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void absentKnownRowUsesItsDeclaredDefault() {
        jdbc.sql("delete from public.feature_flags where flag_key = 'language_switcher'").update();

        assertThat(repository.isEnabled(FeatureFlagKey.LANGUAGE_SWITCHER)).isTrue();
    }
}
