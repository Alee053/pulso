begin;

select plan(9);

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
  'invite-creator@example.test',
  '$2a$10$abcdefghijklmnopqrstuu',
  '{"provider":"email","providers":["email"]}'::jsonb,
  '{"display_name":"Invite Creator"}'::jsonb,
  timezone('utc', now()),
  timezone('utc', now())
);

select has_index(
  'public',
  'project_members',
  'project_members_active_invite_email_key',
  'active invitations are unique per project and normalized invitee email'
);

select throws_ok(
  $$insert into public.project_members (
      project_id,
      invitee_email,
      invite_token_digest,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'Ada@Example.com',
      repeat('a', 64),
      'MEMBER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_members" violates check constraint "project_members_invitee_email_normalized"',
  'invitee emails must be stored normalized'
);

insert into public.project_members (
  project_id,
  invitee_email,
  invite_token_digest,
  role,
  membership_status,
  created_by_profile_id
)
values (
  '11111111-1111-1111-1111-111111111111',
  'invitee@example.com',
  repeat('b', 64),
  'MEMBER',
  'INVITED',
  'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

select throws_ok(
  $$insert into public.project_members (
      project_id,
      invitee_email,
      invite_token_digest,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'invitee@example.com',
      repeat('c', 64),
      'MEMBER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23505',
  'duplicate key value violates unique constraint "project_members_active_invite_email_key"',
  'a second INVITED row for the same project and email is rejected'
);

select throws_ok(
  $$insert into public.project_members (
      project_id,
      profile_id,
      invitee_email,
      invite_token_digest,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'dddddddd-dddd-dddd-dddd-dddddddddddd',
      'bound@example.com',
      repeat('d', 64),
      'MEMBER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_members" violates check constraint "project_members_invited_shape"',
  'INVITED rows keep profile_id empty'
);

select throws_ok(
  $$insert into public.project_members (
      project_id,
      invitee_email,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'nodigest@example.com',
      'MEMBER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_members" violates check constraint "project_members_invited_shape"',
  'INVITED rows require an invite token digest'
);

select throws_ok(
  $$insert into public.project_members (
      project_id,
      invitee_email,
      invite_token_digest,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'owner-invite@example.com',
      repeat('f', 64),
      'OWNER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  '23514',
  'new row for relation "project_members" violates check constraint "project_members_invited_shape"',
  'INVITED rows start as MEMBER'
);

update public.project_members
set membership_status = 'LEFT'
where project_id = '11111111-1111-1111-1111-111111111111'
  and invitee_email = 'invitee@example.com';

select lives_ok(
  $$insert into public.project_members (
      project_id,
      invitee_email,
      invite_token_digest,
      role,
      membership_status,
      created_by_profile_id
    )
    values (
      '11111111-1111-1111-1111-111111111111',
      'invitee@example.com',
      repeat('e', 64),
      'MEMBER',
      'INVITED',
      'dddddddd-dddd-dddd-dddd-dddddddddddd'
    )$$,
  'a new invitation is allowed after the previous one left'
);

select is(
  (select created_by_profile_id is not null
   from public.project_members
   where invitee_email = 'invitee@example.com'
     and membership_status = 'INVITED'),
  true,
  'invitations keep created_by_profile_id'
);

select is(
  (select count(*)
   from pg_constraint
   where conrelid = 'public.project_members'::regclass
     and confrelid = 'public.projects'::regclass
     and contype = 'f'),
  1::bigint,
  'invitations remain related to projects'
);

select * from finish();
rollback;
