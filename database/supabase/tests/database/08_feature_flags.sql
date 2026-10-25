begin;

select plan(14);

select has_table('public', 'feature_flags', 'feature flags use a database table');
select has_column('public', 'feature_flags', 'flag_key', 'each flag has a stable identifier');
select has_column('public', 'feature_flags', 'enabled', 'each flag stores a boolean state');
select is(
  (select count(*) from public.feature_flags),
  2::bigint,
  'the initial configuration contains only the selected flags'
);
select is(
  (select array_agg(flag_key order by flag_key) from public.feature_flags),
  array['language_switcher', 'project_creation']::text[],
  'persisted keys match the backend allowlist'
);
select is(
  (select enabled from public.feature_flags where flag_key = 'language_switcher'),
  true,
  'language switching is enabled by default'
);
select is(
  (select enabled from public.feature_flags where flag_key = 'project_creation'),
  true,
  'project creation is enabled by default'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.feature_flags'::regclass),
  true,
  'row level security is enabled'
);
select is(
  has_table_privilege('anon', 'public.feature_flags', 'select'),
  false,
  'anonymous clients cannot query persisted flags directly'
);
select is(
  not (
    has_table_privilege('anon', 'public.feature_flags', 'select')
    or has_table_privilege('anon', 'public.feature_flags', 'insert')
    or has_table_privilege('anon', 'public.feature_flags', 'update')
    or has_table_privilege('anon', 'public.feature_flags', 'delete')
  ),
  true,
  'anonymous clients cannot read or write feature flags directly'
);
select is(
  has_table_privilege('authenticated', 'public.feature_flags', 'select'),
  false,
  'authenticated clients cannot query persisted flags directly'
);
select is(
  has_table_privilege('authenticated', 'public.feature_flags', 'update'),
  false,
  'authenticated clients cannot update flags outside the backend'
);
select is(
  has_table_privilege('authenticated', 'public.feature_flags', 'insert'),
  false,
  'authenticated clients cannot create arbitrary flags'
);
select is(
  has_table_privilege('authenticated', 'public.feature_flags', 'delete'),
  false,
  'authenticated clients cannot remove persisted flags'
);

select * from finish();

rollback;
