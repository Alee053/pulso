begin;

create table public.profile_preferences (
  profile_id uuid primary key references public.profiles (id) on delete cascade,
  intentions jsonb not null default '[]'::jsonb,
  preferred_sectors jsonb not null default '[]'::jsonb,
  preferred_stages jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default timezone('utc', now()),
  constraint profile_preferences_intentions_array check (
    jsonb_typeof(intentions) = 'array'
  ),
  constraint profile_preferences_preferred_sectors_array check (
    jsonb_typeof(preferred_sectors) = 'array'
  ),
  constraint profile_preferences_preferred_stages_array check (
    jsonb_typeof(preferred_stages) = 'array'
  )
);

create trigger profile_preferences_set_updated_at
before update on public.profile_preferences
for each row execute function public.set_updated_at();

revoke all privileges on table public.profile_preferences from anon, authenticated;
grant select on public.profile_preferences to authenticated;
grant insert (
  profile_id,
  intentions,
  preferred_sectors,
  preferred_stages
) on public.profile_preferences to authenticated;
grant update (
  intentions,
  preferred_sectors,
  preferred_stages
) on public.profile_preferences to authenticated;

alter table public.profile_preferences enable row level security;

create policy "Users can read their own profile preferences"
  on public.profile_preferences for select
  to authenticated
  using (profile_id = (select auth.uid()));

create policy "Users can create their own profile preferences"
  on public.profile_preferences for insert
  to authenticated
  with check (profile_id = (select auth.uid()));

create policy "Users can update their own profile preferences"
  on public.profile_preferences for update
  to authenticated
  using (profile_id = (select auth.uid()))
  with check (profile_id = (select auth.uid()));

comment on table public.profile_preferences is
  'Onboarding preferences keyed one-to-one by the Supabase Auth profile UUID.';
comment on column public.profile_preferences.intentions is
  'JSON array of controlled onboarding intentions.';
comment on column public.profile_preferences.preferred_sectors is
  'JSON array of sector IDs or slugs selected during onboarding.';
comment on column public.profile_preferences.preferred_stages is
  'JSON array of project stages selected during onboarding.';

commit;
