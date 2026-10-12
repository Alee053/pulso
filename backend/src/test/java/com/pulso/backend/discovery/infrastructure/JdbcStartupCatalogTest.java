package com.pulso.backend.discovery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.discovery.domain.StartupSummary;

@Testcontainers(disabledWithoutDocker = true)
class JdbcStartupCatalogTest {

    private static final UUID PUBLISHED_PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID PENDING_PROJECT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID REJECTED_PROJECT_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID ARCHIVED_PROJECT_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID SUSPENDED_PROJECT_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");
    private static final UUID SECTOR_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-01T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcStartupCatalog catalog;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        catalog = new JdbcStartupCatalog(jdbcClient);

        jdbcClient.sql("drop table if exists public.projects, public.sectors cascade")
                .update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  name text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  tagline text not null,
                  sector_id uuid not null references public.sectors (id),
                  city text,
                  stage text not null,
                  media jsonb,
                  status text not null,
                  published_at timestamptz
                )
                """).update();
        jdbcClient.sql("insert into public.sectors (id, name) values (:id, 'Sostenibilidad')")
                .param("id", SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, sector_id, city, stage, status, published_at
                ) values
                  (:publishedId, 'public-project', 'Proyecto público', 'Publicado',
                    :sectorId, 'Cochabamba', 'MVP', 'PUBLISHED', :publishedAt),
                  (:draftId, 'draft-project', 'Borrador', 'Privado',
                    :sectorId, 'Cochabamba', 'IDEA', 'DRAFT', null),
                  (:pendingId, 'pending-project', 'En revisión', 'Pendiente',
                    :sectorId, 'Cochabamba', 'IDEA', 'PENDING_REVIEW', null),
                  (:rejectedId, 'rejected-project', 'Rechazado', 'No publicado',
                    :sectorId, 'Cochabamba', 'IDEA', 'REJECTED', null),
                  (:archivedId, 'archived-project', 'Archivado', 'Retirado',
                    :sectorId, 'Cochabamba', 'MVP', 'ARCHIVED', :publishedAt),
                  (:suspendedId, 'suspended-project', 'Suspendido', 'No disponible',
                    :sectorId, 'Cochabamba', 'MVP', 'SUSPENDED', :publishedAt)
                """)
                .param("publishedId", PUBLISHED_PROJECT_ID)
                .param("draftId", DRAFT_PROJECT_ID)
                .param("pendingId", PENDING_PROJECT_ID)
                .param("rejectedId", REJECTED_PROJECT_ID)
                .param("archivedId", ARCHIVED_PROJECT_ID)
                .param("suspendedId", SUSPENDED_PROJECT_ID)
                .param("sectorId", SECTOR_ID)
                .param("publishedAt", PUBLISHED_AT.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Test
    void readsTheCoverInsteadOfOtherMediaAndPreservesMissingImages() {
        jdbcClient.sql("""
                update public.projects set media = '[
                  {"type":"VIDEO","url":"https://example.test/video"},
                  {"type":"COVER","url":"https://example.test/cover.jpg","alt":"Portada"}
                ]'::jsonb where id = :id
                """).param("id", PUBLISHED_PROJECT_ID).update();

        StartupSummary project = catalog.findPublished(0, 10).getFirst();
        assertThat(project.imageUrl()).isEqualTo("https://example.test/cover.jpg");
        assertThat(project.imageAlt()).isEqualTo("Portada");

        jdbcClient.sql("update public.projects set media = '[]'::jsonb where id = :id")
                .param("id", PUBLISHED_PROJECT_ID).update();
        assertThat(catalog.findPublished(0, 10).getFirst().imageUrl()).isNull();
    }

    @Test
    void listsOnlyPublishedProjectsForPublicDiscovery() {
        assertThat(catalog.findPublished(0, 10)).containsExactly(
                new StartupSummary(
                        PUBLISHED_PROJECT_ID,
                        "public-project",
                        "Proyecto público",
                        "Publicado",
                        "Sostenibilidad",
                        "Cochabamba",
                        "MVP"));
    }
}
