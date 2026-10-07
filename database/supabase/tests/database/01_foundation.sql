begin;

select plan(21);

select has_table('public', 'profiles', 'profiles table exists');
select has_table('public', 'sectors', 'sectors table exists');
select has_table('public', 'tags', 'tags table exists');
select has_table('public', 'skills', 'skills table exists');
select has_table('public', 'projects', 'projects table exists');
select has_table('public', 'project_members', 'project_members table exists');
select has_index(
  'public',
  'projects',
  'projects_status_published_at_id_idx',
  'published project ordering has an index'
);
select ok(
  (select count(*) >= 2 from public.sectors where is_active),
  'local seed contains at least two active sectors'
);

select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'tags'
      and policyname = 'Active tags are readable by authenticated users'),
  1::bigint,
  'active tags have an authenticated read policy'
);
select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'skills'
      and policyname = 'Active skills are readable by authenticated users'),
  1::bigint,
  'active skills have an authenticated read policy'
);
select has_index(
  'public',
  'sectors',
  'sectors_active_catalog_order_idx',
  'active sectors have a stable catalog ordering index'
);
select has_index(
  'public',
  'tags',
  'tags_active_catalog_order_idx',
  'active tags have a stable catalog ordering index'
);
select has_index(
  'public',
  'skills',
  'skills_active_catalog_order_idx',
  'active skills have a stable catalog ordering index'
);

select is(
  (select count(*) from public.projects where status = 'PUBLISHED'),
  2::bigint,
  'local seed contains two published projects'
);
select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'projects'
      and policyname = 'Published projects are publicly readable'),
  1::bigint,
  'published projects have a public read policy'
);
select is(
  (select relrowsecurity from pg_class
    where oid = 'public.projects'::regclass),
  true,
  'row level security is enabled for projects'
);
select is(
  has_table_privilege('anon', 'public.projects', 'select'),
  false,
  'public project rows are not exposed through a direct table grant'
);
insert into public.projects (
  id,
  slug,
  name,
  tagline,
  city,
  stage,
  sector_id,
  status,
  published_at
)
select fixture.id,
       fixture.slug,
       fixture.name,
       fixture.tagline,
       fixture.city,
       fixture.stage,
       sector.id,
       fixture.status,
       fixture.published_at
from (
  values
    ('f0000000-0000-0000-0000-000000000001'::uuid, 'pul73-published',
     'Proyecto PUL-73 publicado', 'Visible', 'Cochabamba', 'MVP', 'PUBLISHED',
     '2026-10-01 12:00:00+00'::timestamptz),
    ('f0000000-0000-0000-0000-000000000002'::uuid, 'pul73-draft',
     'Proyecto PUL-73 borrador', 'Privado', 'Cochabamba', 'IDEA', 'DRAFT', null::timestamptz),
    ('f0000000-0000-0000-0000-000000000003'::uuid, 'pul73-pending',
     'Proyecto PUL-73 en revisión', 'Pendiente', 'Cochabamba', 'IDEA', 'PENDING_REVIEW', null::timestamptz),
    ('f0000000-0000-0000-0000-000000000004'::uuid, 'pul73-rejected',
     'Proyecto PUL-73 rechazado', 'No publicado', 'Cochabamba', 'IDEA', 'REJECTED', null::timestamptz),
    ('f0000000-0000-0000-0000-000000000005'::uuid, 'pul73-archived',
     'Proyecto PUL-73 archivado', 'Retirado', 'Cochabamba', 'MVP', 'ARCHIVED',
     '2026-09-01 12:00:00+00'::timestamptz),
    ('f0000000-0000-0000-0000-000000000006'::uuid, 'pul73-suspended',
     'Proyecto PUL-73 suspendido', 'No disponible', 'Cochabamba', 'MVP', 'SUSPENDED',
     '2026-09-01 12:00:00+00'::timestamptz)
) as fixture(id, slug, name, tagline, city, stage, status, published_at)
cross join lateral (
  select id
  from public.sectors
  where is_active
  order by sort_order, id
  limit 1
) as sector;

-- Temporarily grant SELECT so this transaction can exercise the anon RLS policy.
grant select on public.projects to anon;
set local role anon;
select is(
  (select count(*) from public.projects where slug = 'pul73-published'),
  1::bigint,
  'anon can read a published project through RLS'
);
select is(
  (select count(*) from public.projects
    where slug in (
      'pul73-draft',
      'pul73-pending',
      'pul73-rejected',
      'pul73-archived',
      'pul73-suspended'
    )),
  0::bigint,
  'anon cannot read unpublished, archived, or suspended projects through RLS'
);
reset role;

select is(
  (select count(*) from pg_constraint
    where conrelid = 'public.profiles'::regclass
      and confrelid = 'auth.users'::regclass
      and contype = 'f'),
  1::bigint,
  'profiles remain linked to Supabase Auth users'
);
select is(
  (select count(*) from pg_constraint
    where conrelid = 'public.project_members'::regclass
      and confrelid = 'public.profiles'::regclass
      and contype = 'f'),
  2::bigint,
  'project memberships reference profiles for member and creator'
);

select * from finish();
rollback;
