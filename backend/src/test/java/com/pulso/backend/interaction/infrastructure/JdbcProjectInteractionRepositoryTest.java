package com.pulso.backend.interaction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.pulso.backend.interaction.domain.ProjectFeedbackType;
import com.pulso.backend.project.application.PublicProjectNotFoundException;

@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectInteractionRepositoryTest {

    private static final UUID PROFILE_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID SECTOR_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SAVED_PROJECT_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID DISMISSED_PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID AVAILABLE_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcProjectInteractionRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcProjectInteractionRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.interaction_events, public.project_feedback, public.saved_projects, public.projects, public.sectors cascade")
                .update();
        jdbcClient.sql("create table public.sectors (id uuid primary key, name text not null)")
                .update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  tagline text not null,
                  sector_id uuid not null,
                  city text,
                  stage text not null,
                  status text not null,
                  published_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.saved_projects (
                  profile_id uuid not null,
                  project_id uuid not null,
                  saved_at timestamptz not null default timezone('utc', now()),
                  unsaved_at timestamptz,
                  primary key (profile_id, project_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_feedback (
                  profile_id uuid not null,
                  project_id uuid not null,
                  feedback_type text not null,
                  changed_at timestamptz not null default timezone('utc', now()),
                  primary key (profile_id, project_id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.interaction_events (
                  id bigserial primary key,
                  profile_id uuid not null,
                  project_id uuid not null,
                  event_type text not null,
                  source text not null
                )
                """).update();
        jdbcClient.sql("insert into public.sectors (id, name) values (:id, 'Sostenibilidad')")
                .param("id", SECTOR_ID)
                .update();
        insertProject(SAVED_PROJECT_ID, "saved-project", "Saved project", "PUBLISHED", 3);
        insertProject(DISMISSED_PROJECT_ID, "dismissed-project", "Dismissed project", "PUBLISHED", 2);
        insertProject(AVAILABLE_PROJECT_ID, "available-project", "Available project", "PUBLISHED", 1);
        insertProject(DRAFT_PROJECT_ID, "draft-project", "Draft project", "DRAFT", 4);
    }

    @Test
    void persistsSavedAndFeedbackStateAndRecordsOnlyChangedActions() {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        repository.setFeedback(PROFILE_ID, SAVED_PROJECT_ID, ProjectFeedbackType.INTERESTED);
        repository.setFeedback(PROFILE_ID, DISMISSED_PROJECT_ID, ProjectFeedbackType.DISMISSED);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findSavedProjects(PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(repository.countInterestedProjects(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findInterestedProjects(PROFILE_ID, 0, 20))
                .extracting(project -> project.id())
                .containsExactly(SAVED_PROJECT_ID);
        assertThat(repository.countDiscoveryFeed(PROFILE_ID)).isEqualTo(1);
        assertThat(repository.findDiscoveryFeed(PROFILE_ID, 0, 20))
                .extracting(item -> item.project().id())
                .containsExactly(AVAILABLE_PROJECT_ID);
        assertThat(jdbcClient.sql("select count(*) from public.interaction_events")
                .query(Integer.class)
                .single()).isEqualTo(3);
    }

    @Test
    void unsavesIdempotentlyAndRejectsUnpublishedMutationTargets() {
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, true);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, false);
        repository.setSaved(PROFILE_ID, SAVED_PROJECT_ID, false);

        assertThat(repository.countSavedProjects(PROFILE_ID)).isZero();
        assertThat(jdbcClient.sql("select count(*) from public.interaction_events")
                .query(Integer.class)
                .single()).isEqualTo(2);
        assertThatThrownBy(() -> repository.setSaved(PROFILE_ID, DRAFT_PROJECT_ID, true))
                .isInstanceOf(PublicProjectNotFoundException.class);
    }

    private void insertProject(
            UUID id,
            String slug,
            String name,
            String status,
            int publishedOrder) {
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, sector_id, city, stage, status, published_at
                ) values (
                  :id, :slug, :name, :tagline, :sectorId, 'La Paz', 'MVP', :status,
                  timezone('utc', now()) - (:publishedOrder * interval '1 hour')
                )
                """)
                .param("id", id)
                .param("slug", slug)
                .param("name", name)
                .param("tagline", name + " tagline")
                .param("sectorId", SECTOR_ID)
                .param("status", status)
                .param("publishedOrder", publishedOrder)
                .update();
    }
}
