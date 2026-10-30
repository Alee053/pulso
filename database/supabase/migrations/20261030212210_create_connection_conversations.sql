begin;

create table public.connection_intents (
  id uuid primary key default gen_random_uuid(),
  from_profile_id uuid not null references public.profiles (id),
  project_id uuid not null references public.projects (id),
  opportunity_id uuid references public.project_opportunities (id),
  reason text not null,
  message text,
  status text not null default 'PENDING',
  outcome_type text,
  responded_by_profile_id uuid references public.profiles (id),
  created_at timestamptz not null default timezone('utc', now()),
  responded_at timestamptz,
  confirmed_at timestamptz,
  constraint connection_intents_reason check (
    reason in (
      'COLLABORATE', 'WORK', 'TRY_PRODUCT', 'BUY', 'INSTITUTION', 'INVEST',
      'OFFER_SERVICE', 'OTHER'
    )
  ),
  constraint connection_intents_status check (
    status in ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'EXPIRED', 'CONVERTED')
  ),
  constraint connection_intents_outcome_type check (
    outcome_type is null
    or outcome_type in ('COLLABORATION', 'CUSTOMER', 'TALENT', 'PARTNERSHIP', 'OTHER')
  ),
  constraint connection_intents_distinct_responder check (
    responded_by_profile_id is null or responded_by_profile_id <> from_profile_id
  )
);

create index connection_intents_project_status_created_idx
  on public.connection_intents (project_id, status, created_at desc, id);
create index connection_intents_from_profile_idx
  on public.connection_intents (from_profile_id);
create index connection_intents_opportunity_idx
  on public.connection_intents (opportunity_id)
  where opportunity_id is not null;

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  conversation_type text not null,
  project_id uuid references public.projects (id),
  connection_intent_id uuid unique references public.connection_intents (id),
  created_by_profile_id uuid not null references public.profiles (id),
  status text not null default 'ACTIVE',
  last_message_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint conversations_type check (
    conversation_type in ('PROJECT_CONTACT', 'DIRECT')
  ),
  constraint conversations_status check (
    status in ('ACTIVE', 'ARCHIVED', 'BLOCKED', 'CLOSED')
  ),
  constraint conversations_project_contact_shape check (
    conversation_type <> 'PROJECT_CONTACT'
    or (project_id is not null and connection_intent_id is not null)
  )
);

create index conversations_project_last_message_idx
  on public.conversations (project_id, last_message_at desc, id);
create index conversations_created_by_profile_idx
  on public.conversations (created_by_profile_id);

create trigger conversations_set_updated_at
before update on public.conversations
for each row execute function public.set_updated_at();

create table public.conversation_participants (
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  profile_id uuid not null references public.profiles (id),
  participant_role text not null default 'PARTICIPANT',
  last_read_at timestamptz,
  muted_at timestamptz,
  joined_at timestamptz not null default timezone('utc', now()),
  left_at timestamptz,
  primary key (conversation_id, profile_id),
  constraint conversation_participants_role check (
    participant_role in ('INITIATOR', 'PROJECT_MEMBER', 'PARTICIPANT')
  )
);

create index conversation_participants_profile_idx
  on public.conversation_participants (profile_id, joined_at desc, conversation_id);

create table public.profile_blocks (
  blocker_profile_id uuid not null references public.profiles (id) on delete cascade,
  blocked_profile_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (blocker_profile_id, blocked_profile_id),
  constraint profile_blocks_distinct_profiles check (
    blocker_profile_id <> blocked_profile_id
  )
);

create index profile_blocks_blocked_profile_idx
  on public.profile_blocks (blocked_profile_id, blocker_profile_id);

alter table public.connection_intents enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_participants enable row level security;
alter table public.profile_blocks enable row level security;

revoke all privileges on table public.connection_intents from anon, authenticated;
revoke all privileges on table public.conversations from anon, authenticated;
revoke all privileges on table public.conversation_participants from anon, authenticated;
revoke all privileges on table public.profile_blocks from anon, authenticated;

comment on table public.connection_intents is
  'Contextual requests from a profile to a project, managed only through the backend API.';
comment on table public.conversations is
  'Private conversation contexts. PROJECT_CONTACT rows originate from one accepted connection intent.';
comment on table public.conversation_participants is
  'Profiles allowed to participate in a conversation and their contextual role.';
comment on table public.profile_blocks is
  'Directed profile blocks that prevent new contact in either direction.';

commit;
