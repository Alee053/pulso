begin;

create function public.protect_linked_connection_intent_context()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if exists (
       select 1
         from public.conversations
        where connection_intent_id = old.id
     )
     and (
       new.from_profile_id is distinct from old.from_profile_id
       or new.project_id is distinct from old.project_id
       or new.responded_by_profile_id is distinct from old.responded_by_profile_id
       or new.status not in ('ACCEPTED', 'CONVERTED')
     ) then
    raise check_violation using
      constraint = 'connection_intents_linked_context_immutable',
      message = 'a linked connection intent must preserve its accepted conversation context';
  end if;

  return new;
end;
$$;

create trigger connection_intents_protect_linked_context
before update of from_profile_id, project_id, responded_by_profile_id, status
on public.connection_intents
for each row execute function public.protect_linked_connection_intent_context();

commit;
