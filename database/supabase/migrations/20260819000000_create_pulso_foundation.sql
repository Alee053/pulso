create extension if not exists "pgcrypto";

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  city text,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint profiles_display_name_length check (
    display_name is null or char_length(trim(display_name)) between 1 and 120
  ),
  constraint profiles_city_length check (
    city is null or char_length(trim(city)) between 1 and 120
  )
);

create table public.startups (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references public.profiles (id) on delete set null,
  slug text not null unique,
  name text not null,
  tagline text not null,
  description text,
  sector text not null,
  city text not null,
  stage text not null,
  status text not null default 'DRAFT',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint startups_slug_length check (char_length(slug) between 2 and 120),
  constraint startups_name_length check (char_length(trim(name)) between 1 and 160),
  constraint startups_tagline_length check (char_length(trim(tagline)) between 1 and 240),
  constraint startups_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create table public.startup_members (
  startup_id uuid not null references public.startups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'member',
  created_at timestamptz not null default timezone('utc', now()),
  primary key (startup_id, user_id),
  constraint startup_members_role check (role in ('owner', 'admin', 'member'))
);

create index startups_published_created_at_idx
  on public.startups (created_at desc, id desc)
  where status = 'PUBLISHED';

create index startup_members_user_id_idx
  on public.startup_members (user_id);

grant usage on schema public to anon, authenticated;
grant select, insert, update on public.profiles to authenticated;
grant select on public.startup_members to authenticated;

alter table public.profiles enable row level security;
alter table public.startups enable row level security;
alter table public.startup_members enable row level security;

create policy "Users can read their own profile"
  on public.profiles for select
  to authenticated
  using (id = (select auth.uid()));

create policy "Users can create their own profile"
  on public.profiles for insert
  to authenticated
  with check (id = (select auth.uid()));

create policy "Users can update their own profile"
  on public.profiles for update
  to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

create policy "Published startups are publicly readable"
  on public.startups for select
  to anon, authenticated
  using (status = 'PUBLISHED');

create policy "Users can read their own startup memberships"
  on public.startup_members for select
  to authenticated
  using (user_id = (select auth.uid()));

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = timezone('utc', now());
  return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger startups_set_updated_at
before update on public.startups
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    left(nullif(trim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), ''), 120)
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();
