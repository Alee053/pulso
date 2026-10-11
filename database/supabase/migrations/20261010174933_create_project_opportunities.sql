begin;

create table public.project_opportunities (
  id uuid primary key default gen_random_uuid(),
  project_id uuid not null references public.projects (id) on delete cascade,
  opportunity_type text not null,
  title text not null,
  description text not null,
  status text not null default 'OPEN',
  city text,
  department text,
  remote_allowed boolean not null default false,
  created_by_profile_id uuid not null references public.profiles (id),
  starts_at timestamptz,
  ends_at timestamptz,
  closed_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint project_opportunities_type check (
    opportunity_type in (
      'TALENT', 'COLLABORATION', 'CUSTOMER', 'BETA_TESTER', 'INVESTMENT',
      'MENTOR', 'SUPPLIER', 'PARTNERSHIP', 'OTHER'
    )
  ),
  constraint project_opportunities_status check (
    status in ('DRAFT', 'OPEN', 'PAUSED', 'FILLED', 'CLOSED', 'ARCHIVED')
  ),
  constraint project_opportunities_title_not_blank check (char_length(trim(title)) > 0),
  constraint project_opportunities_description_not_blank check (char_length(trim(description)) > 0),
  constraint project_opportunities_dates_ordered check (
    starts_at is null or ends_at is null or ends_at >= starts_at
  )
);

create index project_opportunities_project_status_created_idx
  on public.project_opportunities (project_id, status, created_at desc, id);
create index project_opportunities_type_location_idx
  on public.project_opportunities (opportunity_type, city, department);

alter table public.interaction_events
  add constraint interaction_events_opportunity_id_fkey
  foreign key (opportunity_id) references public.project_opportunities (id);

create trigger project_opportunities_set_updated_at
before update on public.project_opportunities
for each row execute function public.set_updated_at();

create function public.require_public_project_for_open_opportunity()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  project_status text;
  project_sector_id uuid;
  sector_is_active boolean;
begin
  if new.status <> 'OPEN' then
    return new;
  end if;

  select status, sector_id
    into project_status, project_sector_id
  from public.projects
  where id = new.project_id
  for update;

  select is_active
    into sector_is_active
  from public.sectors
  where id = project_sector_id
  for share;

  if project_status <> 'PUBLISHED' or coalesce(sector_is_active, false) = false then
    raise exception using
      errcode = '23514',
      constraint = 'project_opportunities_open_project_public',
      message = 'OPEN opportunities require a published project with an active sector.';
  end if;

  return new;
end;
$$;

create trigger project_opportunities_require_public_project
before insert or update of project_id, status on public.project_opportunities
for each row execute function public.require_public_project_for_open_opportunity();

create function public.pause_open_opportunities_for_project()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.status <> 'PUBLISHED'
     or not exists (
       select 1
       from public.sectors
       where id = new.sector_id and is_active = true
     ) then
    update public.project_opportunities
    set status = 'PAUSED'
    where project_id = new.id and status = 'OPEN';
  end if;

  return new;
end;
$$;

create trigger projects_pause_open_opportunities
after update of status, sector_id on public.projects
for each row execute function public.pause_open_opportunities_for_project();

create function public.pause_open_opportunities_for_inactive_sector()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if old.is_active and not new.is_active then
    update public.project_opportunities opportunity
    set status = 'PAUSED'
    from public.projects project
    where opportunity.project_id = project.id
      and project.sector_id = new.id
      and project.status = 'PUBLISHED'
      and opportunity.status = 'OPEN';
  end if;

  return new;
end;
$$;

create trigger sectors_pause_open_opportunities
after update of is_active on public.sectors
for each row execute function public.pause_open_opportunities_for_inactive_sector();

alter table public.project_opportunities enable row level security;
revoke all privileges on table public.project_opportunities from anon, authenticated;

comment on table public.project_opportunities is
  'Structured project needs managed through the authenticated backend API. OPEN rows require a published project with an active sector.';

commit;
