package com.pulso.backend.profile.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.pulso.backend.profile.domain.Profile;
import com.pulso.backend.profile.domain.ProfilePatch;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProfileRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProfileRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProfileRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.profiles").update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key,
                  profile_kind text not null,
                  display_name text not null,
                  avatar_storage_path text,
                  short_bio text,
                  city text,
                  department text,
                  profile_visibility text not null,
                  verification_status text not null,
                  account_status text not null,
                  platform_role text not null,
                  onboarding_completed_at timestamptz,
                  created_at timestamptz not null,
                  updated_at timestamptz not null
                )
                """).update();
        jdbcClient.sql("""
                insert into public.profiles (
                  id,
                  profile_kind,
                  display_name,
                  avatar_storage_path,
                  short_bio,
                  city,
                  department,
                  profile_visibility,
                  verification_status,
                  account_status,
                  platform_role,
                  onboarding_completed_at,
                  created_at,
                  updated_at
                ) values (
                  :id,
                  'PERSON',
                  'Perfil Uno',
                  null,
                  'Biografía inicial.',
                  'Cochabamba',
                  'Cochabamba',
                  'PUBLIC',
                  'UNVERIFIED',
                  'ACTIVE',
                  'USER',
                  null,
                  '2026-09-08T12:00:00Z',
                  '2026-09-08T12:00:00Z'
                )
                """)
                .param("id", PROFILE_ID)
                .update();
    }

    @Test
    void findsTheProfileByItsAuthIdentity() {
        Profile profile = repository.findById(PROFILE_ID).orElseThrow();

        assertThat(profile.id()).isEqualTo(PROFILE_ID);
        assertThat(profile.displayName()).isEqualTo("Perfil Uno");
        assertThat(profile.shortBio()).isEqualTo("Biografía inicial.");
        assertThat(profile.platformRole()).isEqualTo("USER");
    }

    @Test
    void updatesEditableFieldsWithoutAllowingServerManagedFields() {
        ProfilePatch patch = new ProfilePatch(
                "ORGANIZATION",
                "Perfil actualizado",
                null,
                "Biografía actualizada.",
                null,
                "La Paz",
                "PRIVATE",
                null);

        Profile updated = repository.update(PROFILE_ID, patch).orElseThrow();

        assertThat(updated.profileKind()).isEqualTo("ORGANIZATION");
        assertThat(updated.displayName()).isEqualTo("Perfil actualizado");
        assertThat(updated.shortBio()).isEqualTo("Biografía actualizada.");
        assertThat(updated.department()).isEqualTo("La Paz");
        assertThat(updated.profileVisibility()).isEqualTo("PRIVATE");
        assertThat(updated.verificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(updated.accountStatus()).isEqualTo("ACTIVE");
        assertThat(updated.platformRole()).isEqualTo("USER");
    }

    @Test
    void returnsEmptyWhenTheProfileDoesNotExist() {
        assertThat(repository.findById(UUID.fromString("22222222-2222-2222-2222-222222222222")))
                .isEmpty();
    }
}
