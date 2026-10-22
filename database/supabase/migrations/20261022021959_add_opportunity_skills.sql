begin;

create table public.opportunity_skills (
  opportunity_id uuid not null
    references public.project_opportunities (id) on delete cascade,
  skill_id uuid not null
    references public.skills (id) on delete restrict,
  importance text not null default 'PREFERRED',
  created_at timestamptz not null default timezone('utc', now()),
  constraint opportunity_skills_pkey primary key (opportunity_id, skill_id),
  constraint opportunity_skills_importance check (
    importance in ('REQUIRED', 'PREFERRED')
  )
);

create index opportunity_skills_skill_id_idx
  on public.opportunity_skills (skill_id);

alter table public.opportunity_skills enable row level security;
revoke all privileges on table public.opportunity_skills from anon, authenticated;

comment on table public.opportunity_skills is
  'Opportunity-to-skill requirements managed through the authenticated backend API.';

commit;