begin;

select plan(44);

select has_column(
  'public',
  'profiles',
  'profile_kind',
  'profiles expose a profile kind'
);
select has_column(
  'public',
  'profiles',
  'avatar_storage_path',
  'profiles expose an avatar storage path'
);
select has_column(
  'public',
  'profiles',
  'short_bio',
  'profiles expose a short biography'
);
select has_column(
  'public',
  'profiles',
  'department',
  'profiles expose a department'
);
select has_column(
  'public',
  'profiles',
  'profile_visibility',
  'profiles expose profile visibility'
);
select has_column(
  'public',
  'profiles',
  'verification_status',
  'profiles expose verification status'
);
select has_column(
  'public',
  'profiles',
  'account_status',
  'profiles expose account status'
);
select has_column(
  'public',
  'profiles',
  'platform_role',
  'profiles expose platform role'
);
select has_column(
  'public',
  'profiles',
  'onboarding_completed_at',
  'profiles expose onboarding completion time'
);
select has_column(
  'public',
  'profiles',
  'deleted_at',
  'profiles expose deletion time'
);

select is(
  (select is_nullable
   from information_schema.columns
   where table_schema = 'public'
     and table_name = 'profiles'
     and column_name = 'display_name'),
  'NO',
  'display name remains required after profile completion'
);
select is(
  (select relrowsecurity
   from pg_class
   where oid = 'public.profiles'::regclass),
  true,
  'row level security is enabled for profiles'
);
select is(
  (select count(*)
   from pg_policies
   where schemaname = 'public'
     and tablename = 'profiles'),
  3::bigint,
  'profiles keep separate read, insert, and update policies'
);
select is(
  has_table_privilege('authenticated', 'public.profiles', 'select'),
  true,
  'authenticated users can request profile rows subject to RLS'
);
select is(
  has_table_privilege('anon', 'public.profiles', 'select'),
  false,
  'anonymous clients have no profile table grant'
);
select is(
  has_table_privilege('authenticated', 'public.profiles', 'delete'),
  false,
  'authenticated users cannot delete profiles directly'
);
select is(
  has_column_privilege('authenticated', 'public.profiles', 'short_bio', 'update'),
  true,
  'authenticated users can update profile content'
);
select is(
  has_column_privilege('authenticated', 'public.profiles', 'platform_role', 'update'),
  false,
  'authenticated users cannot update platform roles'
);
select is(
  has_column_privilege('authenticated', 'public.profiles', 'id', 'update'),
  false,
  'authenticated users cannot update the external identity'
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
    'profile-one@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Perfil Uno"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'authenticated',
    'authenticated',
    'profile-two@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'authenticated',
    'authenticated',
    'profile-three@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Perfil Tres"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'authenticated',
    'authenticated',
    'profile-four@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Perfil Cuatro"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  );

delete from public.profiles
where id = 'dddddddd-dddd-dddd-dddd-dddddddddddd';

select is(
  (select id from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
  'Auth signup creates a profile with the same external UUID'
);
select is(
  (select id from public.profiles where id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid,
  'each Auth signup keeps its own profile identity'
);
select is(
  (select display_name from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'Perfil Uno',
  'Auth signup copies the display name metadata'
);
select is(
  (select display_name from public.profiles where id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'),
  'Usuario',
  'Auth signup supplies a safe display name when metadata is absent'
);
select is(
  (select profile_kind from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'PERSON',
  'new profiles default to a personal profile'
);
select is(
  (select profile_visibility from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'PUBLIC',
  'new profiles default to public visibility'
);
select is(
  (select verification_status from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'UNVERIFIED',
  'new profiles default to unverified status'
);
select is(
  (select account_status from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'ACTIVE',
  'new profiles default to active account status'
);
select is(
  (select platform_role from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  'USER',
  'new profiles default to the user platform role'
);
select is(
  (select count(*) from public.profiles where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  1::bigint,
  'Auth signup creates exactly one profile'
);

select throws_ok(
  $$update public.profiles
    set short_bio = repeat('x', 501)
    where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'$$,
  '23514',
  'new row for relation "profiles" violates check constraint "profiles_short_bio_length"',
  'short biographies longer than 500 characters are rejected'
);
select throws_ok(
  $$update public.profiles
    set short_bio = '   '
    where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'$$,
  '23514',
  'new row for relation "profiles" violates check constraint "profiles_short_bio_length"',
  'blank short biographies are rejected'
);
select throws_ok(
  $$update public.profiles
    set department = repeat('x', 121)
    where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'$$,
  '23514',
  'new row for relation "profiles" violates check constraint "profiles_department_length"',
  'departments longer than 120 characters are rejected'
);
select throws_ok(
  $$update public.profiles
    set profile_visibility = 'HIDDEN'
    where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'$$,
  '23514',
  'new row for relation "profiles" violates check constraint "profiles_visibility"',
  'unknown profile visibility values are rejected'
);
select throws_ok(
  $$update public.profiles
    set id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
    where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'$$,
  'P0001',
  'profiles.id is immutable',
  'profile updates cannot replace the external identity'
);

update public.profiles
set updated_at = '2000-01-01 00:00:00+00',
    display_name = 'Perfil Tres actualizado'
where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc';

select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.profiles
   where id = 'cccccccc-cccc-cccc-cccc-cccccccccccc'),
  'profile updates refresh updated_at'
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  true
);

select is(
  (select count(*) from public.profiles),
  1::bigint,
  'a user can read exactly one own profile row'
);
select is(
  (select id from public.profiles),
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'::uuid,
  'a user cannot read another profile row'
);
update public.profiles
set short_bio = 'Biografía propia', department = 'Cochabamba'
where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

select is(
  (select short_bio from public.profiles),
  'Biografía propia',
  'own profile updates persist product fields'
);
select is(
  (select department from public.profiles),
  'Cochabamba',
  'own profile updates persist department'
);
select throws_ok(
  $$update public.profiles
    set platform_role = 'ADMIN'
    where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'$$,
  '42501',
  'permission denied for table profiles',
  'a user cannot elevate the platform role'
);
update public.profiles
set display_name = 'Intento cruzado'
where id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

select throws_ok(
  $$insert into public.profiles (id, display_name)
    values ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Perfil cruzado')$$,
  '42501',
  'new row violates row-level security policy for table "profiles"',
  'a user cannot create another profile'
);

select set_config(
  'request.jwt.claim.sub',
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  true
);

select is(
  (select count(*) from public.profiles),
  1::bigint,
  'the second user can read exactly one own profile row'
);
select is(
  (select id from public.profiles),
  'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'::uuid,
  'the second user cannot read the first profile'
);
select is(
  (select display_name from public.profiles),
  'Usuario',
  'cross-user attempts do not change the second profile'
);

select * from finish();
rollback;
