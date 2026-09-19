package com.pulso.backend.project.infrastructure;

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

import com.pulso.backend.project.domain.PublicProjectDetail;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPublicProjectRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID SECTOR_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID TAG_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID PUBLIC_PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRIVATE_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INACTIVE_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcPublicProjectRepository repository;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcClient = JdbcClient.create(dataSource);
        repository = new JdbcPublicProjectRepository(jdbcClient);

        jdbcClient.sql("drop table if exists public.project_members, public.project_tags, "
                + "public.projects, public.tags, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key,
                  display_name text,
                  avatar_storage_path text,
                  account_status text not null
                )
                """).update();
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
                  value_proposition text,
                  stage text not null,
                  city text,
                  department text,
                  external_links jsonb,
                  sector_id uuid not null references public.sectors (id),
                  status text not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_tags (
                  project_id uuid not null references public.projects (id),
                  tag_id uuid not null references public.tags (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_members (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  profile_id uuid references public.profiles (id),
                  membership_status text not null,
                  is_public boolean not null,
                  display_title text,
                  is_featured boolean not null
                )
                """).update();

        jdbcClient.sql("insert into public.sectors (id, slug, name) values (:id, :slug, :name)")
                .param("id", SECTOR_ID)
                .param("slug", "sostenibilidad")
                .param("name", "Sostenibilidad")
                .update();
        jdbcClient.sql("insert into public.tags (id, slug, name) values (:id, :slug, :name)")
                .param("id", TAG_ID)
                .param("slug", "economia-circular")
                .param("name", "Economía circular")
                .update();
        jdbcClient.sql("""
                insert into public.profiles (id, display_name, avatar_storage_path, account_status)
                values
                  (:publicId, 'Persona Uno', 'avatars/persona-uno.webp', 'ACTIVE'),
                  (:privateId, 'Persona Privada', null, 'ACTIVE'),
                  (:inactiveId, 'Persona Inactiva', null, 'SUSPENDED')
                """)
                .param("publicId", PUBLIC_PROFILE_ID)
                .param("privateId", PRIVATE_PROFILE_ID)
                .param("inactiveId", INACTIVE_PROFILE_ID)
                .update();
        jdbcClient.sql("""
                insert into public.projects (
                  id, slug, name, tagline, description, value_proposition,
                  stage, city, department, external_links, sector_id, status
                ) values
                  (
                    :projectId, 'eco-pack', 'EcoPack Bolivia', 'Envases biodegradables',
                    'Envases compostables.', 'Reduce residuos agrícolas.', 'MVP',
                    'Cochabamba', 'Cochabamba',
                    cast(:links as jsonb), :sectorId, 'PUBLISHED'
                  ),
                  (
                    :draftId, 'draft-project', 'Draft', 'Privado', null, null, 'IDEA',
                    null, null, null, :sectorId, 'DRAFT'
                  )
                """)
                .param("projectId", PROJECT_ID)
                .param("draftId", DRAFT_PROJECT_ID)
                .param("links", """
                        [{"type":"WEBSITE","url":"https://ecopack.example","label":"Sitio web",\"private\":\"no\"}]
                        """)
                .param("sectorId", SECTOR_ID)
                .update();
        jdbcClient.sql("insert into public.project_tags (project_id, tag_id) values (:projectId, :tagId)")
                .param("projectId", PROJECT_ID)
                .param("tagId", TAG_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                  id, project_id, profile_id, membership_status, is_public, display_title, is_featured
                ) values
                  (:publicMemberId, :projectId, :publicProfileId, 'ACTIVE', true, 'Co-fundadora', true),
                  (:privateMemberId, :projectId, :privateProfileId, 'ACTIVE', false, 'Invitado', false),
                  (:inactiveMemberId, :projectId, :inactiveProfileId, 'ACTIVE', true, 'Ex miembro', false)
                """)
                .param("publicMemberId", UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                .param("privateMemberId", UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
                .param("inactiveMemberId", UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                .param("projectId", PROJECT_ID)
                .param("publicProfileId", PUBLIC_PROFILE_ID)
                .param("privateProfileId", PRIVATE_PROFILE_ID)
                .param("inactiveProfileId", INACTIVE_PROFILE_ID)
                .update();
    }

    @Test
    void readsPublishedDetailsBySlugOrUuidAndFiltersPrivateData() {
        PublicProjectDetail bySlug = repository.findPublishedByReference("eco-pack").orElseThrow();
        PublicProjectDetail byId = repository.findPublishedByReference(PROJECT_ID.toString())
                .orElseThrow();

        assertThat(bySlug).isEqualTo(byId);
        assertThat(bySlug.sector()).isEqualTo(
                new PublicProjectDetail.Sector(SECTOR_ID, "sostenibilidad", "Sostenibilidad"));
        assertThat(bySlug.tags()).containsExactly(
                new PublicProjectDetail.Tag(TAG_ID, "economia-circular", "Economía circular"));
        assertThat(bySlug.externalLinks()).containsExactly(
                new PublicProjectDetail.ExternalLink(
                        "WEBSITE", "https://ecopack.example", "Sitio web"));
        assertThat(bySlug.members()).containsExactly(
                new PublicProjectDetail.Member(
                        PUBLIC_PROFILE_ID,
                        "Persona Uno",
                        "avatars/persona-uno.webp",
                        "Co-fundadora",
                        true));
    }

    @Test
    void doesNotReturnUnpublishedProjects() {
        assertThat(repository.findPublishedByReference("draft-project")).isEmpty();
        assertThat(repository.findPublishedByReference(DRAFT_PROJECT_ID.toString())).isEmpty();
        assertThat(repository.findPublishedByReference("not-a-project")).isEmpty();
    }
}
