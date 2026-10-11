begin;

select plan(28);

select has_table('public', 'project_opportunities', 'project opportunities use the canonical table');
select has_column('public', 'project_opportunities', 'status', 'opportunities store their lifecycle status');
select has_column('public', 'project_opportunities', 'remote_allowed', 'opportunities store remote availability');
select has_index(
  'public',
  'project_opportunities',
  'project_opportunities_project_status_created_idx',
  'project opportunity pages have a project and status index'
);
select has_index(
  'public',
  'project_opportunities',
  'project_opportunities_type_location_idx',
  'opportunity type and location filters have an index'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.project_opportunities'::regclass
     and confrelid = 'public.projects'::regclass
     and contype = 'f'),
  1::bigint,
  'each opportunity belongs to a valid project'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.project_opportunities'::regclass
     and confrelid = 'public.profiles'::regclass
     and contype = 'f'),
  1::bigint,
  'each opportunity creator references a profile'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.interaction_events'::regclass
     and confrelid = 'public.project_opportunities'::regclass
     and contype = 'f'),
  1::bigint,
  'opportunity interaction events use a valid opportunity reference'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.project_opportunities'::regclass),
  true,
  'row level security is enabled'
);
select is(
  has_table_privilege('anon', 'public.project_opportunities', 'select'),
  false,
  'anonymous clients cannot read opportunity rows directly'
);
select is(
  has_table_privilege('authenticated', 'public.project_opportunities', 'insert'),
  false,
  'authenticated clients cannot bypass the backend to create opportunities'
);
select is(
  has_table_privilege('authenticated', 'public.project_opportunities', 'select'),
  false,
  'authenticated clients cannot read opportunity rows directly'
);

insert into auth.users (
  id,
  aud,
  role,
  email,
  encrypted_password,
  raw_app_meta_data,
  raw_user_meta_data,
  created_at,
  updated_at
)
values (
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  'authenticated',
  'authenticated',
  'opportunity-owner@example.test',
  '$2a$10$abcdefghijklmnopqrstuu',
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"display_name":"Opportunity Owner"}'::jsonb,
  timezone('utc', now()),
  timezone('utc', now())
);

select is(
  (select count(*) from public.profiles
   where id = 'dddddddd-dddd-dddd-dddd-dddddddddddd'),
  1::bigint,
  'the creator identity is a persisted profile'
);

insert into public.project_members (
  project_id, profile_id, role, membership_status, created_by_profile_id, joined_at
)
values (
  '11111111-1111-1111-1111-111111111111',
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  'OWNER',
  'ACTIVE',
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  timezone('utc', now())
);

insert into public.project_opportunities (
  project_id,
  opportunity_type,
  title,
  description,
  city,
  department,
  created_by_profile_id
)
values (
  '11111111-1111-1111-1111-111111111111',
  'TALENT',
  'PUL-18 open opportunity',
  'A structured project need.',
  'La Paz',
  'La Paz',
  'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

select is(
  (select status from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'OPEN',
  'new opportunities use the canonical OPEN default'
);
select is(
  (select remote_allowed from public.project_opportunities where title = 'PUL-18 open opportunity'),
  false,
  'remote_allowed defaults to false'
);
select is(
  (select created_by_profile_id from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'dddddddd-dddd-dddd-dddd-dddddddddddd'::uuid,
  'opportunities persist the authenticated creator reference'
);
select ok(
  (select created_at is not null from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'created_at is populated by PostgreSQL'
);
select ok(
  (select updated_at is not null from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'updated_at is populated by PostgreSQL'
);

update public.project_opportunities
set updated_at = '2000-01-01 00:00:00+00'
where title = 'PUL-18 open opportunity';
update public.projects
set status = 'DRAFT'
where id = '11111111-1111-1111-1111-111111111111';

select is(
  (select status from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'PAUSED',
  'an open opportunity pauses when its project is no longer published'
);
select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.project_opportunities where title = 'PUL-18 open opportunity'),
  'automatic pausing refreshes updated_at'
);
select throws_ok(
  $$insert into public.project_opportunities (
      project_id, opportunity_type, title, description, city, created_by_profile_id
    ) values (
      '11111111-1111-1111-1111-111111111111', 'TALENT', 'Invalid open', 'Details', 'La Paz',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'OPEN opportunities require a published project with an active sector.',
  'a draft project cannot own an open opportunity'
);

insert into public.project_opportunities (
  project_id, opportunity_type, title, description, status, remote_allowed, created_by_profile_id
)
values (
  '11111111-1111-1111-1111-111111111111', 'MENTOR', 'PUL-18 draft opportunity',
  'A draft can be prepared before publication.', 'DRAFT', true,
  'dddddddd-dddd-dddd-dddd-dddddddddddd'
);
select is(
  (select status from public.project_opportunities where title = 'PUL-18 draft opportunity'),
  'DRAFT',
  'draft opportunities can be prepared while a project is unpublished'
);

update public.projects
set status = 'PUBLISHED'
where id = '11111111-1111-1111-1111-111111111111';
insert into public.project_opportunities (
  project_id, opportunity_type, title, description, city, created_by_profile_id
)
values (
  '11111111-1111-1111-1111-111111111111', 'CUSTOMER', 'PUL-18 sector pause',
  'A public opportunity before the sector is deactivated.', 'La Paz',
  'dddddddd-dddd-dddd-dddd-dddddddddddd'
);
update public.project_opportunities
set updated_at = '2000-01-01 00:00:00+00'
where title = 'PUL-18 sector pause';
update public.sectors
set is_active = false
where id = (
  select sector_id from public.projects where id = '11111111-1111-1111-1111-111111111111'
);

select is(
  (select status from public.project_opportunities where title = 'PUL-18 sector pause'),
  'PAUSED',
  'open opportunities pause when their project sector becomes inactive'
);
select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.project_opportunities where title = 'PUL-18 sector pause'),
  'sector-triggered pausing refreshes updated_at'
);
select throws_ok(
  $$insert into public.project_opportunities (
      project_id, opportunity_type, title, description, city, created_by_profile_id
    ) values (
      '11111111-1111-1111-1111-111111111111', 'TALENT', 'Inactive sector open', 'Details', 'La Paz',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'OPEN opportunities require a published project with an active sector.',
  'a project with an inactive sector cannot own an open opportunity'
);
select throws_ok(
  $$insert into public.project_opportunities (
      project_id, opportunity_type, title, description, status, remote_allowed,
      created_by_profile_id
    ) values (
      '11111111-1111-1111-1111-111111111111', 'UNKNOWN', 'Invalid type', 'Details', 'DRAFT', true,
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_opportunities" violates check constraint "project_opportunities_type"',
  'unknown opportunity types are rejected'
);
select throws_ok(
  $$insert into public.project_opportunities (
      project_id, opportunity_type, title, description, status, remote_allowed,
      created_by_profile_id
    ) values (
      '11111111-1111-1111-1111-111111111111', 'TALENT', '   ', 'Details', 'DRAFT', true,
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_opportunities" violates check constraint "project_opportunities_title_not_blank"',
  'blank opportunity titles are rejected'
);
select throws_ok(
  $$insert into public.project_opportunities (
      project_id, opportunity_type, title, description, status, remote_allowed,
      created_by_profile_id
    ) values (
      '11111111-1111-1111-1111-111111111111', 'TALENT', 'Invalid status', 'Details', 'PENDING', true,
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_opportunities" violates check constraint "project_opportunities_status"',
  'unknown opportunity states are rejected'
);

select * from finish();
rollback;
