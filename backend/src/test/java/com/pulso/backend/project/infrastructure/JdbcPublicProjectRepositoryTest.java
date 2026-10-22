package com.pulso.backend.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulso.backend.project.domain.ProjectLifecycleState;
import com.pulso.backend.project.domain.PublicProjectDetail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class JdbcPublicProjectRepositoryTest {

    private static final UUID PROJECT_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");
    private static final UUID PENDING_PROJECT_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666");
    private static final UUID REJECTED_PROJECT_ID = UUID.fromString(
            "77777777-7777-7777-7777-777777777777");
    private static final UUID ARCHIVED_PROJECT_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888");
    private static final UUID SUSPENDED_PROJECT_ID = UUID.fromString(
            "99999999-9999-9999-9999-999999999999");
    private static final UUID INACTIVE_SECTOR_PROJECT_ID = UUID.fromString(
            "12121212-1212-1212-1212-121212121212");
    private static final UUID SECTOR_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID INACTIVE_SECTOR_ID = UUID.fromString(
            "13131313-1313-1313-1313-131313131313");
    private static final UUID TAG_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
    private static final UUID INACTIVE_TAG_ID = UUID.fromString(
            "14141414-1414-1414-1414-141414141414");
    private static final UUID SECOND_TAG_ID = UUID.fromString(
            "15151515-1515-1515-1515-151515151515");
    private static final UUID OPEN_OPPORTUNITY_ID = UUID.fromString(
            "16161616-1616-1616-1616-161616161616");
    private static final UUID PAUSED_OPPORTUNITY_ID = UUID.fromString(
            "17171717-1717-1717-1717-171717171717");
    private static final UUID REQUIRED_SKILL_ID = UUID.fromString(
            "18181818-1818-1818-1818-181818181818");
    private static final UUID PREFERRED_SKILL_ID = UUID.fromString(
            "19191919-1919-1919-1919-191919191919");
    private static final UUID INACTIVE_SKILL_ID = UUID.fromString(
            "20202020-2020-2020-2020-202020202020");
    private static final UUID PUBLIC_PROFILE_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRIVATE_PROFILE_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INACTIVE_PROFILE_ID = UUID.fromString(
            "cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LEFT_PROFILE_ID = UUID.fromString(
            "dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID SECOND_PUBLIC_PROFILE_ID = UUID.fromString(
            "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcPublicProjectRepository repository;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcProjectLifecycleRepository lifecycleRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcClient.sql("drop table if exists public.opportunity_skills, public.project_opportunities, "
                + "public.skills, public.project_members, public.project_tags, "
                + "public.projects, public.tags, public.sectors, public.profiles cascade")
                .update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key,
                  display_name text,
                  avatar_storage_path text,
                  account_status text not null,
                  profile_visibility text not null default 'PUBLIC',
                  deleted_at timestamptz,
                  short_bio text default 'private biography',
                  platform_role text default 'ADMIN'
                )
                """).update();
        jdbcClient.sql("""
                create table public.sectors (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.tags (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  is_active boolean not null
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
                  media jsonb,
                  sector_id uuid not null references public.sectors (id),
                  status text not null,
                  published_at timestamptz,
                  archived_at timestamptz,
                  review_note text default 'private review note',
                  reviewed_by_profile_id uuid,
                  reviewed_at timestamptz
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
                  role text not null default 'MEMBER',
                  is_public boolean not null,
                  display_title text,
                  is_featured boolean not null,
                  invitee_email text,
                  invite_token_digest text
                )
                """).update();
        jdbcClient.sql("""
                create table public.skills (
                  id uuid primary key,
                  slug text not null,
                  name text not null,
                  is_active boolean not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_opportunities (
                  id uuid primary key,
                  project_id uuid not null,
                  opportunity_type text not null,
                  title text not null,
                  description text not null,
                  status text not null,
                  city text,
                  department text,
                  remote_allowed boolean not null,
                  created_at timestamptz not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.opportunity_skills (
                  opportunity_id uuid not null,
                  skill_id uuid not null,
                  importance text not null
                )
                """).update();

        jdbcClient.sql("""
                insert into public.sectors (id, slug, name, is_active)
                values
                  (:activeId, 'sostenibilidad', 'Sostenibilidad', true),
                  (:inactiveId, 'sector-inactivo', 'Sector inactivo', false)
                """)
                .param("activeId", SECTOR_ID)
                .param("inactiveId", INACTIVE_SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.tags (id, slug, name, is_active)
                values
                  (:id, :slug, :name, true),
                  (:inactiveId, 'tag-inactivo', 'Tag inactivo', false),
                  (:secondId, 'impacto', 'Impacto', true)
                """)
                .param("id", TAG_ID)
                .param("slug", "economia-circular")
                .param("inactiveId", INACTIVE_TAG_ID)
                .param("secondId", SECOND_TAG_ID)
                .param("name", "Economía circular")
                .update();
        jdbcClient.sql("""
                insert into public.skills (id, slug, name, is_active)
                values
                  (:requiredId, 'angular', 'Angular', true),
                  (:preferredId, 'figma', 'Figma', true),
                  (:inactiveId, 'internal-skill', 'Internal skill', false)
                """)
                .param("requiredId", REQUIRED_SKILL_ID)
                .param("preferredId", PREFERRED_SKILL_ID)
                .param("inactiveId", INACTIVE_SKILL_ID)
                .update();
        jdbcClient.sql("""
                insert into public.profiles (id, display_name, avatar_storage_path, account_status)
                values
                  (:publicId, 'Persona Uno', 'avatars/persona-uno.webp', 'ACTIVE'),
                  (:privateId, 'Persona Privada', null, 'ACTIVE'),
                  (:inactiveId, 'Persona Inactiva', null, 'SUSPENDED'),
                  (:leftId, 'Persona que salio', null, 'ACTIVE'),
                  (:secondPublicId, 'Persona Dos', null, 'ACTIVE')
                """)
                .param("publicId", PUBLIC_PROFILE_ID)
                .param("privateId", PRIVATE_PROFILE_ID)
                .param("inactiveId", INACTIVE_PROFILE_ID)
                .param("leftId", LEFT_PROFILE_ID)
                .param("secondPublicId", SECOND_PUBLIC_PROFILE_ID)
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
                  ),
                  (
                    :pendingId, 'pending-project', 'Pending', 'Under review', null, null, 'IDEA',
                    null, null, null, :sectorId, 'PENDING_REVIEW'
                  ),
                  (
                    :rejectedId, 'rejected-project', 'Rejected', 'Rejected', null, null, 'IDEA',
                    null, null, null, :sectorId, 'REJECTED'
                  ),
                  (
                    :archivedId, 'archived-project', 'Archived', 'Archived', null, null, 'IDEA',
                    null, null, null, :sectorId, 'ARCHIVED'
                  ),
                  (
                    :suspendedId, 'suspended-project', 'Suspended', 'Suspended', null, null, 'IDEA',
                    null, null, null, :sectorId, 'SUSPENDED'
                  ),
                  (
                    :inactiveSectorId, 'inactive-sector-project', 'Inactive sector', 'Unavailable',
                    null, null, 'IDEA', null, null, null, :inactiveSector, 'PUBLISHED'
                  )
                """)
                .param("projectId", PROJECT_ID)
                .param("draftId", DRAFT_PROJECT_ID)
                .param("pendingId", PENDING_PROJECT_ID)
                .param("rejectedId", REJECTED_PROJECT_ID)
                .param("archivedId", ARCHIVED_PROJECT_ID)
                .param("suspendedId", SUSPENDED_PROJECT_ID)
                .param("inactiveSectorId", INACTIVE_SECTOR_PROJECT_ID)
                .param("links", """
                        [
                          {"type":"WEBSITE","url":"https://ecopack.example","label":"Sitio web","is_public":true},
                          {"type":"ADMIN","url":"https://internal.ecopack.example","label":"Panel interno","is_public":false},
                          {"type":"INVITE","url":"https://invite.ecopack.example","label":"Invitacion","private":true}
                        ]
                        """)
                .param("sectorId", SECTOR_ID)
                .param("inactiveSector", INACTIVE_SECTOR_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_opportunities (
                  id, project_id, opportunity_type, title, description, status,
                  city, department, remote_allowed, created_at
                ) values
                  (:openId, :projectId, 'TALENT', 'Desarrollador Frontend',
                   'Implementa la experiencia web.', 'OPEN', 'Cochabamba',
                   'Cochabamba', true, '2026-10-01T12:00:00Z'),
                  (:pausedId, :projectId, 'MENTOR', 'Mentoría privada',
                   'No debe mostrarse.', 'PAUSED', null, null, false,
                   '2026-10-02T12:00:00Z')
                """)
                .param("openId", OPEN_OPPORTUNITY_ID)
                .param("pausedId", PAUSED_OPPORTUNITY_ID)
                .param("projectId", PROJECT_ID)
                .update();
        jdbcClient.sql("""
                insert into public.opportunity_skills (opportunity_id, skill_id, importance)
                values
                  (:openId, :requiredId, 'REQUIRED'),
                  (:openId, :preferredId, 'PREFERRED'),
                  (:openId, :inactiveId, 'REQUIRED'),
                  (:pausedId, :requiredId, 'REQUIRED')
                """)
                .param("openId", OPEN_OPPORTUNITY_ID)
                .param("pausedId", PAUSED_OPPORTUNITY_ID)
                .param("requiredId", REQUIRED_SKILL_ID)
                .param("preferredId", PREFERRED_SKILL_ID)
                .param("inactiveId", INACTIVE_SKILL_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_tags (project_id, tag_id)
                values
                  (:projectId, :activeTagId),
                  (:projectId, :inactiveTagId),
                  (:projectId, :secondTagId)
                """)
                .param("projectId", PROJECT_ID)
                .param("activeTagId", TAG_ID)
                .param("inactiveTagId", INACTIVE_TAG_ID)
                .param("secondTagId", SECOND_TAG_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                  id, project_id, profile_id, membership_status, is_public, display_title, is_featured
                ) values
                  (:publicMemberId, :projectId, :publicProfileId, 'ACTIVE', true, 'Co-fundadora', true),
                  (:privateMemberId, :projectId, :privateProfileId, 'ACTIVE', false, 'Invitado', false),
                  (:inactiveMemberId, :projectId, :inactiveProfileId, 'ACTIVE', true, 'Ex miembro', false),
                  (:leftMemberId, :projectId, :leftProfileId, 'LEFT', true, 'Former member', false),
                  (:secondPublicMemberId, :projectId, :secondPublicProfileId, 'ACTIVE', true, 'Member', false)
                """)
                .param("publicMemberId", UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                .param("privateMemberId", UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
                .param("inactiveMemberId", UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))
                .param("leftMemberId", UUID.fromString("12121212-3434-5656-7878-909090909090"))
                .param("secondPublicMemberId", UUID.fromString("23232323-2323-2323-2323-232323232323"))
                .param("projectId", PROJECT_ID)
                .param("publicProfileId", PUBLIC_PROFILE_ID)
                .param("privateProfileId", PRIVATE_PROFILE_ID)
                .param("inactiveProfileId", INACTIVE_PROFILE_ID)
                .param("leftProfileId", LEFT_PROFILE_ID)
                .param("secondPublicProfileId", SECOND_PUBLIC_PROFILE_ID)
                .update();
        jdbcClient.sql("""
                insert into public.project_members (
                    id, project_id, membership_status, is_public, is_featured,
                    invitee_email, invite_token_digest
                ) values (:id, :projectId, 'INVITED', true, true,
                    'invited@example.test', 'test-invite-digest')
                """).param("id", UUID.fromString("abababab-abab-abab-abab-abababababab"))
                .param("projectId", PROJECT_ID).update();
    }

    @Test
    void exposesCoverInPublicDetail() throws Exception {
        jdbcClient.sql("""
                update public.projects set media = '[
                  {"type":"COVER","url":"https://example.test/cover.jpg","alt":"Portada EcoPack"}
                ]'::jsonb where id = :id
                """).param("id", PROJECT_ID).update();

        mockMvc.perform(get("/api/v1/projects/eco-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.test/cover.jpg"))
                .andExpect(jsonPath("$.data.imageAlt").value("Portada EcoPack"));
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
                new PublicProjectDetail.Tag(TAG_ID, "economia-circular", "Economía circular"),
                new PublicProjectDetail.Tag(SECOND_TAG_ID, "impacto", "Impacto"));
        assertThat(bySlug.externalLinks()).containsExactly(
                new PublicProjectDetail.ExternalLink(
                        "WEBSITE", "https://ecopack.example", "Sitio web"));
        assertThat(bySlug.members()).containsExactly(
                new PublicProjectDetail.Member(
                        PUBLIC_PROFILE_ID,
                        "Persona Uno",
                        "avatars/persona-uno.webp",
                        "Co-fundadora",
                        true),
                new PublicProjectDetail.Member(
                        SECOND_PUBLIC_PROFILE_ID,
                        "Persona Dos",
                        null,
                        "Member",
                        false));
        assertThat(bySlug.opportunities()).containsExactly(
                new PublicProjectDetail.Opportunity(
                        "Desarrollador Frontend",
                        "TALENT",
                        "Implementa la experiencia web.",
                        "Cochabamba",
                        "Cochabamba",
                        true,
                        java.util.List.of("Angular")));
    }

    @ParameterizedTest
    @CsvSource({"PRIVATE,ACTIVE,false", "MEMBERS_ONLY,ACTIVE,false", "PUBLIC,DELETED,false",
            "PUBLIC,ACTIVE,true"})
    void doesNotExposeProfilesWithoutPublicConsent(String visibility, String accountStatus,
            boolean deleted) throws Exception {
        jdbcClient.sql("""
                update public.profiles set profile_visibility = :visibility,
                    account_status = :accountStatus,
                    deleted_at = case when :deleted then now() else null end
                where id = :id
                """)
                .param("visibility", visibility).param("accountStatus", accountStatus)
                .param("deleted", deleted).param("id", PUBLIC_PROFILE_ID).update();

        for (boolean authenticated : new boolean[] {false, true}) {
            mockMvc.perform(publicRequest("eco-pack", authenticated))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.members.length()").value(1))
                    .andExpect(jsonPath("$.data.members[0].profileId")
                            .value(SECOND_PUBLIC_PROFILE_ID.toString()));
        }
    }

    @ParameterizedTest
    @CsvSource({"eco-pack,false", "eco-pack,true",
            "33333333-3333-3333-3333-333333333333,false",
            "33333333-3333-3333-3333-333333333333,true"})
    void exposesExactlyTheAuthorizedPublicContract(String reference, boolean authenticated)
            throws Exception {
        String body = mockMvc.perform(publicRequest(reference, authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[0].profileId").value(PUBLIC_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data.members[1].profileId").value(SECOND_PUBLIC_PROFILE_ID.toString()))
                .andReturn().getResponse().getContentAsString();
        JsonNode envelope = objectMapper.readTree(body);
        assertFields(envelope, "data");
        JsonNode data = envelope.path("data");
        assertFields(data, "id", "slug", "name", "tagline", "description", "valueProposition",
                "sector", "stage", "city", "department", "tags", "externalLinks", "members",
                "imageUrl", "imageAlt", "opportunities");
        assertFields(data.path("sector"), "id", "slug", "name");
        data.path("tags").forEach(tag -> assertFields(tag, "id", "slug", "name"));
        data.path("externalLinks").forEach(link -> assertFields(link, "type", "url", "label"));
        data.path("members").forEach(member -> assertFields(member,
                "profileId", "displayName", "avatarStoragePath", "displayTitle", "isFeatured"));
        assertThat(data.path("opportunities")).hasSize(1);
        JsonNode opportunity = data.path("opportunities").get(0);
        assertFields(opportunity, "title", "opportunityType", "description", "city",
                "department", "remoteAllowed", "requiredSkills");
        assertThat(opportunity.path("requiredSkills").toString()).isEqualTo("[\"Angular\"]");
        assertThat(body).doesNotContain("private biography", "private review note",
                "invited@example.test", "test-invite-digest", "test-token",
                OPEN_OPPORTUNITY_ID.toString(), REQUIRED_SKILL_ID.toString(),
                "Figma", "Internal skill", "Mentoría privada");
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,false", "DRAFT,true", "PENDING_REVIEW,false", "PENDING_REVIEW,true",
            "REJECTED,false", "REJECTED,true", "ARCHIVED,false", "ARCHIVED,true",
            "SUSPENDED,false", "SUSPENDED,true"})
    void hidesNonPublishedDetailsForEveryVisitor(String projectStatus, boolean authenticated)
            throws Exception {
        jdbcClient.sql("update public.projects set status = :status where id = :id")
                .param("status", projectStatus).param("id", PROJECT_ID).update();
        String missing = mockMvc.perform(publicRequest("missing-project", authenticated))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (String reference : new String[] {"eco-pack", PROJECT_ID.toString()}) {
            String unavailable = mockMvc.perform(publicRequest(reference, authenticated)
                            .queryParam("status", "PUBLISHED"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(objectMapper.readTree(unavailable).path("detail"))
                    .isEqualTo(objectMapper.readTree(missing).path("detail"));
            assertThat(unavailable).doesNotContain("EcoPack", projectStatus);
        }
    }

    @ParameterizedTest
    @CsvSource({"INVITED,false", "INVITED,true", "LEFT,false", "LEFT,true",
            "REMOVED,false", "REMOVED,true"})
    void excludesNonActiveMembershipsEvenWithAProfile(String membershipStatus, boolean authenticated)
            throws Exception {
        jdbcClient.sql("""
                update public.project_members set membership_status = :status,
                    invitee_email = 'invited@example.test', invite_token_digest = 'test-invite-digest'
                where profile_id = :id
                """).param("status", membershipStatus).param("id", PUBLIC_PROFILE_ID).update();
        mockMvc.perform(publicRequest("eco-pack", authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].profileId")
                        .value(SECOND_PUBLIC_PROFILE_ID.toString()));
    }

    @Test
    void openingPublicDetailsDoesNotChangeTheExistingFeedContract() throws Exception {
        String before = mockMvc.perform(get("/api/v1/startups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(publicRequest("eco-pack", false)).andExpect(status().isOk());
        mockMvc.perform(publicRequest("draft-project", true)).andExpect(status().isNotFound());
        String after = mockMvc.perform(get("/api/v1/startups"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(after)).isEqualTo(objectMapper.readTree(before));
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void hidesDatabaseFailureDetailsFromEveryVisitor(boolean authenticated) throws Exception {
        jdbcClient.sql("drop table public.project_members").update();
        String body = mockMvc.perform(publicRequest("eco-pack", authenticated))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The data store is temporarily unavailable."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("project_members", "select", "jdbc", "password", "PSQL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN"})
    void publicationRequestAndArchiveKeepTheProjectPrivate(String role) throws Exception {
        jdbcClient.sql("update public.projects set status = 'DRAFT' where id = :id")
                .param("id", PROJECT_ID).update();
        setTeamRole(role, "ACTIVE");
        Map<String, Object> auditBefore = reviewAudit();
        assertProjectHidden();

        mockMvc.perform(lifecycleRequest("publication-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(lifecycleState()).isEqualTo(new ProjectLifecycleState("PENDING_REVIEW", null, null));
        assertThat(reviewAudit()).isEqualTo(auditBefore);
        assertProjectHidden();
        mockMvc.perform(lifecycleRequest("publication-request")).andExpect(status().isBadRequest());

        mockMvc.perform(lifecycleRequest("archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        ProjectLifecycleState archived = lifecycleState();
        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(archived.archivedAt()).isNotNull();
        assertThat(archived.publishedAt()).isNull();
        assertThat(reviewAudit()).isEqualTo(auditBefore);
        assertProjectHidden();
        mockMvc.perform(lifecycleRequest("publication-request")).andExpect(status().isBadRequest());
        mockMvc.perform(lifecycleRequest("archive")).andExpect(status().isBadRequest());
        assertThat(lifecycleState()).isEqualTo(archived);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ADMIN"})
    void archivingAPublishedProjectRemovesItFromPublicReadsAndPreservesReview(String role)
            throws Exception {
        Instant publishedAt = Instant.parse("2026-09-01T12:00:00Z");
        jdbcClient.sql("""
                update public.projects set published_at = :publishedAt,
                    reviewed_at = :reviewedAt, reviewed_by_profile_id = :reviewer
                where id = :id
                """)
                .param("publishedAt", publishedAt.atOffset(ZoneOffset.UTC))
                .param("reviewedAt", publishedAt.minusSeconds(60).atOffset(ZoneOffset.UTC))
                .param("reviewer", PRIVATE_PROFILE_ID).param("id", PROJECT_ID).update();
        setTeamRole(role, "ACTIVE");
        Map<String, Object> auditBefore = reviewAudit();
        mockMvc.perform(publicRequest("eco-pack", false)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/startups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(hasItem(PROJECT_ID.toString())));

        Instant beforeArchive = Instant.now();
        mockMvc.perform(lifecycleRequest("archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        ProjectLifecycleState archived = lifecycleState();
        assertThat(archived.publishedAt()).isEqualTo(publishedAt);
        assertThat(archived.archivedAt()).isBetween(beforeArchive, Instant.now());
        assertThat(reviewAudit()).isEqualTo(auditBefore);
        assertProjectHidden();
    }

    @ParameterizedTest
    @CsvSource({"MEMBER, ACTIVE", "OWNER, INVITED", "ADMIN, LEFT", "OWNER, REMOVED"})
    void deniedTeamActionsLeaveProjectAndAuditUntouched(String role, String membershipStatus)
            throws Exception {
        setTeamRole(role, membershipStatus);
        ProjectLifecycleState before = lifecycleState();
        Map<String, Object> auditBefore = reviewAudit();
        for (String action : new String[] {"publication-request", "archive"}) {
            mockMvc.perform(lifecycleRequest(action))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("active OWNER or ADMIN membership is required."));
        }
        assertThat(lifecycleState()).isEqualTo(before);
        assertThat(reviewAudit()).isEqualTo(auditBefore);
        mockMvc.perform(publicRequest("eco-pack", false)).andExpect(status().isOk());
    }

    @Test
    void directPublicationAndReviewFieldsAreRejectedWithoutPersisting() throws Exception {
        jdbcClient.sql("update public.projects set status = 'DRAFT' where id = :id")
                .param("id", PROJECT_ID).update();
        setTeamRole("OWNER", "ACTIVE");
        ProjectLifecycleState before = lifecycleState();
        Map<String, Object> auditBefore = reviewAudit();
        for (String action : new String[] {"publication-request", "archive"}) {
            mockMvc.perform(lifecycleRequest(action).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"PUBLISHED\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(lifecycleRequest(action).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reviewNote\":\"approved\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(lifecycleState()).isEqualTo(before);
        assertThat(reviewAudit()).isEqualTo(auditBefore);
        assertProjectHidden();
    }

    private void assertProjectHidden() throws Exception {
        for (boolean authenticated : new boolean[] {false, true}) {
            for (String reference : new String[] {"eco-pack", PROJECT_ID.toString()}) {
                mockMvc.perform(publicRequest(reference, authenticated))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.data").doesNotExist());
            }
            MockHttpServletRequestBuilder feed = get("/api/v1/startups");
            if (authenticated) {
                feed.header("Authorization", "Bearer " + signedToken());
            }
            mockMvc.perform(feed)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].id").value(not(hasItem(PROJECT_ID.toString()))));
        }
    }

    private void setTeamRole(String role, String membershipStatus) {
        jdbcClient.sql("""
                update public.project_members set role = :role, membership_status = :status
                where project_id = :project and profile_id = :profile
                """)
                .param("role", role).param("status", membershipStatus)
                .param("project", PROJECT_ID).param("profile", PUBLIC_PROFILE_ID).update();
    }

    private ProjectLifecycleState lifecycleState() {
        return lifecycleRepository.findForActor(PROJECT_ID, PUBLIC_PROFILE_ID).orElseThrow().state();
    }

    private Map<String, Object> reviewAudit() {
        return jdbcClient.sql("""
                select review_note, reviewed_by_profile_id, reviewed_at from public.projects where id = :id
                """).param("id", PROJECT_ID).query().singleRow();
    }

    private static MockHttpServletRequestBuilder lifecycleRequest(String action) throws Exception {
        return post("/api/v1/projects/" + PROJECT_ID + "/" + action)
                .header("Authorization", "Bearer " + signedToken());
    }

    private static MockHttpServletRequestBuilder publicRequest(String reference, boolean authenticated)
            throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/projects/" + reference);
        if (!authenticated) {
            return request;
        }
        return request.header("Authorization", "Bearer " + signedToken());
    }

    private static String signedToken() throws Exception {
        Date issuedAt = new Date();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test.supabase.local/auth/v1")
                .subject(PUBLIC_PROFILE_ID.toString())
                .audience("authenticated")
                .claim("role", "authenticated")
                .issueTime(issuedAt)
                .expirationTime(new Date(issuedAt.getTime() + 300_000))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(new SecretKeySpec(
                "test-secret-that-is-long-enough-for-local-tests".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256")));
        return token.serialize();
    }

    private static void assertFields(JsonNode node, String... fields) {
        assertThat(node.properties().stream().map(java.util.Map.Entry::getKey)
                .collect(Collectors.toSet())).isEqualTo(Set.of(fields));
    }

    @Test
    void doesNotReturnUnpublishedProjectsOrProjectsWithInactiveSectors() {
        assertThat(repository.findPublishedByReference("draft-project")).isEmpty();
        assertThat(repository.findPublishedByReference("pending-project")).isEmpty();
        assertThat(repository.findPublishedByReference("rejected-project")).isEmpty();
        assertThat(repository.findPublishedByReference("archived-project")).isEmpty();
        assertThat(repository.findPublishedByReference("suspended-project")).isEmpty();
        assertThat(repository.findPublishedByReference("inactive-sector-project")).isEmpty();
        assertThat(repository.findPublishedByReference(DRAFT_PROJECT_ID.toString())).isEmpty();
        assertThat(repository.findPublishedByReference("not-a-project")).isEmpty();
    }
}
