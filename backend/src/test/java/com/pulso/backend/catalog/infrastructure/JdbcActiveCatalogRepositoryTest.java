package com.pulso.backend.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcActiveCatalogRepositoryTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcClient jdbcClient;
    private JdbcActiveCatalogRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcActiveCatalogRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.skills, public.tags, public.sectors cascade").update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  description text,
                  is_active boolean not null,
                  sort_order integer not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.tags (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  tag_type text not null,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.skills (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  category text,
                  is_active boolean not null
                )
                """).update();
    }

    @Test
    void readsOnlyActiveEntriesWithStableOrderingAndBoundedResults() {
        jdbcClient.sql("""
                insert into public.sectors (id, slug, name, description, is_active, sort_order) values
                ('00000000-0000-0000-0000-000000000003', 'finanzas', 'Finanzas', null, true, 2),
                ('00000000-0000-0000-0000-000000000002', 'agro', 'Agro', 'Sector agropecuario', true, 1),
                ('00000000-0000-0000-0000-000000000001', 'oculto', 'Oculto', null, false, 0)
                """).update();
        jdbcClient.sql("""
                insert into public.tags (id, slug, name, tag_type, is_active) values
                ('10000000-0000-0000-0000-000000000001', 'zeta', 'Zeta', 'TOPIC', true),
                ('10000000-0000-0000-0000-000000000002', 'alfa', 'Alfa', 'INDUSTRY', true),
                ('10000000-0000-0000-0000-000000000003', 'oculto', 'Oculto', 'OTHER', false)
                """).update();
        jdbcClient.sql("""
                insert into public.skills (id, slug, name, category, is_active) values
                ('20000000-0000-0000-0000-000000000001', 'python', 'Python', 'DESARROLLO', true),
                ('20000000-0000-0000-0000-000000000002', 'java', 'Java', 'DESARROLLO', true),
                ('20000000-0000-0000-0000-000000000003', 'oculta', 'Oculta', null, false)
                """).update();

        assertThat(repository.findActiveSectors(1))
                .extracting(item -> item.slug())
                .containsExactly("agro");
        assertThat(repository.findActiveTags(10))
                .extracting(item -> item.slug())
                .containsExactly("alfa", "zeta");
        assertThat(repository.findActiveSkills(10))
                .extracting(item -> item.slug())
                .containsExactly("java", "python");
    }

    @Test
    void returnsEmptyListsWhenCatalogsHaveNoActiveEntries() {
        jdbcClient.sql("""
                insert into public.sectors (id, slug, name, description, is_active, sort_order)
                values ('00000000-0000-0000-0000-000000000001', 'inactivo', 'Inactivo', null, false, 1)
                """).update();

        assertThat(repository.findActiveSectors(10)).isEmpty();
        assertThat(repository.findActiveTags(10)).isEmpty();
        assertThat(repository.findActiveSkills(10)).isEmpty();
    }

    @Test
    void surfacesDatasourceFailuresAsSpringDataAccessExceptions() {
        DataSource unavailableDataSource = new DriverManagerDataSource(
                "jdbc:postgresql://127.0.0.1:1/postgres?connectTimeout=1",
                "postgres",
                "postgres");
        JdbcActiveCatalogRepository unavailableRepository = new JdbcActiveCatalogRepository(
                JdbcClient.create(unavailableDataSource));

        assertThatThrownBy(() -> unavailableRepository.findActiveSectors(10))
                .isInstanceOf(DataAccessException.class);
    }
}
