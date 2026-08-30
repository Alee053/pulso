begin;

-- The first migration is historical. Create the canonical sector catalog
-- before transforming the foundation's textual startup.sector values.
create table public.sectors (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  description text,
  is_active boolean not null default true,
  sort_order integer not null default 0,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint sectors_slug_length check (char_length(slug) between 1 and 120),
  constraint sectors_name_length check (char_length(trim(name)) between 1 and 160)
);

with source_values as (
  select distinct coalesce(nullif(trim(sector), ''), 'Sin clasificar') as name
  from public.startups
), normalized_values as (
  select
    name,
    coalesce(
      nullif(
        trim(
          both '-' from regexp_replace(
            translate(lower(name), 'áéíóúüñ', 'aeiouun'),
            '[^a-z0-9]+',
            '-',
            'g'
          )
        ),
        ''
      ),
      'sector-' || substr(md5(name), 1, 12)
    ) as slug
  from source_values
)
insert into public.sectors (slug, name)
select slug, min(name)
from normalized_values
group by slug
on conflict (slug) do update set
  name = excluded.name,
  updated_at = timezone('utc', now());

alter table public.startups rename to projects;
alter table public.startup_members rename to project_members;

alter table public.projects rename column owner_id to created_by_profile_id;
alter table public.projects rename column sector to legacy_sector;
alter table public.projects rename constraint startups_pkey to projects_pkey;
alter table public.projects rename constraint startups_slug_key to projects_slug_key;
alter table public.projects rename constraint startups_slug_length to projects_slug_length;
alter table public.projects rename constraint startups_name_length to projects_name_length;
alter table public.projects rename constraint startups_tagline_length to projects_tagline_length;
alter table public.projects rename constraint startups_owner_id_fkey
  to projects_created_by_profile_id_fkey;
alter table public.projects drop constraint startups_status;

alter table public.projects
  add column short_description text,
  add column problem text,
  add column solution text,
  add column value_proposition text,
  add column product_or_service text,
  add column sector_id uuid,
  add column department text,
  add column logo_storage_path text,
  add column cover_storage_path text,
  add column media jsonb,
  add column external_links jsonb,
  add column verification_status text not null default 'UNVERIFIED',
  add column reviewed_by_profile_id uuid,
  add column review_note text,
  add column reviewed_at timestamptz,
  add column published_at timestamptz,
  add column archived_at timestamptz;

with normalized_projects as (
  select
    p.id,
    coalesce(
      nullif(
        trim(
          both '-' from regexp_replace(
            translate(lower(coalesce(nullif(trim(p.legacy_sector), ''), 'Sin clasificar')), 'áéíóúüñ', 'aeiouun'),
            '[^a-z0-9]+',
            '-',
            'g'
          )
        ),
        ''
      ),
      'sector-' || substr(md5(coalesce(nullif(trim(p.legacy_sector), ''), 'Sin clasificar')), 1, 12)
    ) as sector_slug
  from public.projects p
)
update public.projects p
set sector_id = s.id
from normalized_projects np
join public.sectors s on s.slug = np.sector_slug
where p.id = np.id;

update public.projects
set published_at = created_at
where status = 'PUBLISHED';

alter table public.projects alter column sector_id set not null;
alter table public.projects drop column legacy_sector;
alter table public.projects add constraint projects_sector_id_fkey
  foreign key (sector_id) references public.sectors (id);
alter table public.projects add constraint projects_reviewed_by_profile_id_fkey
  foreign key (reviewed_by_profile_id) references public.profiles (id) on delete set null;
alter table public.projects add constraint projects_status
  check (status in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'SUSPENDED'));

-- Seed projects are public system data created before user profiles exist. The
-- profile/project stories must always provide this value for user-owned data.
comment on column public.projects.created_by_profile_id is
  'Nullable only for pre-authenticated system seed rows; user-created projects must set it.';

alter trigger startups_set_updated_at on public.projects rename to projects_set_updated_at;
alter index public.startups_published_created_at_idx rename to projects_legacy_published_created_at_idx;
drop index public.projects_legacy_published_created_at_idx;
create index projects_status_published_at_id_idx
  on public.projects (status, published_at desc, id desc);

drop policy "Published startups are publicly readable" on public.projects;
create policy "Published projects are publicly readable"
  on public.projects for select
  to anon, authenticated
  using (status = 'PUBLISHED');

alter table public.project_members rename column startup_id to project_id;
alter table public.project_members rename column user_id to profile_id;
alter table public.project_members add column id uuid;
alter table public.project_members add column invitee_email text;
alter table public.project_members add column invite_token_digest text unique;
alter table public.project_members add column membership_status text not null default 'INVITED';
alter table public.project_members add column display_title text;
alter table public.project_members add column is_featured boolean not null default false;
alter table public.project_members add column is_public boolean not null default true;
alter table public.project_members add column created_by_profile_id uuid;
alter table public.project_members add column joined_at timestamptz;
alter table public.project_members add column updated_at timestamptz not null default timezone('utc', now());

update public.project_members
set id = gen_random_uuid(),
    role = upper(role),
    membership_status = 'ACTIVE',
    created_by_profile_id = profile_id,
    joined_at = created_at
where id is null;

alter table public.project_members alter column id set default gen_random_uuid();
alter table public.project_members alter column id set not null;
alter table public.project_members alter column created_by_profile_id set not null;
alter table public.project_members drop constraint startup_members_pkey;
alter table public.project_members add constraint project_members_pkey primary key (id);
alter table public.project_members alter column profile_id drop not null;
alter table public.project_members rename constraint startup_members_startup_id_fkey
  to project_members_project_id_fkey;
alter table public.project_members drop constraint startup_members_user_id_fkey;

-- Existing memberships referenced auth.users directly. Ensure their product
-- profiles exist before moving the relationship to public.profiles.
insert into public.profiles (id)
select distinct profile_id
from public.project_members
where profile_id is not null
on conflict (id) do nothing;

alter table public.project_members add constraint project_members_profile_id_fkey
  foreign key (profile_id) references public.profiles (id) on delete cascade;
alter table public.project_members add constraint project_members_created_by_profile_id_fkey
  foreign key (created_by_profile_id) references public.profiles (id) on delete cascade;
alter table public.project_members drop constraint startup_members_role;
alter table public.project_members add constraint project_members_role
  check (role in ('OWNER', 'ADMIN', 'MEMBER'));
alter table public.project_members add constraint project_members_status
  check (membership_status in ('INVITED', 'ACTIVE', 'LEFT', 'REMOVED'));
alter table public.project_members add constraint project_members_identity
  check (profile_id is not null or invitee_email is not null);

alter index public.startup_members_user_id_idx rename to project_members_profile_id_idx;
create unique index project_members_project_profile_key
  on public.project_members (project_id, profile_id);
create index project_members_project_status_public_idx
  on public.project_members (project_id, membership_status, is_public);

drop policy "Users can read their own startup memberships" on public.project_members;
create policy "Users can read their own project memberships"
  on public.project_members for select
  to authenticated
  using (profile_id = (select auth.uid()));

create trigger project_members_set_updated_at
before update on public.project_members
for each row execute function public.set_updated_at();

create trigger sectors_set_updated_at
before update on public.sectors
for each row execute function public.set_updated_at();

grant select on public.sectors to authenticated;
alter table public.sectors enable row level security;
create policy "Active sectors are readable by authenticated users"
  on public.sectors for select
  to authenticated
  using (is_active);

grant select on public.project_members to authenticated;

commit;
