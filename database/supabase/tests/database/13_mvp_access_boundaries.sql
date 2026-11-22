begin;

-- PUL-32: cross-cutting access boundaries for the MVP schema. Product data is
-- read and written through the Spring API; the browser uses Supabase for Auth
-- only. These checks fail when a new table ships without RLS or when a public
-- role keeps privileges outside the documented allowlist.

select plan(24);

-- Structural checks over every application table in the public schema.
select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
   from pg_class c
   join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('r', 'p')
     and not c.relrowsecurity),
  '{}'::text[],
  'every public application table has row level security enabled'
);

select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
   from pg_class c
   join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('r', 'p')
     and (
       has_table_privilege('anon', c.oid, 'select')
       or has_table_privilege('anon', c.oid, 'insert')
       or has_table_privilege('anon', c.oid, 'update')
       or has_table_privilege('anon', c.oid, 'delete')
       or has_table_privilege('anon', c.oid, 'truncate')
     )),
  '{}'::text[],
  'anon has no direct privileges on public application tables'
);

select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
   from pg_class c
   join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('r', 'p')
     and has_table_privilege('authenticated', c.oid, 'select')),
  array[
    'profile_preferences',
    'profiles',
    'project_members',
    'sectors',
    'skills',
    'tags'
  ]::text[],
  'authenticated can select only the documented own-row and catalog tables'
);

select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
   from pg_class c
   join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('r', 'p')
     and (
       has_table_privilege('authenticated', c.oid, 'insert')
       or has_table_privilege('authenticated', c.oid, 'update')
       or has_table_privilege('authenticated', c.oid, 'delete')
       or has_table_privilege('authenticated', c.oid, 'truncate')
     )),
  '{}'::text[],
  'authenticated has no table-wide write privileges'
);

select is(
  (select coalesce(array_agg(distinct cp.table_name::text order by cp.table_name::text), '{}')
   from information_schema.column_privileges cp
   join pg_class c on c.relname = cp.table_name and c.relkind in ('r', 'p')
   join pg_namespace n on n.oid = c.relnamespace and n.nspname = cp.table_schema
   where cp.table_schema = 'public'
     and cp.grantee = 'authenticated'
     and cp.privilege_type in ('INSERT', 'UPDATE')),
  array['profile_preferences', 'profiles']::text[],
  'column-level writes are limited to the own profile and own preferences'
);

select is(
  (select coalesce(array_agg(t.tablename::text order by t.tablename), '{}')
   from (
     values
       ('profiles'), ('profile_preferences'), ('project_members'),
       ('sectors'), ('skills'), ('tags')
   ) as t(tablename)
   where not exists (
     select 1
     from pg_policies p
     where p.schemaname = 'public'
       and p.tablename = t.tablename
       and p.cmd in ('SELECT', 'ALL')
   )),
  '{}'::text[],
  'every table readable by authenticated has a select policy'
);

-- Fixtures: two people, a published project and a draft project.
insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data,
  raw_user_meta_data, created_at, updated_at
)
values
  (
    'c3200000-0000-4000-8000-000000000001', 'authenticated', 'authenticated',
    'pul32-draft-owner@example.test', '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"PUL-32 draft owner"}'::jsonb,
    timezone('utc', now()), timezone('utc', now())
  ),
  (
    'c3200000-0000-4000-8000-000000000002', 'authenticated', 'authenticated',
    'pul32-published-owner@example.test', '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"PUL-32 published owner"}'::jsonb,
    timezone('utc', now()), timezone('utc', now())
  );

insert into public.projects (
  id, slug, name, tagline, city, stage, sector_id, status,
  created_by_profile_id, published_at
)
select fixture.id, fixture.slug, fixture.name, 'PUL-32 access fixture',
       'Cochabamba', 'MVP', sector.id, fixture.status,
       fixture.created_by_profile_id, fixture.published_at
from (
  values
    ('c3210000-0000-4000-8000-000000000001'::uuid, 'pul32-draft',
     'PUL-32 borrador', 'DRAFT',
     'c3200000-0000-4000-8000-000000000001'::uuid, null::timestamptz),
    ('c3210000-0000-4000-8000-000000000002'::uuid, 'pul32-published',
     'PUL-32 publicado', 'PUBLISHED',
     'c3200000-0000-4000-8000-000000000002'::uuid, timezone('utc', now()))
) as fixture(id, slug, name, status, created_by_profile_id, published_at)
cross join lateral (
  select id from public.sectors where is_active order by sort_order, id limit 1
) as sector;

insert into public.project_members (
  project_id, profile_id, role, membership_status, created_by_profile_id, joined_at
)
values
  ('c3210000-0000-4000-8000-000000000001', 'c3200000-0000-4000-8000-000000000001',
   'OWNER', 'ACTIVE', 'c3200000-0000-4000-8000-000000000001', timezone('utc', now())),
  ('c3210000-0000-4000-8000-000000000002', 'c3200000-0000-4000-8000-000000000002',
   'OWNER', 'ACTIVE', 'c3200000-0000-4000-8000-000000000002', timezone('utc', now()));

insert into public.project_opportunities (
  id, project_id, opportunity_type, title, description, status, created_by_profile_id
)
values (
  'c3220000-0000-4000-8000-000000000001', 'c3210000-0000-4000-8000-000000000002',
  'TALENT', 'PUL-32 oportunidad', 'Fixture de acceso directo.', 'OPEN',
  'c3200000-0000-4000-8000-000000000002'
);

-- Anonymous visitors cannot read or write product tables directly.
set local role anon;
select throws_ok(
  $$select count(*) from public.projects$$,
  '42501', null,
  'anon cannot read projects directly'
);
select throws_ok(
  $$select count(*) from public.project_members$$,
  '42501', null,
  'anon cannot read project memberships directly'
);
select throws_ok(
  $$insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
    values ('c3210000-0000-4000-8000-000000000002', 'c3200000-0000-4000-8000-000000000001',
            'OWNER', 'ACTIVE', 'c3200000-0000-4000-8000-000000000001')$$,
  '42501', null,
  'anon cannot create a project membership directly'
);
reset role;

-- An authenticated person, even a project owner, reaches product data only
-- through the backend.
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c3200000-0000-4000-8000-000000000001', true);

select throws_ok(
  $$select count(*) from public.projects$$,
  '42501', null,
  'authenticated cannot read projects directly'
);
select throws_ok(
  $$select count(*) from public.project_opportunities$$,
  '42501', null,
  'authenticated cannot read project opportunities directly'
);
select throws_ok(
  $$select count(*) from public.connection_intents$$,
  '42501', null,
  'authenticated cannot read connection requests directly'
);
select throws_ok(
  $$select count(*) from public.conversations$$,
  '42501', null,
  'authenticated cannot read conversations directly'
);
select throws_ok(
  $$select count(*) from public.messages$$,
  '42501', null,
  'authenticated cannot read messages directly'
);
select throws_ok(
  $$select count(*) from public.notifications$$,
  '42501', null,
  'authenticated cannot read notifications directly'
);
select throws_ok(
  $$select count(*) from public.reports$$,
  '42501', null,
  'authenticated cannot read reports directly'
);
select throws_ok(
  $$select count(*) from public.admin_audit_logs$$,
  '42501', null,
  'authenticated cannot read the administrative audit log directly'
);
select throws_ok(
  $$insert into public.project_members (project_id, profile_id, role, membership_status, created_by_profile_id)
    values ('c3210000-0000-4000-8000-000000000002', 'c3200000-0000-4000-8000-000000000001',
            'OWNER', 'ACTIVE', 'c3200000-0000-4000-8000-000000000001')$$,
  '42501', null,
  'authenticated cannot join another project directly'
);
select throws_ok(
  $$update public.project_members set role = 'OWNER'
    where project_id = 'c3210000-0000-4000-8000-000000000001'$$,
  '42501', null,
  'authenticated cannot change a membership role directly'
);
select throws_ok(
  $$delete from public.project_members
    where project_id = 'c3210000-0000-4000-8000-000000000002'$$,
  '42501', null,
  'authenticated cannot delete memberships directly'
);
select is(
  (select array_agg(project_id::text order by project_id) from public.project_members
   where project_id in (
     'c3210000-0000-4000-8000-000000000001',
     'c3210000-0000-4000-8000-000000000002'
   )),
  array['c3210000-0000-4000-8000-000000000001']::text[],
  'authenticated reads only its own project memberships'
);
reset role;

-- Defense in depth: if a future grant exposed these tables, RLS would still
-- hide unpublished projects and tables without policies.
grant select on public.projects, public.project_opportunities to authenticated;
set local role authenticated;
select set_config('request.jwt.claim.sub', 'c3200000-0000-4000-8000-000000000001', true);

select is(
  (select count(*) from public.projects where id = 'c3210000-0000-4000-8000-000000000001'),
  0::bigint,
  'RLS hides a draft project even from its own owner on direct access'
);
select is(
  (select count(*) from public.projects where id = 'c3210000-0000-4000-8000-000000000002'),
  1::bigint,
  'RLS keeps a published project readable'
);
select is(
  (select count(*) from public.project_opportunities),
  0::bigint,
  'project opportunities have no direct read policy'
);
reset role;

select * from finish();
rollback;
