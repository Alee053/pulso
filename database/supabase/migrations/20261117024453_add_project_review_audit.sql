begin;

create table public.admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_profile_id uuid not null references public.profiles (id),
  action text not null check (char_length(trim(action)) between 1 and 120),
  target_type text,
  target_id uuid,
  metadata jsonb,
  created_at timestamptz not null default now()
);

create index admin_audit_logs_actor_created_idx
  on public.admin_audit_logs (actor_profile_id, created_at desc);
create index admin_audit_logs_target_created_idx
  on public.admin_audit_logs (target_type, target_id, created_at desc);

alter table public.admin_audit_logs enable row level security;
revoke all privileges on table public.admin_audit_logs from anon, authenticated;

create index projects_pending_review_created_idx
  on public.projects (created_at, id) where status = 'PENDING_REVIEW';

comment on table public.admin_audit_logs is
  'Backend-only administrative decisions. Metadata contains state changes, never submitted project content or credentials.';

commit;
