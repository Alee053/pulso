begin;

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_profile_id uuid not null references public.profiles (id),
  reply_to_message_id uuid,
  body text not null,
  message_type text not null default 'TEXT',
  status text not null default 'SENT',
  created_at timestamptz not null default timezone('utc', now()),
  edited_at timestamptz,
  deleted_at timestamptz,
  constraint messages_conversation_id_id_key unique (conversation_id, id),
  constraint messages_reply_in_conversation_fkey
    foreign key (conversation_id, reply_to_message_id)
    references public.messages (conversation_id, id),
  constraint messages_type check (message_type in ('TEXT', 'SYSTEM')),
  constraint messages_status check (status in ('SENT', 'EDITED', 'DELETED')),
  constraint messages_body_length check (char_length(body) <= 5000),
  constraint messages_body_present check (status = 'DELETED' or btrim(body) <> ''),
  constraint messages_status_timestamps check (
    (status = 'SENT' and edited_at is null and deleted_at is null)
    or (status = 'EDITED' and edited_at is not null and deleted_at is null)
    or (status = 'DELETED' and deleted_at is not null)
  )
);

create index messages_conversation_created_idx
  on public.messages (conversation_id, created_at, id);
create index messages_sender_profile_idx
  on public.messages (sender_profile_id);

create function public.validate_message_sender()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if not exists (
    select 1
      from public.conversation_participants
     where conversation_id = new.conversation_id
       and profile_id = new.sender_profile_id
       and left_at is null
  ) then
    raise check_violation using
      constraint = 'messages_sender_active_participant',
      message = 'Message senders must be active participants of the conversation';
  end if;

  return new;
end;
$$;

create trigger messages_validate_sender
before insert on public.messages
for each row execute function public.validate_message_sender();

create function public.protect_message_identity()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.conversation_id <> old.conversation_id
     or new.sender_profile_id <> old.sender_profile_id
     or new.message_type <> old.message_type
     or new.created_at <> old.created_at then
    raise check_violation using
      constraint = 'messages_identity_immutable',
      message = 'A message cannot change its conversation, sender, type or creation time';
  end if;

  return new;
end;
$$;

create trigger messages_protect_identity
before update on public.messages
for each row execute function public.protect_message_identity();

alter table public.messages enable row level security;

revoke all privileges on table public.messages from anon, authenticated;

comment on table public.messages is
  'Messages of a private conversation. Product access is owned by the backend API.';
comment on column public.messages.body is
  'Message text. Deleted messages are exposed without their body.';

commit;
