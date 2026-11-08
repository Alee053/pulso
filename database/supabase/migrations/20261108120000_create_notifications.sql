begin;

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_profile_id uuid not null references public.profiles (id) on delete cascade,
  actor_profile_id uuid references public.profiles (id) on delete set null,
  project_id uuid references public.projects (id) on delete cascade,
  conversation_id uuid references public.conversations (id) on delete cascade,
  connection_intent_id uuid references public.connection_intents (id) on delete cascade,
  notification_type text not null,
  title text not null,
  body text,
  payload jsonb,
  read_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  expires_at timestamptz,
  constraint notifications_type check (
    notification_type in (
      'NEW_MESSAGE',
      'USER_INTERESTED',
      'MEMBERSHIP_UPDATE',
      'PROJECT_APPROVED',
      'PROJECT_VERIFIED',
      'ADMIN_UPDATE',
      'CONNECTION_REQUEST',
      'CONNECTION_RESPONSE'
    )
  ),
  constraint notifications_title_present check (btrim(title) <> ''),
  constraint notifications_context check (
    case notification_type
      when 'NEW_MESSAGE' then conversation_id is not null
      when 'CONNECTION_REQUEST' then connection_intent_id is not null and project_id is not null
      when 'CONNECTION_RESPONSE' then connection_intent_id is not null
      else true
    end
  )
);

create index notifications_recipient_created_idx
  on public.notifications (recipient_profile_id, created_at desc, id desc);
create index notifications_recipient_unread_idx
  on public.notifications (recipient_profile_id, created_at desc, id desc)
  where read_at is null;
create unique index notifications_connection_event_uidx
  on public.notifications (recipient_profile_id, notification_type, connection_intent_id)
  where notification_type in ('CONNECTION_REQUEST', 'CONNECTION_RESPONSE');

create function public.protect_notification_identity()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.recipient_profile_id <> old.recipient_profile_id
     or new.notification_type <> old.notification_type
     or new.created_at <> old.created_at
     or new.conversation_id is distinct from old.conversation_id
     or new.connection_intent_id is distinct from old.connection_intent_id
     or new.project_id is distinct from old.project_id then
    raise check_violation using
      constraint = 'notifications_identity_immutable',
      message = 'A notification cannot change its recipient, type, context or creation time';
  end if;

  if old.read_at is not null and new.read_at is distinct from old.read_at then
    raise check_violation using
      constraint = 'notifications_read_at_immutable',
      message = 'A notification keeps the time it was first read';
  end if;

  return new;
end;
$$;

create trigger notifications_protect_identity
before update on public.notifications
for each row execute function public.protect_notification_identity();

alter table public.notifications enable row level security;

revoke all privileges on table public.notifications from anon, authenticated;

comment on table public.notifications is
  'Essential notices for connection requests, connection responses and new messages. Product access is owned by the backend API.';
comment on column public.notifications.payload is
  'Identifiers only, never message text: messageId for NEW_MESSAGE and response for CONNECTION_RESPONSE.';

commit;
