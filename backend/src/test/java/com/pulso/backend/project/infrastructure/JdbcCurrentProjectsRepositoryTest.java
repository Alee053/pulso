package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class JdbcCurrentProjectsRepositoryTest {
    private static final UUID PROFILE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private JdbcCurrentProjectsRepository repository;

    @BeforeEach
    void setUp() {
        var jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.sql("drop table if exists public.project_members, public.projects").update();
        jdbc.sql("create table public.projects (id uuid primary key, name text not null)").update();
        jdbc.sql("""
                create table public.project_members (
                    project_id uuid references public.projects(id), profile_id uuid,
                    role text not null, membership_status text not null)
                """).update();
        for (int i = 1; i <= 6; i++) {
            UUID id = new UUID(0, i);
            jdbc.sql("insert into public.projects values (:id, :name)")
                    .param("id", id).param("name", "Project " + i).update();
            jdbc.sql("insert into public.project_members values (:id, :profile, :role, :status)")
                    .param("id", id).param("profile", i == 3 ? UUID.randomUUID() : PROFILE_ID)
                    .param("role", i == 1 ? "ADMIN" : "MEMBER")
                    .param("status", switch (i) {
                        case 4 -> "LEFT";
                        case 5 -> "REMOVED";
                        case 6 -> "INVITED";
                        default -> "ACTIVE";
                    }).update();
        }
        repository = new JdbcCurrentProjectsRepository(jdbc);
    }

    @Test
    void returnsOnlyOwnActiveMembershipsIncludingPrivateProjects() {
        var projects = repository.findActive(PROFILE_ID, 0, 20);
        assertThat(projects).extracting(project -> project.name()).containsExactly("Project 1", "Project 2");
        assertThat(projects).extracting(project -> project.role()).containsExactly("ADMIN", "MEMBER");
    }

    @Test
    void boundsAndOrdersPagesWithoutOtherAccountsOrInactiveMemberships() {
        assertThat(repository.findActive(PROFILE_ID, 1, 1)).extracting(project -> project.name())
                .containsExactly("Project 2");
        assertThat(repository.findActive(PROFILE_ID, 2, 1)).isEmpty();
        assertThat(repository.findActive(UUID.randomUUID(), 0, 20)).isEmpty();
    }
}
