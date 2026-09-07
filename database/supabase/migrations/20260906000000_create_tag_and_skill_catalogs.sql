begin;

-- Align the deployed schema with the catalog entities already defined in
-- database/pulso.dbml. Relationship tables remain outside this migration;
-- PUL-44 only needs read access to the canonical catalogs.
create table public.tags (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  tag_type text not null default 'TOPIC',
  is_active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint tags_slug_length check (char_length(slug) between 1 and 120),
  constraint tags_name_length check (char_length(trim(name)) between 1 and 160),
  constraint tags_type check (tag_type in ('TOPIC', 'INDUSTRY', 'OTHER'))
);

create table public.skills (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  category text,
  is_active boolean not null default true,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint skills_slug_length check (char_length(slug) between 1 and 120),
  constraint skills_name_length check (char_length(trim(name)) between 1 and 160)
);

create index sectors_active_catalog_order_idx
  on public.sectors (is_active, sort_order, name, id);
create index tags_active_catalog_order_idx
  on public.tags (is_active, name, id);
create index skills_active_catalog_order_idx
  on public.skills (is_active, name, id);

create trigger tags_set_updated_at
before update on public.tags
for each row execute function public.set_updated_at();

create trigger skills_set_updated_at
before update on public.skills
for each row execute function public.set_updated_at();

grant select on public.tags to authenticated;
grant select on public.skills to authenticated;

alter table public.tags enable row level security;
alter table public.skills enable row level security;

create policy "Active tags are readable by authenticated users"
  on public.tags for select
  to authenticated
  using (is_active);

create policy "Active skills are readable by authenticated users"
  on public.skills for select
  to authenticated
  using (is_active);

commit;