begin;

select plan(16);

select has_table('public', 'notifications', 'notifications use a database table');
select is(
  (select relrowsecurity from pg_class where oid = 'public.notifications'::regclass),
  true,
  'notifications enable row level security'
);
select is(
  has_table_privilege('authenticated', 'public.notifications', 'select'),
  false,
  'authenticated clients cannot read notifications directly'
);
select is(
  has_table_privilege('authenticated', 'public.notifications', 'update'),
  false,
  'authenticated clients cannot mark notifications directly'
);
select has_index(
  'public',
  'notifications',
  'notifications_recipient_unread_idx',
  'unread notifications are indexed per recipient'
);

insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data, raw_user_meta_data,
  created_at, updated_at
)
values
  (
    '29111111-1111-4111-8111-111111111111',
    'authenticated',
    'authenticated',
    'notification-initiator@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Notification Initiator"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  ),
  (
    '29222222-2222-4222-8222-222222222222',
    'authenticated',
    'authenticated',
    'notification-responder@example.test',
    '$2a$10$abcdefghijklmnopqrstuu',
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"display_name":"Notification Responder"}'::jsonb,
    timezone('utc', now()),
    timezone('utc', now())
  );

insert into public.connection_intents (
  id, from_profile_id, project_id, reason, message
)
values (
  '29333333-3333-4333-8333-333333333333',
  '29111111-1111-4111-8111-111111111111',
  '11111111-1111-1111-1111-111111111111',
  'COLLABORATE',
  'I would like to collaborate.'
);

select lives_ok(
  $$insert into public.notifications (
      id, recipient_profile_id, actor_profile_id, project_id, connection_intent_id,
      notification_type, title
    )
    values (
      '29666666-6666-4666-8666-666666666661',
      '29222222-2222-4222-8222-222222222222',
      '29111111-1111-4111-8111-111111111111',
      '11111111-1111-1111-1111-111111111111',
      '29333333-3333-4333-8333-333333333333',
      'CONNECTION_REQUEST',
      'New connection request'
    )$$,
  'a connection request notification can be stored for a recipient'
);
select is(
  (select read_at is null from public.notifications
    where id = '29666666-6666-4666-8666-666666666661'),
  true,
  'new notifications start unread'
);
select throws_ok(
  $$insert into public.notifications (
      recipient_profile_id, actor_profile_id, project_id, connection_intent_id,
      notification_type, title
    )
    values (
      '29222222-2222-4222-8222-222222222222',
      '29111111-1111-4111-8111-111111111111',
      '11111111-1111-1111-1111-111111111111',
      '29333333-3333-4333-8333-333333333333',
      'CONNECTION_REQUEST',
      'Duplicated request notice'
    )$$,
  '23505',
  'duplicate key value violates unique constraint "notifications_connection_event_uidx"',
  'a recipient is notified once per connection request'
);
select throws_ok(
  $$insert into public.notifications (recipient_profile_id, notification_type, title)
    values ('29222222-2222-4222-8222-222222222222', 'NEW_MESSAGE', 'New message')$$,
  '23514',
  'new row for relation "notifications" violates check constraint "notifications_context"',
  'a new message notification needs its conversation'
);
select throws_ok(
  $$insert into public.notifications (
      recipient_profile_id, connection_intent_id, notification_type, title
    )
    values (
      '29222222-2222-4222-8222-222222222222',
      '29333333-3333-4333-8333-333333333333',
      'CONNECTION_REQUEST',
      'Missing project'
    )$$,
  '23514',
  'new row for relation "notifications" violates check constraint "notifications_context"',
  'a connection request notification needs its project'
);
select throws_ok(
  $$insert into public.notifications (recipient_profile_id, notification_type, title)
    values ('29222222-2222-4222-8222-222222222222', 'SOMETHING_ELSE', 'Unknown type')$$,
  '23514',
  'new row for relation "notifications" violates check constraint "notifications_type"',
  'notifications only accept the known types'
);
select throws_ok(
  $$insert into public.notifications (recipient_profile_id, notification_type, title)
    values ('29222222-2222-4222-8222-222222222222', 'ADMIN_UPDATE', '   ')$$,
  '23514',
  'new row for relation "notifications" violates check constraint "notifications_title_present"',
  'a notification needs a title'
);
select lives_ok(
  $$update public.notifications
       set read_at = '2026-11-08T12:00:00Z'
     where id = '29666666-6666-4666-8666-666666666661'$$,
  'a notification can be marked as read'
);
select throws_ok(
  $$update public.notifications
       set read_at = null
     where id = '29666666-6666-4666-8666-666666666661'$$,
  '23514',
  'A notification keeps the time it was first read',
  'a read notification cannot become unread'
);
select throws_ok(
  $$update public.notifications
       set read_at = '2026-11-08T13:00:00Z'
     where id = '29666666-6666-4666-8666-666666666661'$$,
  '23514',
  'A notification keeps the time it was first read',
  'the first read time is preserved'
);
select throws_ok(
  $$update public.notifications
       set recipient_profile_id = '29111111-1111-4111-8111-111111111111'
     where id = '29666666-6666-4666-8666-666666666661'$$,
  '23514',
  'A notification cannot change its recipient, type, context or creation time',
  'a notification cannot be reassigned to another recipient'
);
select * from finish();
rollback;
