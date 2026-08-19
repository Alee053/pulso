begin;

select plan(8);

select has_table('public', 'profiles', 'profiles table exists');
select has_table('public', 'startups', 'startups table exists');
select has_table('public', 'startup_members', 'startup_members table exists');
select has_index(
  'public',
  'startups',
  'startups_published_created_at_idx',
  'published startup ordering has an index'
);
select is(
  (select count(*) from public.startups where status = 'PUBLISHED'),
  2::bigint,
  'local seed contains two published startups'
);
select is(
  (select count(*) from pg_policies
    where schemaname = 'public'
      and tablename = 'startups'
      and policyname = 'Published startups are publicly readable'),
  1::bigint,
  'published startups have a public read policy'
);
select is(
  (select relrowsecurity from pg_class
    where oid = 'public.startups'::regclass),
  true,
  'row level security is enabled for startups'
);
select is(
  has_table_privilege('anon', 'public.startups', 'select'),
  false,
  'public startup rows are not exposed through a direct table grant'
);

select * from finish();
rollback;
