begin;

select plan(39);

select has_table('public', 'connection_intents', 'connection requests use the canonical table');
select has_column('public', 'connection_intents', 'from_profile_id', 'the sender is persisted');
select has_column('public', 'connection_intents', 'project_id', 'the project context is persisted');
select has_column('public', 'connection_intents', 'opportunity_id', 'the opportunity context is optional');
select has_column('public', 'connection_intents', 'reason', 'the connection reason is persisted');
select has_column('public', 'connection_intents', 'message', 'the optional message is persisted');
select has_column('public', 'connection_intents', 'status', 'the request lifecycle is persisted');
select has_column('public', 'connection_intents', 'outcome_type', 'the canonical optional outcome field exists');
select has_column('public', 'connection_intents', 'responded_by_profile_id', 'the canonical response actor field exists');
select has_column('public', 'connection_intents', 'created_at', 'the creation time is persisted');
select has_column('public', 'connection_intents', 'responded_at', 'the canonical response time field exists');
select has_column('public', 'connection_intents', 'confirmed_at', 'the canonical confirmation time field exists');
select has_index(
  'public', 'connection_intents', 'connection_intents_project_status_created_idx',
  'project request listings have a bounded lookup index'
);
select has_index(
  'public', 'connection_intents', 'connection_intents_pending_project_context_uidx',
  'a pending project-only context has an atomic unique guard'
);
select has_index(
  'public', 'connection_intents', 'connection_intents_pending_opportunity_context_uidx',
  'a pending opportunity context has an atomic unique guard'
);
select has_index(
  'public', 'connection_intents', 'connection_intents_from_profile_idx',
  'sender queries have an index'
);
select has_index(
  'public', 'connection_intents', 'connection_intents_opportunity_idx',
  'opportunity references have an index'
);
select has_index(
  'public', 'project_opportunities', 'project_opportunities_id_project_id_key',
  'opportunity IDs can be constrained together with their project IDs'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = to_regclass('public.connection_intents')
     and confrelid = 'public.profiles'::regclass
     and contype = 'f'),
  2::bigint,
  'sender and response actor reference profiles'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = to_regclass('public.connection_intents')
     and confrelid = 'public.projects'::regclass
     and contype = 'f'),
  1::bigint,
  'each request references a project'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.connection_intents'::regclass
     and confrelid = 'public.project_opportunities'::regclass
     and contype = 'f'),
  1::bigint,
  'an opportunity reference uses the project-scoped composite foreign key'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.connection_intents'::regclass
     and conname in (
       'connection_intents_reason',
       'connection_intents_status',
       'connection_intents_outcome_type'
     )
     and contype = 'c'),
  3::bigint,
  'reason, status, and optional outcome are constrained to canonical values'
);
select is(
  (select relrowsecurity from pg_class where oid = to_regclass('public.connection_intents')),
  true,
  'row level security is enabled'
);
select is(
  has_table_privilege('anon', to_regclass('public.connection_intents'), 'select'),
  false,
  'anonymous clients cannot read request rows directly'
);
select is(
  has_table_privilege('anon', to_regclass('public.connection_intents'), 'insert'),
  false,
  'anonymous clients cannot create request rows directly'
);
select is(
  has_table_privilege('authenticated', to_regclass('public.connection_intents'), 'select'),
  false,
  'authenticated clients cannot read request rows directly'
);
select is(
  has_table_privilege('authenticated', to_regclass('public.connection_intents'), 'insert'),
  false,
  'authenticated clients cannot bypass the backend to create requests'
);

insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data,
  raw_user_meta_data, created_at, updated_at
)
values (
  'a0000000-0000-4000-8000-000000000001', 'authenticated', 'authenticated',
  'pul25-connection-sender@example.test', '$2a$10$abcdefghijklmnopqrstuu',
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"display_name":"PUL-25 sender"}'::jsonb, timezone('utc', now()), timezone('utc', now())
);

insert into public.sectors (id, slug, name)
values ('a2000000-0000-4000-8000-000000000001', 'pul25-connection-test', 'PUL-25 connection test');

insert into public.projects (
  id, slug, name, tagline, city, stage, sector_id, status, created_by_profile_id, published_at
)
values
  ('a1000000-0000-4000-8000-000000000001', 'pul25-connection-project', 'PUL-25 project', 'Connection test', 'La Paz', 'MVP', 'a2000000-0000-4000-8000-000000000001', 'PUBLISHED', 'a0000000-0000-4000-8000-000000000001', timezone('utc', now())),
  ('a1000000-0000-4000-8000-000000000002', 'pul25-connection-other-project', 'PUL-25 other project', 'Connection test', 'La Paz', 'MVP', 'a2000000-0000-4000-8000-000000000001', 'PUBLISHED', 'a0000000-0000-4000-8000-000000000001', timezone('utc', now()));

insert into public.project_opportunities (
  id, project_id, opportunity_type, title, description, status, created_by_profile_id
)
values
  ('a3000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'TALENT', 'PUL-25 opportunity one', 'Connection context fixture.', 'OPEN', 'a0000000-0000-4000-8000-000000000001'),
  ('a3000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', 'COLLABORATION', 'PUL-25 opportunity two', 'Connection context fixture.', 'OPEN', 'a0000000-0000-4000-8000-000000000001');

insert into public.connection_intents (
  id, from_profile_id, project_id, reason
)
values (
  'a4000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000001', 'COLLABORATE'
);

select is(
  (select status from public.connection_intents where id = 'a4000000-0000-4000-8000-000000000001'),
  'PENDING',
  'new requests default to PENDING'
);
select is(
  (select from_profile_id from public.connection_intents where id = 'a4000000-0000-4000-8000-000000000001'),
  'a0000000-0000-4000-8000-000000000001'::uuid,
  'the sender profile association is persisted'
);
select is(
  (select project_id from public.connection_intents where id = 'a4000000-0000-4000-8000-000000000001'),
  'a1000000-0000-4000-8000-000000000001'::uuid,
  'the project association is persisted'
);
select ok(
  (select opportunity_id is null and message is null
   from public.connection_intents where id = 'a4000000-0000-4000-8000-000000000001'),
  'project-only requests allow omitted opportunity and message values'
);

insert into public.connection_intents (
  id, from_profile_id, project_id, opportunity_id, reason, message
)
values (
  'a4000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000001',
  'WORK', 'I would like to discuss this role.'
);

select is(
  (select count(*) from public.connection_intents
   where from_profile_id = 'a0000000-0000-4000-8000-000000000001'
     and project_id = 'a1000000-0000-4000-8000-000000000001'),
  2::bigint,
  'project-only and opportunity-specific requests are different contexts'
);

insert into public.connection_intents (
  id, from_profile_id, project_id, opportunity_id, reason
)
values (
  'a4000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000002',
  'OFFER_SERVICE'
);

select is(
  (select count(*) from public.connection_intents
   where from_profile_id = 'a0000000-0000-4000-8000-000000000001'
     and project_id = 'a1000000-0000-4000-8000-000000000001'
     and opportunity_id is not null),
  2::bigint,
  'distinct open opportunities are separate contexts'
);

select throws_ok(
  $$insert into public.connection_intents (from_profile_id, project_id, reason)
    values ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'BUY')$$,
  '23505',
  'duplicate key value violates unique constraint "connection_intents_pending_project_context_uidx"',
  'a second pending project-only request is rejected'
);
select throws_ok(
  $$insert into public.connection_intents (from_profile_id, project_id, opportunity_id, reason)
    values ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'a3000000-0000-4000-8000-000000000001', 'BUY')$$,
  '23505',
  'duplicate key value violates unique constraint "connection_intents_pending_opportunity_context_uidx"',
  'a second pending request for the same opportunity is rejected'
);

update public.connection_intents
set status = 'ACCEPTED'
where id = 'a4000000-0000-4000-8000-000000000001';
insert into public.connection_intents (id, from_profile_id, project_id, reason)
values (
  'a4000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000001',
  'a1000000-0000-4000-8000-000000000001', 'BUY'
);
select is(
  (select count(*) from public.connection_intents
   where from_profile_id = 'a0000000-0000-4000-8000-000000000001'
     and project_id = 'a1000000-0000-4000-8000-000000000001'
     and status = 'PENDING' and opportunity_id is null),
  1::bigint,
  'a closed historical request does not block a new pending request'
);

select throws_ok(
  $$insert into public.connection_intents (from_profile_id, project_id, reason)
    values ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'UNKNOWN')$$,
  '23514',
  'new row for relation "connection_intents" violates check constraint "connection_intents_reason"',
  'unknown connection reasons are rejected'
);
select throws_ok(
  $$insert into public.connection_intents (from_profile_id, project_id, reason, status)
    values ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'OTHER', 'BLOCKED')$$,
  '23514',
  'new row for relation "connection_intents" violates check constraint "connection_intents_status"',
  'unknown lifecycle states are rejected'
);
select throws_ok(
  $$insert into public.connection_intents (from_profile_id, project_id, opportunity_id, reason)
    values ('a0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000002', 'a3000000-0000-4000-8000-000000000001', 'OTHER')$$,
  '23503',
  'insert or update on table "connection_intents" violates foreign key constraint "connection_intents_opportunity_project_fkey"',
  'an opportunity from another project cannot be attached'
);

select * from finish();
rollback;
