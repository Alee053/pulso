begin;

select plan(26);

select has_table('public', 'connection_intents', 'connection intents use a database table');
select has_table('public', 'conversations', 'conversations use a database table');
select has_table('public', 'conversation_participants', 'conversation participants use a database table');
select has_table('public', 'profile_blocks', 'profile blocks use a database table');
select has_column('public', 'connection_intents', 'responded_by_profile_id', 'accepted intents retain the responder');
select has_column('public', 'conversations', 'connection_intent_id', 'project contact conversations retain their source intent');
select has_pk('public', 'conversation_participants', 'conversation participants have a composite primary key');
select has_pk('public', 'profile_blocks', 'profile blocks have a directional composite primary key');
select has_index(
  'public',
  'conversations',
  'conversations_connection_intent_id_key',
  'one connection intent can create at most one conversation'
);

select is(
  (select relrowsecurity from pg_class where oid = 'public.connection_intents'::regclass),
  true,
  'connection intents enable row level security'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.conversations'::regclass),
  true,
  'conversations enable row level security'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.conversation_participants'::regclass),
  true,
  'conversation participants enable row level security'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.profile_blocks'::regclass),
  true,
  'profile blocks enable row level security'
);

select is(
  has_table_privilege('anon', 'public.connection_intents', 'select'),
  false,
  'anonymous clients cannot read connection intents directly'
);
select is(
  has_table_privilege('authenticated', 'public.conversations', 'select'),
  false,
  'authenticated clients cannot read conversations directly'
);
select is(
  has_table_privilege('authenticated', 'public.conversation_participants', 'insert'),
  false,
  'authenticated clients cannot add conversation participants directly'
);
select is(
  has_table_privilege('authenticated', 'public.profile_blocks', 'select'),
  false,
  'authenticated clients cannot read profile blocks directly'
);

insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data, raw_user_meta_data,
  created_at, updated_at
)
values
  (
    '27111111-1111-4111-8111-111111111111',
    'authenticated',
    'authenticated',
    'connection-initiator@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Connection Initiator"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    '27222222-2222-4222-8222-222222222222',
    'authenticated',
    'authenticated',
    'project-responder@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Project Responder"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  );

select throws_ok(
  $$insert into public.profile_blocks (blocker_profile_id, blocked_profile_id)
    values (
      '27111111-1111-4111-8111-111111111111',
      '27111111-1111-4111-8111-111111111111'
    )$$,
  '23514',
  'new row for relation "profile_blocks" violates check constraint "profile_blocks_distinct_profiles"',
  'a profile cannot block itself'
);

insert into public.connection_intents (
  id, from_profile_id, project_id, reason, message
)
values (
  '27333333-3333-4333-8333-333333333333',
  '27111111-1111-4111-8111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  'COLLABORATE',
  'I would like to collaborate.'
);

select is(
  (select status from public.connection_intents where id = '27333333-3333-4333-8333-333333333333'),
  'PENDING',
  'new connection intents start pending'
);

select throws_ok(
  $$insert into public.conversations (
      conversation_type, project_id, created_by_profile_id
    )
    values (
      'PROJECT_CONTACT',
      '11111111-1111-1111-1111-111111111111',
      '27111111-1111-4111-8111-111111111111'
    )$$,
  '23514',
  'new row for relation "conversations" violates check constraint "conversations_project_contact_shape"',
  'project contact conversations require a connection intent'
);

select lives_ok(
  $$insert into public.conversations (
      id, conversation_type, project_id, connection_intent_id, created_by_profile_id
    )
    values (
      '27444444-4444-4444-8444-444444444444',
      'PROJECT_CONTACT',
      '11111111-1111-1111-1111-111111111111',
      '27333333-3333-4333-8333-333333333333',
      '27111111-1111-4111-8111-111111111111'
    )$$,
  'an accepted connection can own a project contact conversation'
);

select throws_ok(
  $$insert into public.conversations (
      conversation_type, project_id, connection_intent_id, created_by_profile_id
    )
    values (
      'PROJECT_CONTACT',
      '11111111-1111-1111-1111-111111111111',
      '27333333-3333-4333-8333-333333333333',
      '27111111-1111-4111-8111-111111111111'
    )$$,
  '23505',
  'duplicate key value violates unique constraint "conversations_connection_intent_id_key"',
  'a connection intent cannot create a duplicate conversation'
);

insert into public.conversation_participants (
  conversation_id, profile_id, participant_role
)
values
  (
    '27444444-4444-4444-8444-444444444444',
    '27111111-1111-4111-8111-111111111111',
    'INITIATOR'
  ),
  (
    '27444444-4444-4444-8444-444444444444',
    '27222222-2222-4222-8222-222222222222',
    'PROJECT_MEMBER'
  );

select is(
  (select count(*) from public.conversation_participants
   where conversation_id = '27444444-4444-4444-8444-444444444444'),
  2::bigint,
  'a project contact conversation can contain both required participants'
);
select is(
  (select array_agg(participant_role order by participant_role)
   from public.conversation_participants
   where conversation_id = '27444444-4444-4444-8444-444444444444'),
  array['INITIATOR', 'PROJECT_MEMBER']::text[],
  'conversation participants retain their contextual roles'
);
select throws_ok(
  $$insert into public.conversation_participants (
      conversation_id, profile_id, participant_role
    )
    values (
      '27444444-4444-4444-8444-444444444444',
      '27111111-1111-4111-8111-111111111111',
      'INITIATOR'
    )$$,
  '23505',
  'duplicate key value violates unique constraint "conversation_participants_pkey"',
  'a profile cannot be added to the same conversation twice'
);
select lives_ok(
  $$insert into public.profile_blocks (blocker_profile_id, blocked_profile_id)
    values (
      '27111111-1111-4111-8111-111111111111',
      '27222222-2222-4222-8222-222222222222'
    )$$,
  'a profile can block another profile directionally'
);

select * from finish();
rollback;
