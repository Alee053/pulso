begin;

alter table public.conversations
  drop constraint conversations_project_contact_shape,
  add constraint conversations_context_shape check (
    (
      conversation_type = 'PROJECT_CONTACT'
      and project_id is not null
      and connection_intent_id is not null
    )
    or (
      conversation_type = 'DIRECT'
      and connection_intent_id is null
    )
  );

create function public.validate_connection_conversation_context()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  source_intent public.connection_intents%rowtype;
begin
  if new.conversation_type <> 'PROJECT_CONTACT' then
    return new;
  end if;

  select *
    into source_intent
    from public.connection_intents
   where id = new.connection_intent_id;

  if not found
     or source_intent.status <> 'ACCEPTED'
     or source_intent.responded_by_profile_id is null
     or source_intent.project_id <> new.project_id
     or source_intent.from_profile_id <> new.created_by_profile_id then
    raise check_violation using
      constraint = 'conversations_project_contact_context',
      message = 'PROJECT_CONTACT conversations require their accepted connection intent context';
  end if;

  return new;
end;
$$;

create trigger conversations_validate_connection_context
before insert or update of conversation_type, project_id, connection_intent_id, created_by_profile_id
on public.conversations
for each row execute function public.validate_connection_conversation_context();

create function public.validate_project_contact_participants(target_conversation_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
declare
  conversation_type text;
  initiator_profile_id uuid;
  responder_profile_id uuid;
  valid_participant_count bigint;
  participant_count bigint;
begin
  select c.conversation_type,
         i.from_profile_id,
         i.responded_by_profile_id
    into conversation_type, initiator_profile_id, responder_profile_id
    from public.conversations c
    join public.connection_intents i on i.id = c.connection_intent_id
   where c.id = target_conversation_id;

  if not found or conversation_type <> 'PROJECT_CONTACT' then
    return;
  end if;

  select count(*),
         count(*) filter (
           where (profile_id = initiator_profile_id and participant_role = 'INITIATOR')
              or (profile_id = responder_profile_id and participant_role = 'PROJECT_MEMBER')
         )
    into participant_count, valid_participant_count
    from public.conversation_participants
   where conversation_id = target_conversation_id;

  if participant_count <> 2 or valid_participant_count <> 2 then
    raise check_violation using
      constraint = 'project_contact_required_participants',
      message = 'PROJECT_CONTACT conversations require exactly their initiator and responder';
  end if;
end;
$$;

create function public.validate_project_contact_participants_from_conversation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  perform public.validate_project_contact_participants(new.id);
  return null;
end;
$$;

create constraint trigger conversations_validate_participants
after insert or update of conversation_type, connection_intent_id
on public.conversations
deferrable initially deferred
for each row execute function public.validate_project_contact_participants_from_conversation();

create function public.validate_project_contact_participants_from_participant()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'UPDATE' and old.conversation_id <> new.conversation_id then
    perform public.validate_project_contact_participants(old.conversation_id);
  end if;

  perform public.validate_project_contact_participants(
    case when tg_op = 'DELETE' then old.conversation_id else new.conversation_id end
  );
  return null;
end;
$$;

create constraint trigger conversation_participants_validate_project_contact
after insert or update or delete
on public.conversation_participants
deferrable initially deferred
for each row execute function public.validate_project_contact_participants_from_participant();

create function public.profile_contact_lock_key(first_profile_id uuid, second_profile_id uuid)
returns bigint
language sql
immutable
strict
set search_path = ''
return hashtextextended(
  concat_ws(
    ':',
    least(first_profile_id::text, second_profile_id::text),
    greatest(first_profile_id::text, second_profile_id::text)
  ),
  0
);

create function public.serialize_profile_block_change()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  old_lock_key bigint;
  new_lock_key bigint;
begin
  if tg_op in ('UPDATE', 'DELETE') then
    old_lock_key := public.profile_contact_lock_key(
      old.blocker_profile_id,
      old.blocked_profile_id
    );
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    new_lock_key := public.profile_contact_lock_key(
      new.blocker_profile_id,
      new.blocked_profile_id
    );
  end if;

  if old_lock_key is not null and new_lock_key is not null and old_lock_key <> new_lock_key then
    perform pg_catalog.pg_advisory_xact_lock(least(old_lock_key, new_lock_key));
    perform pg_catalog.pg_advisory_xact_lock(greatest(old_lock_key, new_lock_key));
  else
    perform pg_catalog.pg_advisory_xact_lock(coalesce(old_lock_key, new_lock_key));
  end if;

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

create trigger profile_blocks_serialize_contact_pair
before insert or update or delete on public.profile_blocks
for each row execute function public.serialize_profile_block_change();

commit;
