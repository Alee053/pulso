begin;

create table public.project_tags (
  project_id uuid not null references public.projects (id) on delete cascade,
  tag_id uuid not null references public.tags (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  constraint project_tags_pkey primary key (project_id, tag_id)
);

create index project_tags_tag_id_idx
  on public.project_tags (tag_id);

-- Project reads stay behind the Spring API. Keep the relationship table
-- inaccessible through direct Supabase client roles as well.
revoke all privileges on table public.project_tags from anon, authenticated;
alter table public.project_tags enable row level security;

commit;
