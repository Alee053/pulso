begin;

select plan(20);

select has_table('public', 'messages', 'messages use a database table');
select is(
  (select relrowsecurity from pg_class where oid = 'public.messages'::regclass),
  true,
  'messages enable row level security'
);
select is(
  has_table_privilege('authenticated', 'public.messages', 'select'),
  false,
  'authenticated clients cannot read messages directly'
);
select is(
  has_table_privilege('authenticated', 'public.messages', 'insert'),
  false,
  'authenticated clients cannot send messages directly'
);
select has_trigger(
  'public',
  'messages',
  'messages_validate_sender',
  'messages validate that the sender is an active participant'
);
select has_index(
  'public',
  'messages',
  'messages_conversation_created_idx',
  'messages are indexed for ordered conversation reads'
);

insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data, raw_user_meta_data,
  created_at, updated_at
)
values
  (
    '28111111-1111-4111-8111-111111111111',
    'authenticated',
    'authenticated',
    'message-initiator@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Message Initiator"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    '28222222-2222-4222-8222-222222222222',
    'authenticated',
    'authenticated',
    'message-responder@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Message Responder"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    '28555555-5555-4555-8555-555555555555',
    'authenticated',
    'authenticated',
    'message-outsider@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Message Outsider"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  );

insert into public.connection_intents (
  id, from_profile_id, project_id, reason, message
)
values (
  '28333333-3333-4333-8333-333333333333',
  '28111111-1111-4111-8111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  'COLLABORATE',
  'I would like to collaborate.'
);

update public.connection_intents
   set status = 'ACCEPTED',
       responded_by_profile_id = '28222222-2222-4222-8222-222222222222',
       responded_at = timezone('utc', now())
 where id = '28333333-3333-4333-8333-333333333333';

insert into public.conversations (
  id, conversation_type, project_id, connection_intent_id, created_by_profile_id
)
values (
  '28444444-4444-4444-8444-444444444444',
  'PROJECT_CONTACT',
  '11111111-1111-1111-1111-111111111111',
  '28333333-3333-4333-8333-333333333333',
  '28111111-1111-4111-8111-111111111111'
);

insert into public.conversation_participants (
  conversation_id, profile_id, participant_role
)
values
  (
    '28444444-4444-4444-8444-444444444444',
    '28111111-1111-4111-8111-111111111111',
    'INITIATOR'
  ),
  (
    '28444444-4444-4444-8444-444444444444',
    '28222222-2222-4222-8222-222222222222',
    'PROJECT_MEMBER'
  );

select lives_ok(
  $$insert into public.messages (id, conversation_id, sender_profile_id, body)
    values (
      '28666666-6666-4666-8666-666666666661',
      '28444444-4444-4444-8444-444444444444',
      '28111111-1111-4111-8111-111111111111',
      'Hola, me interesa colaborar.'
    )$$,
  'an active participant can send a text message'
);
select is(
  (select message_type || '/' || status
     from public.messages
    where id = '28666666-6666-4666-8666-666666666661'),
  'TEXT/SENT',
  'new messages default to the TEXT type and the SENT status'
);
select throws_ok(
  $$insert into public.messages (conversation_id, sender_profile_id, body)
    values (
      '28444444-4444-4444-8444-444444444444',
      '28555555-5555-4555-8555-555555555555',
      'No pertenezco a esta conversación.'
    )$$,
  '23514',
  'Message senders must be active participants of the conversation',
  'an external profile cannot send a message'
);
select throws_ok(
  $$insert into public.messages (conversation_id, sender_profile_id, body)
    values (
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      '   '
    )$$,
  '23514',
  'new row for relation "messages" violates check constraint "messages_body_present"',
  'a sent message needs a non-blank body'
);
select throws_ok(
  $$insert into public.messages (conversation_id, sender_profile_id, body, status)
    values (
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      'Estado desconocido',
      'ARCHIVED'
    )$$,
  '23514',
  'new row for relation "messages" violates check constraint "messages_status"',
  'messages only accept the SENT, EDITED and DELETED statuses'
);
select throws_ok(
  $$insert into public.messages (conversation_id, sender_profile_id, body, message_type)
    values (
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      'Archivo adjunto',
      'FILE'
    )$$,
  '23514',
  'new row for relation "messages" violates check constraint "messages_type"',
  'messages only accept the TEXT and SYSTEM types'
);
select throws_ok(
  $$update public.messages
       set status = 'EDITED'
     where id = '28666666-6666-4666-8666-666666666661'$$,
  '23514',
  'new row for relation "messages" violates check constraint "messages_status_timestamps"',
  'an edited message must record when it was edited'
);
select lives_ok(
  $$update public.messages
       set status = 'EDITED',
           body = 'Hola, me interesa colaborar con el equipo.',
           edited_at = timezone('utc', now())
     where id = '28666666-6666-4666-8666-666666666661'$$,
  'a message can move to the EDITED status with its edit time'
);
select lives_ok(
  $$update public.messages
       set status = 'DELETED',
           body = '',
           deleted_at = timezone('utc', now())
     where id = '28666666-6666-4666-8666-666666666661'$$,
  'a message can move to the DELETED status without keeping its body'
);
select throws_ok(
  $$update public.messages
       set sender_profile_id = '28222222-2222-4222-8222-222222222222'
     where id = '28666666-6666-4666-8666-666666666661'$$,
  '23514',
  'A message cannot change its conversation, sender, type or creation time',
  'a message cannot be reassigned to another sender'
);
select lives_ok(
  $$insert into public.messages (
      id, conversation_id, sender_profile_id, body, reply_to_message_id
    )
    values (
      '28666666-6666-4666-8666-666666666662',
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      'Con gusto, conversemos.',
      '28666666-6666-4666-8666-666666666661'
    )$$,
  'a reply can reference a message of the same conversation'
);
select throws_ok(
  $$insert into public.messages (
      conversation_id, sender_profile_id, body, reply_to_message_id
    )
    values (
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      'Respuesta a un mensaje inexistente.',
      '28666666-6666-4666-8666-666666666699'
    )$$,
  '23503',
  'insert or update on table "messages" violates foreign key constraint "messages_reply_in_conversation_fkey"',
  'a reply must reference an existing message of the same conversation'
);

update public.conversation_participants
   set left_at = timezone('utc', now())
 where conversation_id = '28444444-4444-4444-8444-444444444444'
   and profile_id = '28222222-2222-4222-8222-222222222222';

select throws_ok(
  $$insert into public.messages (conversation_id, sender_profile_id, body)
    values (
      '28444444-4444-4444-8444-444444444444',
      '28222222-2222-4222-8222-222222222222',
      'Ya salí de la conversación.'
    )$$,
  '23514',
  'Message senders must be active participants of the conversation',
  'a participant who left cannot send messages'
);
select is(
  (select count(*) from public.messages
    where conversation_id = '28444444-4444-4444-8444-444444444444'),
  2::bigint,
  'rejected messages leave no rows behind'
);

select * from finish();
rollback;
