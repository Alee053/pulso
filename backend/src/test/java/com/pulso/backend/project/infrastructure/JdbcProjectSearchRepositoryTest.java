package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pulso.backend.interaction.domain.ProjectSummary;
import com.pulso.backend.project.domain.ProjectSearchFilters;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcProjectSearchRepositoryTest {

    private static final UUID SECTOR_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_SECTOR_ID = UUID.fromString(
            "12121212-1212-1212-1212-121212121212");
    private static final UUID TAG_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID ECO_PACK_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID SOLAR_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID DRAFT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID PERCENT_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private JdbcProjectSearchRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcClient.sql("drop table if exists public.project_opportunities, public.project_tags, "
                + "public.projects, public.tags, public.sectors cascade").update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  slug text not null,
                  name text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.tags (
                  id uuid primary key,
                  slug text not null,
                  name text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.projects (
                  id uuid primary key,
                  slug text not null unique,
                  name text not null,
                  tagline text not null,
                  description text,
                  stage text not null,
                  city text,
                  department text,
                  media jsonb,
                  sector_id uuid not null references public.sectors (id),
                  status text not null,
                  published_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_tags (
                  project_id uuid not null references public.projects (id),
                  tag_id uuid not null references public.tags (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_opportunities (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  status text not null
                )
                """).update();

        jdbcClient.sql("""
                insert into public.sectors (id, slug, name) values
                  ('%s', 'sostenibilidad', 'Sostenibilidad'),
                  ('%s', 'energia', 'Energía')
                """.formatted(SECTOR_ID, OTHER_SECTOR_ID)).update();
        jdbcClient.sql("insert into public.tags (id, slug, name) values ('%s', 'impacto', 'Impacto')"
                .formatted(TAG_ID)).update();
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, description, stage, city, department,
                  media, sector_id, status, published_at
                ) values
                  ('%s', 'eco-pack', 'EcoPack', 'Envases biodegradables', 'Compostables.',
                   'MVP', 'Cochabamba', 'Cochabamba',
                   '[{"type":"COVER","url":"projects/eco.webp","alt":"Portada"}]'::jsonb,
                   '%s', 'PUBLISHED', '2026-01-01T00:00:00Z'),
                  ('%s', 'solar-sur', 'Solar Sur', 'Paneles para el campo', null,
                   'IDEA', 'La Paz', 'La Paz', null,
                   '%s', 'PUBLISHED', '2026-02-01T00:00:00Z'),
                  ('%s', 'borrador', 'EcoBorrador', 'Aun privado', null,
                   'MVP', 'Cochabamba', 'Cochabamba', null,
                   '%s', 'DRAFT', null),
                  ('%s', 'cien-por-ciento', '100%% Campo', 'Producto local', null,
                   'GROWING', 'Santa Cruz', 'Santa Cruz', null,
                   '%s', 'PUBLISHED', '2026-03-01T00:00:00Z')
                """.formatted(ECO_PACK_ID, SECTOR_ID, SOLAR_ID, OTHER_SECTOR_ID,
                DRAFT_ID, SECTOR_ID, PERCENT_ID, SECTOR_ID)).update();
        jdbcClient.sql("insert into public.project_tags (project_id, tag_id) values ('%s', '%s')"
                .formatted(ECO_PACK_ID, TAG_ID)).update();
        jdbcClient.sql("""
                insert into public.project_opportunities (id, project_id, status) values
                  (gen_random_uuid(), '%s', 'OPEN'),
                  (gen_random_uuid(), '%s', 'CLOSED')
                """.formatted(ECO_PACK_ID, SOLAR_ID)).update();
    }

    @Test
    void listsOnlyPublishedProjectsNewestFirst() {
        ProjectSearchFilters filters = filters(null, null, null, null, List.of(), null);

        assertThat(slugs(repository.search(filters, 0, 20)))
                .containsExactly("cien-por-ciento", "solar-sur", "eco-pack");
        assertThat(repository.count(filters)).isEqualTo(3);
    }

    @Test
    void paginatesWithoutChangingTheTotal() {
        ProjectSearchFilters filters = filters(null, null, null, null, List.of(), null);

        assertThat(slugs(repository.search(filters, 1, 1))).containsExactly("solar-sur");
        assertThat(repository.count(filters)).isEqualTo(3);
    }

    @Test
    void searchesTextAcrossNameTaglineDescriptionAndLocation() {
        assertThat(slugs(search(filters("ECOPACK", null, null, null, List.of(), null))))
                .containsExactly("eco-pack");
        assertThat(slugs(search(filters("paneles", null, null, null, List.of(), null))))
                .containsExactly("solar-sur");
        assertThat(slugs(search(filters("compostables", null, null, null, List.of(), null))))
                .containsExactly("eco-pack");
        assertThat(slugs(search(filters("la paz", null, null, null, List.of(), null))))
                .containsExactly("solar-sur");
    }

    @Test
    void neverReturnsUnpublishedProjectsEvenWhenTheyMatch() {
        assertThat(slugs(search(filters("borrador", null, null, null, List.of(), null)))).isEmpty();
    }

    @Test
    void treatsLikeWildcardsInTheQueryAsLiteralText() {
        assertThat(slugs(search(filters("%", null, null, null, List.of(), null))))
                .containsExactly("cien-por-ciento");
        assertThat(slugs(search(filters("_", null, null, null, List.of(), null)))).isEmpty();
    }

    @Test
    void filtersBySectorByStageAndByLocation() {
        assertThat(slugs(search(filters(null, "energia", null, null, List.of(), null))))
                .containsExactly("solar-sur");
        assertThat(slugs(search(filters(null, OTHER_SECTOR_ID.toString(), null, null, List.of(), null))))
                .containsExactly("solar-sur");
        assertThat(slugs(search(filters(null, null, "MVP", null, List.of(), null))))
                .containsExactly("eco-pack");
        assertThat(slugs(search(filters(null, null, null, "santa", List.of(), null))))
                .containsExactly("cien-por-ciento");
    }

    @Test
    void filtersByTagsAndOpenOpportunities() {
        assertThat(slugs(search(filters(null, null, null, null, List.of("impacto"), null))))
                .containsExactly("eco-pack");
        assertThat(slugs(search(filters(null, null, null, null, List.of("otro"), null)))).isEmpty();
        assertThat(slugs(search(filters(null, null, null, null, List.of(), true))))
                .containsExactly("eco-pack");
    }

    @Test
    void combinesFiltersAndExposesTheCoverImage() {
        List<ProjectSummary> result = search(
                filters("eco", "sostenibilidad", "MVP", "cocha", List.of("impacto"), true));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrl()).isEqualTo("projects/eco.webp");
        assertThat(result.get(0).imageAlt()).isEqualTo("Portada");
        assertThat(result.get(0).sector()).isEqualTo("Sostenibilidad");
    }

    private List<ProjectSummary> search(ProjectSearchFilters filters) {
        return repository.search(filters, 0, 20);
    }

    private static ProjectSearchFilters filters(String query, String sector, String stage,
            String location, List<String> tags, Boolean hasOpenOpportunities) {
        return new ProjectSearchFilters(query, sector, stage, location, tags, hasOpenOpportunities);
    }

    private static List<String> slugs(List<ProjectSummary> projects) {
        return projects.stream().map(ProjectSummary::slug).toList();
    }
}
