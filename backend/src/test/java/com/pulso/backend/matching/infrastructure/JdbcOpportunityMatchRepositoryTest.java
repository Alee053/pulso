package com.pulso.backend.matching.infrastructure;

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

import com.pulso.backend.matching.domain.MatchOpportunity;
import com.pulso.backend.matching.domain.MatchProfile;
import com.pulso.backend.matching.domain.OpportunityMatch;
import com.pulso.backend.matching.domain.OpportunityMatcher;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JdbcOpportunityMatchRepositoryTest {

    private static final UUID SECTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INACTIVE_SECTOR_ID = UUID.fromString("12121212-1212-1212-1212-121212121212");
    private static final UUID TAG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INACTIVE_TAG_ID = UUID.fromString("23232323-2323-2323-2323-232323232323");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRAFT_PROJECT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID HIDDEN_SECTOR_PROJECT_ID = UUID.fromString("45454545-4545-4545-4545-454545454545");
    private static final UUID OLD_OPPORTUNITY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID NEW_OPPORTUNITY_ID = UUID.fromString("56565656-5656-5656-5656-565656565656");
    private static final UUID PAUSED_OPPORTUNITY_ID = UUID.fromString("57575757-5757-5757-5757-575757575757");
    private static final UUID JAVA_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID FIGMA_ID = UUID.fromString("67676767-6767-6767-6767-676767676767");
    private static final UUID INACTIVE_SKILL_ID = UUID.fromString("68686868-6868-6868-6868-686868686868");
    private static final UUID PROFILE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID BARE_PROFILE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DELETED_PROFILE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private JdbcOpportunityMatchRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void setUp() {
        jdbcClient.sql("drop table if exists public.opportunity_skills, public.project_opportunities, "
                + "public.profile_skills, public.profile_tags, public.profile_preferences, "
                + "public.project_tags, public.projects, public.skills, public.tags, "
                + "public.sectors, public.profiles cascade").update();
        jdbcClient.sql("""
                create table public.profiles (
                  id uuid primary key,
                  city text,
                  department text,
                  onboarding_completed_at timestamptz,
                  deleted_at timestamptz
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_preferences (
                  profile_id uuid primary key references public.profiles (id),
                  intentions jsonb not null default '[]'::jsonb,
                  preferred_sectors jsonb not null default '[]'::jsonb,
                  preferred_stages jsonb not null default '[]'::jsonb
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
                create table public.skills (
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
                  stage text not null,
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
                create table public.profile_tags (
                  profile_id uuid not null references public.profiles (id),
                  tag_id uuid not null references public.tags (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.profile_skills (
                  profile_id uuid not null references public.profiles (id),
                  skill_id uuid not null references public.skills (id)
                )
                """).update();
        jdbcClient.sql("""
                create table public.project_opportunities (
                  id uuid primary key,
                  project_id uuid not null references public.projects (id),
                  opportunity_type text not null,
                  title text not null,
                  status text not null,
                  city text,
                  department text,
                  remote_allowed boolean not null,
                  created_at timestamptz not null
                )
                """).update();
        jdbcClient.sql("""
                create table public.opportunity_skills (
                  opportunity_id uuid not null references public.project_opportunities (id),
                  skill_id uuid not null references public.skills (id),
                  importance text not null
                )
                """).update();

        jdbcClient.sql("""
                insert into public.sectors (id, slug, name, is_active) values
                  ('%s', 'sostenibilidad', 'Sostenibilidad', true),
                  ('%s', 'inactivo', 'Sector inactivo', false)
                """.formatted(SECTOR_ID, INACTIVE_SECTOR_ID)).update();
        jdbcClient.sql("""
                insert into public.tags (id, slug, name, is_active) values
                  ('%s', 'impacto', 'Impacto', true),
                  ('%s', 'oculto', 'Oculto', false)
                """.formatted(TAG_ID, INACTIVE_TAG_ID)).update();
        jdbcClient.sql("""
                insert into public.skills (id, slug, name, is_active) values
                  ('%s', 'java', 'Java', true),
                  ('%s', 'figma', 'Figma', true),
                  ('%s', 'interna', 'Interna', false)
                """.formatted(JAVA_ID, FIGMA_ID, INACTIVE_SKILL_ID)).update();
        jdbcClient.sql("""
                insert into public.projects (id, slug, stage, sector_id, status) values
                  ('%s', 'eco-pack', 'MVP', '%s', 'PUBLISHED'),
                  ('%s', 'borrador', 'MVP', '%s', 'DRAFT'),
                  ('%s', 'sector-oculto', 'MVP', '%s', 'PUBLISHED')
                """.formatted(PROJECT_ID, SECTOR_ID, DRAFT_PROJECT_ID, SECTOR_ID,
                HIDDEN_SECTOR_PROJECT_ID, INACTIVE_SECTOR_ID)).update();
        jdbcClient.sql("""
                insert into public.project_tags (project_id, tag_id) values ('%s', '%s'), ('%s', '%s')
                """.formatted(PROJECT_ID, TAG_ID, PROJECT_ID, INACTIVE_TAG_ID)).update();
        jdbcClient.sql("""
                insert into public.project_opportunities (
                  id, project_id, opportunity_type, title, status, city, department, remote_allowed, created_at
                ) values
                  ('%s', '%s', 'TALENT', 'Antigua', 'OPEN', 'Cochabamba', 'Cochabamba', false, '2026-01-01T00:00:00Z'),
                  ('%s', '%s', 'COLLABORATION', 'Nueva', 'OPEN', null, null, true, '2026-02-01T00:00:00Z'),
                  ('%s', '%s', 'TALENT', 'Pausada', 'PAUSED', null, null, false, '2026-03-01T00:00:00Z'),
                  (gen_random_uuid(), '%s', 'TALENT', 'De borrador', 'OPEN', null, null, false, '2026-03-01T00:00:00Z')
                """.formatted(OLD_OPPORTUNITY_ID, PROJECT_ID, NEW_OPPORTUNITY_ID, PROJECT_ID,
                PAUSED_OPPORTUNITY_ID, PROJECT_ID, DRAFT_PROJECT_ID)).update();
        jdbcClient.sql("""
                insert into public.opportunity_skills (opportunity_id, skill_id, importance) values
                  ('%s', '%s', 'REQUIRED'),
                  ('%s', '%s', 'PREFERRED'),
                  ('%s', '%s', 'REQUIRED')
                """.formatted(OLD_OPPORTUNITY_ID, JAVA_ID, OLD_OPPORTUNITY_ID, FIGMA_ID,
                OLD_OPPORTUNITY_ID, INACTIVE_SKILL_ID)).update();

        jdbcClient.sql("""
                insert into public.profiles (id, city, department, onboarding_completed_at, deleted_at) values
                  ('%s', 'Cochabamba', 'Cochabamba', '2026-10-01T00:00:00Z', null),
                  ('%s', null, null, null, null),
                  ('%s', 'La Paz', 'La Paz', null, '2026-10-01T00:00:00Z')
                """.formatted(PROFILE_ID, BARE_PROFILE_ID, DELETED_PROFILE_ID)).update();
        jdbcClient.sql("""
                insert into public.profile_preferences (profile_id, intentions, preferred_sectors, preferred_stages)
                values ('%s', '["WORK","COLLABORATE"]', '["sostenibilidad"]', '["MVP"]')
                """.formatted(PROFILE_ID)).update();
        jdbcClient.sql("insert into public.profile_tags (profile_id, tag_id) values ('%s', '%s')"
                .formatted(PROFILE_ID, TAG_ID)).update();
        jdbcClient.sql("insert into public.profile_skills (profile_id, skill_id) values ('%s', '%s')"
                .formatted(PROFILE_ID, JAVA_ID)).update();
    }

    @Test
    void listsOnlyTheOpenOpportunitiesOfAPublishedProjectNewestFirst() {
        List<MatchOpportunity> byId = repository.findOpenOpportunities(PROJECT_ID.toString()).orElseThrow();
        List<MatchOpportunity> bySlug = repository.findOpenOpportunities("eco-pack").orElseThrow();

        assertThat(byId).extracting(MatchOpportunity::title).containsExactly("Nueva", "Antigua");
        assertThat(bySlug).isEqualTo(byId);
    }

    @Test
    void loadsTheProjectSectorStageAndActiveTagsForEveryOpportunity() {
        MatchOpportunity opportunity = repository.findOpenOpportunities("eco-pack").orElseThrow().get(1);

        assertThat(opportunity.sector().slug()).isEqualTo("sostenibilidad");
        assertThat(opportunity.stage()).isEqualTo("MVP");
        assertThat(opportunity.tags()).extracting(MatchOpportunity.Tag::name).containsExactly("Impacto");
        assertThat(opportunity.city()).isEqualTo("Cochabamba");
        assertThat(opportunity.remoteAllowed()).isFalse();
    }

    @Test
    void loadsOnlyActiveSkillsWithTheirImportance() {
        MatchOpportunity withSkills = repository.findOpenOpportunities("eco-pack").orElseThrow().get(1);
        MatchOpportunity withoutSkills = repository.findOpenOpportunities("eco-pack").orElseThrow().get(0);

        assertThat(withSkills.skills()).extracting(MatchOpportunity.Skill::name)
                .containsExactly("Figma", "Java");
        assertThat(withSkills.skills()).extracting(MatchOpportunity.Skill::required)
                .containsExactly(false, true);
        assertThat(withoutSkills.skills()).isEmpty();
    }

    @Test
    void doesNotExposeProjectsThatAreNotPubliclyAvailable() {
        assertThat(repository.findOpenOpportunities("borrador")).isEmpty();
        assertThat(repository.findOpenOpportunities(DRAFT_PROJECT_ID.toString())).isEmpty();
        assertThat(repository.findOpenOpportunities("sector-oculto")).isEmpty();
        assertThat(repository.findOpenOpportunities("no-existe")).isEmpty();
    }

    @Test
    void returnsAnEmptyListWhenAPublishedProjectHasNoOpenOpportunities() {
        jdbcClient.sql("update public.project_opportunities set status = 'CLOSED'").update();

        assertThat(repository.findOpenOpportunities("eco-pack")).contains(List.of());
    }

    @Test
    void loadsTheOnboardingDataOfTheProfile() {
        MatchProfile profile = repository.findProfile(PROFILE_ID).orElseThrow();

        assertThat(profile.onboardingCompleted()).isTrue();
        assertThat(profile.intentions()).containsExactlyInAnyOrder("WORK", "COLLABORATE");
        assertThat(profile.preferredSectors()).containsExactly("sostenibilidad");
        assertThat(profile.preferredStages()).containsExactly("MVP");
        assertThat(profile.tagIds()).containsExactly(TAG_ID);
        assertThat(profile.skillIds()).containsExactly(JAVA_ID);
        assertThat(profile.city()).isEqualTo("Cochabamba");
    }

    @Test
    void loadsAProfileWithoutOnboardingAsEmptyData() {
        MatchProfile profile = repository.findProfile(BARE_PROFILE_ID).orElseThrow();

        assertThat(profile.onboardingCompleted()).isFalse();
        assertThat(profile.intentions()).isEmpty();
        assertThat(profile.preferredSectors()).isEmpty();
        assertThat(profile.tagIds()).isEmpty();
        assertThat(profile.skillIds()).isEmpty();
    }

    @Test
    void ignoresMissingAndDeletedProfiles() {
        assertThat(repository.findProfile(UUID.randomUUID())).isEmpty();
        assertThat(repository.findProfile(DELETED_PROFILE_ID)).isEmpty();
    }

    @Test
    void matchesStoredDataEndToEnd() {
        List<MatchOpportunity> opportunities = repository.findOpenOpportunities("eco-pack").orElseThrow();
        MatchProfile profile = repository.findProfile(PROFILE_ID).orElseThrow();

        OpportunityMatch talent = OpportunityMatcher.match(profile, opportunities.get(1));

        assertThat(talent.title()).isEqualTo("Antigua");
        assertThat(talent.score()).isEqualTo(88);
        assertThat(talent.missingProfileFactors()).isEmpty();
    }
}
