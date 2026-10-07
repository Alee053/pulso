begin;

alter table public.profile_preferences
  add column recommendation_weights jsonb not null default '{}'::jsonb,
  add constraint profile_preferences_recommendation_weights_object check (
    jsonb_typeof(recommendation_weights) = 'object'
  );

create table public.profile_tags (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  tag_id uuid not null references public.tags (id) on delete restrict,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (profile_id, tag_id)
);

create index profile_tags_tag_id_idx on public.profile_tags (tag_id);

create table public.profile_skills (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  skill_id uuid not null references public.skills (id) on delete restrict,
  proficiency text,
  is_primary boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (profile_id, skill_id),
  constraint profile_skills_proficiency check (
    proficiency is null or proficiency in ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')
  )
);

create index profile_skills_skill_id_idx on public.profile_skills (skill_id);

create table public.saved_projects (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete cascade,
  saved_at timestamptz not null default timezone('utc', now()),
  unsaved_at timestamptz,
  primary key (profile_id, project_id)
);

create index saved_projects_active_profile_idx
  on public.saved_projects (profile_id, saved_at desc, project_id)
  where unsaved_at is null;

create index saved_projects_project_id_idx on public.saved_projects (project_id);

create table public.project_feedback (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete cascade,
  feedback_type text not null,
  changed_at timestamptz not null default timezone('utc', now()),
  primary key (profile_id, project_id),
  constraint project_feedback_type check (
    feedback_type in ('INTERESTED', 'DISMISSED', 'NONE')
  )
);

create index project_feedback_profile_type_idx
  on public.project_feedback (profile_id, feedback_type, changed_at desc, project_id);

create index project_feedback_project_type_idx
  on public.project_feedback (project_id, feedback_type);

create table public.interaction_events (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid references public.profiles (id) on delete set null,
  anonymous_session_id uuid,
  project_id uuid references public.projects (id) on delete set null,
  opportunity_id uuid,
  event_type text not null,
  source text not null,
  position integer,
  duration_ms integer,
  metadata jsonb,
  occurred_at timestamptz not null default timezone('utc', now()),
  constraint interaction_events_event_type check (
    event_type in (
      'PROJECT_VIEWED', 'PROJECT_OPENED', 'MEMBER_VIEWED', 'OPPORTUNITY_VIEWED',
      'SAVED', 'UNSAVED', 'INTERESTED', 'DISMISSED', 'SHARED',
      'CONTACT_STARTED', 'MESSAGE_STARTED'
    )
  ),
  constraint interaction_events_source check (
    source in ('FEED', 'SEARCH', 'PROJECT_DETAIL', 'OPPORTUNITY', 'MESSAGE', 'EXTERNAL_SHARE')
  ),
  constraint interaction_events_position_nonnegative check (position is null or position >= 0),
  constraint interaction_events_duration_nonnegative check (duration_ms is null or duration_ms >= 0),
  constraint interaction_events_metadata_object check (
    metadata is null or jsonb_typeof(metadata) = 'object'
  )
);

create index interaction_events_profile_occurred_idx
  on public.interaction_events (profile_id, occurred_at desc);

create index interaction_events_project_type_occurred_idx
  on public.interaction_events (project_id, event_type, occurred_at desc);

create index interaction_events_type_occurred_idx
  on public.interaction_events (event_type, occurred_at desc);

alter table public.profile_tags enable row level security;
alter table public.profile_skills enable row level security;
alter table public.saved_projects enable row level security;
alter table public.project_feedback enable row level security;
alter table public.interaction_events enable row level security;

-- Product data is read and written through the authenticated backend API.
revoke all privileges on table public.profile_tags from anon, authenticated;
revoke all privileges on table public.profile_skills from anon, authenticated;
revoke all privileges on table public.saved_projects from anon, authenticated;
revoke all privileges on table public.project_feedback from anon, authenticated;
revoke all privileges on table public.interaction_events from anon, authenticated;

create policy "Users can read their own profile tags"
  on public.profile_tags for select
  to authenticated using (profile_id = (select auth.uid()));
create policy "Users can write their own profile tags"
  on public.profile_tags for all
  to authenticated using (profile_id = (select auth.uid()))
  with check (profile_id = (select auth.uid()));

create policy "Users can read their own profile skills"
  on public.profile_skills for select
  to authenticated using (profile_id = (select auth.uid()));
create policy "Users can write their own profile skills"
  on public.profile_skills for all
  to authenticated using (profile_id = (select auth.uid()))
  with check (profile_id = (select auth.uid()));

create policy "Users can read their own saved projects"
  on public.saved_projects for select
  to authenticated using (profile_id = (select auth.uid()));
create policy "Users can write their own saved projects"
  on public.saved_projects for all
  to authenticated using (profile_id = (select auth.uid()))
  with check (profile_id = (select auth.uid()));

create policy "Users can read their own project feedback"
  on public.project_feedback for select
  to authenticated using (profile_id = (select auth.uid()));
create policy "Users can write their own project feedback"
  on public.project_feedback for all
  to authenticated using (profile_id = (select auth.uid()))
  with check (profile_id = (select auth.uid()));

comment on column public.profile_preferences.recommendation_weights is
  'Bounded recommendation weights updated with persisted project feedback.';
comment on table public.profile_tags is
  'Tag selections owned by a profile and saved through the backend preferences API.';
comment on table public.profile_skills is
  'Skill selections owned by a profile and saved through the backend preferences API.';
comment on table public.saved_projects is
  'Current saved-project state; changes are recorded in interaction_events.';
comment on table public.project_feedback is
  'Current interested or dismissed state for a profile and project.';
comment on table public.interaction_events is
  'Append-only interaction history used to retain discovery signals. opportunity_id remains unconstrained until opportunity persistence is migrated.';

commit;
