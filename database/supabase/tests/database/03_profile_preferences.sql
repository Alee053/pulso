begin;

select plan(31);

select has_table(
  'public',
  'profile_preferences',
  'profile preferences table exists'
);
select has_column(
  'public',
  'profile_preferences',
  'intentions',
  'profile preferences expose intentions'
);
select has_column(
  'public',
  'profile_preferences',
  'preferred_sectors',
  'profile preferences expose preferred sectors'
);
select has_column(
  'public',
  'profile_preferences',
  'preferred_stages',
  'profile preferences expose preferred stages'
);

select is(
  (select is_nullable
   from information_schema.columns
   where table_schema = 'public'
     and table_name = 'profile_preferences'
     and column_name = 'intentions'),
  'NO',
  'intentions are required'
);
select is(
  (select is_nullable
   from information_schema.columns
   where table_schema = 'public'
     and table_name = 'profile_preferences'
     and column_name = 'preferred_sectors'),
  'NO',
  'preferred sectors are required'
);
select is(
  (select is_nullable
   from information_schema.columns
   where table_schema = 'public'
     and table_name = 'profile_preferences'
     and column_name = 'preferred_stages'),
  'NO',
  'preferred stages are required'
);

select is(
  (select count(*)
   from pg_constraint
   where conrelid = 'public.profile_preferences'::regclass
     and confrelid = 'public.profiles'::regclass
     and contype = 'f'),
  1::bigint,
  'profile preferences reference the owning profile'
);
select is(
  (select count(*)
   from pg_constraint
   where conrelid = 'public.profile_preferences'::regclass
     and contype = 'p'),
  1::bigint,
  'profile_id is the profile preferences primary key'
);
select is(
  (select relrowsecurity
   from pg_class
   where oid = 'public.profile_preferences'::regclass),
  true,
  'row level security is enabled for profile preferences'
);
select is(
  (select count(*)
   from pg_policies
   where schemaname = 'public'
     and tablename = 'profile_preferences'),
  3::bigint,
  'profile preferences have read, insert, and update policies'
);

select is(
  has_table_privilege('authenticated', 'public.profile_preferences', 'select'),
  true,
  'authenticated users can read preferences subject to RLS'
);
select is(
  has_column_privilege(
    'authenticated',
    'public.profile_preferences',
    'profile_id',
    'insert'),
  true,
  'authenticated users can insert their own preferences'
);
select is(
  has_column_privilege(
    'authenticated',
    'public.profile_preferences',
    'intentions',
    'insert'),
  true,
  'authenticated users can insert preference content'
);
select is(
  has_table_privilege('authenticated', 'public.profile_preferences', 'delete'),
  false,
  'authenticated users cannot delete preference rows directly'
);
select is(
  has_column_privilege(
    'authenticated',
    'public.profile_preferences',
    'profile_id',
    'update'),
  false,
  'authenticated users cannot change the preference owner'
);
select is(
  has_column_privilege(
    'authenticated',
    'public.profile_preferences',
    'intentions',
    'update'),
  true,
  'authenticated users can update preference content'
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
values
  (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'authenticated',
    'authenticated',
    'preferences-one@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Preferencias Uno"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'authenticated',
    'authenticated',
    'preferences-two@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Preferencias Dos"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  );

insert into public.profile_preferences (profile_id)
values
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');

select is(
  (select intentions
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  '[]'::jsonb,
  'intentions default to an empty array'
);
select is(
  (select preferred_sectors
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  '[]'::jsonb,
  'preferred sectors default to an empty array'
);
select is(
  (select preferred_stages
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  '[]'::jsonb,
  'preferred stages default to an empty array'
);

update public.profile_preferences
set updated_at = '2000-01-01 00:00:00+00'
where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  true
);

select is(
  (select count(*) from public.profile_preferences),
  1::bigint,
  'a user can read exactly one own preference row'
);
select is(
  (select profile_id from public.profile_preferences),
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
  'a user cannot read another profile preferences row'
);

update public.profile_preferences
set intentions = '["COLLABORATE"]'::jsonb,
    preferred_sectors = '["sostenibilidad"]'::jsonb
where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

select is(
  (select intentions
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  '["COLLABORATE"]'::jsonb,
  'a user can update own intentions'
);
select is(
  (select preferred_sectors
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  '["sostenibilidad"]'::jsonb,
  'a user can update own preferred sectors'
);
select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.profile_preferences
   where profile_id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'preference updates refresh updated_at'
);

update public.profile_preferences
set preferred_stages = '["MVP"]'::jsonb
where profile_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

select throws_ok(
  $$insert into public.profile_preferences (profile_id)
    values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb')$$,
  '42501',
  'new row violates row-level security policy for table "profile_preferences"',
  'a user cannot insert another profile preferences row'
);

set local role postgres;
select is(
  (select preferred_stages
   from public.profile_preferences
   where profile_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  '[]'::jsonb,
  'a cross-user update does not change another profile preferences row'
);

select throws_ok(
  $$insert into public.profile_preferences (
      profile_id,
      intentions
    ) values (
      'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      '{}'::jsonb
    )$$,
  '23514',
  'new row for relation "profile_preferences" violates check constraint "profile_preferences_intentions_array"',
  'intentions must be a JSON array'
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  true
);

select is(
  (select count(*) from public.profile_preferences),
  1::bigint,
  'the second user can read exactly one own preference row'
);
select is(
  (select profile_id from public.profile_preferences),
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid,
  'the second user cannot read the first profile preferences row'
);

update public.profile_preferences
set preferred_stages = '["MVP"]'::jsonb
where profile_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

select is(
  (select preferred_stages
   from public.profile_preferences
   where profile_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  '["MVP"]'::jsonb,
  'the second user can update own preferred stages'
);

select * from finish();
rollback;
