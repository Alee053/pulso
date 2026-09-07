begin;

select plan(19);

select has_table('public', 'profiles', 'profiles table exists');
select has_table('public', 'sectors', 'sectors table exists');
select has_table('public', 'tags', 'tags table exists');
select has_table('public', 'skills', 'skills table exists');
select has_table('public', 'projects', 'projects table exists');
select has_table('public', 'project_members', 'project_members table exists');
select has_index(
  'public',
  'projects',
  'projects_status_published_at_id_idx',
  'published project ordering has an index'
);
select is(
  (select count(*) from public.sectors where is_active),
  2::bigint,
  'local seed contains two active sectors'
);

select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'tags'
      and policyname = 'Active tags are readable by authenticated users'),
  1::bigint,
  'active tags have an authenticated read policy'
);
select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'skills'
      and policyname = 'Active skills are readable by authenticated users'),
  1::bigint,
  'active skills have an authenticated read policy'
);
select has_index(
  'public',
  'sectors',
  'sectors_active_catalog_order_idx',
  'active sectors have a stable catalog ordering index'
);
select has_index(
  'public',
  'tags',
  'tags_active_catalog_order_idx',
  'active tags have a stable catalog ordering index'
);
select has_index(
  'public',
  'skills',
  'skills_active_catalog_order_idx',
  'active skills have a stable catalog ordering index'
);

select is(
  (select count(*) from public.projects where status = 'PUBLISHED'),
  2::bigint,
  'local seed contains two published projects'
);
select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'projects'
      and policyname = 'Published projects are publicly readable'),
  1::bigint,
  'published projects have a public read policy'
);
select is(
  (select relrowsecurity from pg_class
    where oid = 'public.projects'::regclass),
  true,
  'row level security is enabled for projects'
);
select is(
  has_table_privilege('anon', 'public.projects', 'select'),
  false,
  'public project rows are not exposed through a direct table grant'
);
select is(
  (select count(*) from pg_constraint
    where conrelid = 'public.profiles'::regclass
      and confrelid = 'auth.users'::regclass
      and contype = 'f'),
  1::bigint,
  'profiles remain linked to Supabase Auth users'
);
select is(
  (select count(*) from pg_constraint
    where conrelid = 'public.project_members'::regclass
      and confrelid = 'public.profiles'::regclass
      and contype = 'f'),
  2::bigint,
  'project memberships reference profiles for member and creator'
);

select * from finish();
rollback;