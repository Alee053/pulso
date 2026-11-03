begin;

-- The composite key lets a connection intent prove that its optional
-- opportunity belongs to the same project recorded on the intent.
alter table public.project_opportunities
  add constraint project_opportunities_id_project_id_key unique (id, project_id);

create table public.connection_intents (
  id uuid primary key default gen_random_uuid(),
  from_profile_id uuid not null references public.profiles (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete cascade,
  opportunity_id uuid,
  reason text not null,
  message text,
  status text not null default 'PENDING',
  outcome_type text,
  responded_by_profile_id uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default timezone('utc', now()),
  responded_at timestamptz,
  confirmed_at timestamptz,
  constraint connection_intents_reason check (
    reason in ('COLLABORATE', 'WORK', 'TRY_PRODUCT', 'BUY', 'INSTITUTION', 'INVEST', 'OFFER_SERVICE', 'OTHER')
  ),
  constraint connection_intents_status check (
    status in ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN', 'EXPIRED', 'CONVERTED')
  ),
  constraint connection_intents_outcome_type check (
    outcome_type is null or outcome_type in ('COLLABORATION', 'CUSTOMER', 'TALENT', 'PARTNERSHIP', 'OTHER')
  ),
  constraint connection_intents_opportunity_project_fkey
    foreign key (opportunity_id, project_id)
    references public.project_opportunities (id, project_id)
);

create index connection_intents_project_status_created_idx
  on public.connection_intents (project_id, status, created_at desc, id);
create index connection_intents_from_profile_idx
  on public.connection_intents (from_profile_id, created_at desc, id);
create index connection_intents_opportunity_idx
  on public.connection_intents (opportunity_id);

-- NULL opportunity IDs are equal only within project-only contexts. Splitting
-- these indexes avoids PostgreSQL's default NULLS DISTINCT behavior.
create unique index connection_intents_pending_project_context_uidx
  on public.connection_intents (from_profile_id, project_id)
  where status = 'PENDING' and opportunity_id is null;
create unique index connection_intents_pending_opportunity_context_uidx
  on public.connection_intents (from_profile_id, project_id, opportunity_id)
  where status = 'PENDING' and opportunity_id is not null;

alter table public.connection_intents enable row level security;
revoke all privileges on table public.connection_intents from anon, authenticated;

comment on table public.connection_intents is
  'Contextual project connection requests. Sender, initial status, and all persistence are managed by the authenticated backend API.';

commit;
