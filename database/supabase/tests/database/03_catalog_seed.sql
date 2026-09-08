begin;

select plan(19);

select ok(
  (select count(*) >= 2 from public.sectors where is_active),
  'local seed contains at least two active sectors'
);
select is(
  (select count(*) from public.sectors),
  (select count(distinct slug) from public.sectors),
  'sector slugs are unique'
);
select is(
  (select count(*) from public.sectors where is_active),
  (select count(*) from public.sectors),
  'seeded sectors are active'
);
select ok(
  (select count(*) >= 8 from public.tags where is_active),
  'local seed contains at least eight active tags'
);
select is(
  (select count(*) from public.tags where is_active),
  (select count(*) from public.tags),
  'seeded tags are active'
);
select is(
  (select count(*) from public.tags),
  (select count(distinct slug) from public.tags),
  'tag slugs are unique'
);
select ok(
  (select count(*) >= 8 from public.skills where is_active),
  'local seed contains at least eight active skills'
);
select is(
  (select count(*) from public.skills where is_active),
  (select count(*) from public.skills),
  'seeded skills are active'
);
select is(
  (select count(*) from public.skills),
  (select count(distinct slug) from public.skills),
  'skill slugs are unique'
);
select is(
  (select count(*)
   from public.sectors
   where slug in (
     'sostenibilidad',
     'energia-limpia',
     'agroindustria',
     'educacion',
     'salud',
     'tecnologia'
   )),
  6::bigint,
  'sector slugs remain stable'
);
select is(
  (select count(*)
   from public.tags
   where slug in (
     'innovacion',
     'impacto-social',
     'economia-circular',
     'inteligencia-artificial',
     'comercio-electronico',
     'inclusion-financiera',
     'agricultura',
     'talento-local'
   )),
  8::bigint,
  'tag slugs remain stable'
);
select is(
  (select count(*)
   from public.skills
   where slug in (
     'desarrollo-web',
     'desarrollo-movil',
     'analisis-de-datos',
     'marketing-digital',
     'ventas',
     'gestion-de-proyectos',
     'diseno-ux',
     'agricultura-sostenible'
   )),
  8::bigint,
  'skill slugs remain stable'
);

create temporary table seed_catalog_counts on commit drop as
select
  (select count(*) from public.sectors) as sectors_count,
  (select count(*) from public.tags) as tags_count,
  (select count(*) from public.skills) as skills_count,
  (select count(*) from public.projects) as projects_count,
  (select count(*) from public.projects where status = 'PUBLISHED') as published_projects_count;

\ir ../../seed.sql

select is(
  (select count(*) from public.sectors),
  (select sectors_count from seed_catalog_counts),
  're-running the seed does not duplicate sectors'
);
select is(
  (select count(*) from public.tags),
  (select tags_count from seed_catalog_counts),
  're-running the seed does not duplicate tags'
);
select is(
  (select count(*) from public.skills),
  (select skills_count from seed_catalog_counts),
  're-running the seed does not duplicate skills'
);
select is(
  (select count(*) from public.projects),
  (select projects_count from seed_catalog_counts),
  're-running the seed does not duplicate projects'
);
select is(
  (select count(*) from public.projects where status = 'PUBLISHED'),
  (select published_projects_count from seed_catalog_counts),
  're-running the seed keeps published projects intact'
);
select is(
  (select count(*) from public.projects where status = 'PUBLISHED' and sector_id is not null),
  (select count(*) from public.projects where status = 'PUBLISHED'),
  'published projects reference a canonical sector'
);
select is(
  (select count(*)
   from public.projects p
   join public.sectors s on s.id = p.sector_id
   where p.status = 'PUBLISHED'),
  (select count(*) from public.projects where status = 'PUBLISHED'),
  'published projects resolve their canonical sector'
);

select * from finish();
rollback;
