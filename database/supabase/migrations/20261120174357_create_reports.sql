begin;

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_profile_id uuid not null references public.profiles (id) on delete restrict,
  target_profile_id uuid references public.profiles (id) on delete restrict,
  target_project_id uuid references public.projects (id) on delete restrict,
  target_message_id uuid references public.messages (id) on delete restrict,
  reason text not null,
  description text,
  status text not null default 'OPEN',
  assigned_moderator_id uuid references public.profiles (id) on delete restrict,
  resolution_note text,
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  updated_at timestamptz not null default now(),
  constraint reports_single_target check (
    num_nonnulls(target_profile_id, target_project_id, target_message_id) = 1
  ),
  constraint reports_reason_length check (reason ~ '[^[:space:]]' and char_length(reason) <= 120),
  constraint reports_description_length check (char_length(description) <= 2000),
  constraint reports_status check (status in ('OPEN', 'IN_REVIEW', 'RESOLVED', 'DISMISSED'))
);

create index reports_status_created_idx on public.reports (status, created_at);
create index reports_reporter_idx on public.reports (reporter_profile_id);
create index reports_target_profile_idx on public.reports (target_profile_id);
create index reports_target_project_idx on public.reports (target_project_id);
create index reports_target_message_idx on public.reports (target_message_id);

create trigger reports_set_updated_at
before update on public.reports
for each row execute function public.set_updated_at();

alter table public.reports enable row level security;
revoke all privileges on table public.reports from public, anon, authenticated;

comment on table public.reports is
  'Backend-only reports. Restricted foreign keys preserve the linked evidence.';

commit;
