begin;

select plan(11);

select has_table('public', 'reports', 'reports use a database table');
select ok(
  (select relrowsecurity from pg_class where oid = 'public.reports'::regclass),
  'reports enable row level security'
);
select ok(not has_table_privilege('anon', 'public.reports', 'select'), 'anonymous cannot read reports');
select ok(not has_table_privilege('authenticated', 'public.reports', 'select'), 'authenticated cannot read reports');
select ok(not has_table_privilege('authenticated', 'public.reports', 'insert'), 'authenticated cannot insert reports');
select ok(not has_table_privilege('authenticated', 'public.reports', 'update'), 'authenticated cannot update reports');

insert into auth.users (id, raw_user_meta_data)
values ('29000000-0000-4000-8000-000000000001', '{"display_name":"Test reporter"}'),
       ('29000000-0000-4000-8000-000000000002', '{"display_name":"Test target"}');

insert into public.reports (reporter_profile_id, target_profile_id, reason)
values ('29000000-0000-4000-8000-000000000001', '29000000-0000-4000-8000-000000000002', 'ABUSE');

select is(
  (select status from public.reports where reporter_profile_id = '29000000-0000-4000-8000-000000000001'),
  'OPEN',
  'a new report is open'
);
select is(
  (select description from public.reports where reporter_profile_id = '29000000-0000-4000-8000-000000000001'),
  null::text,
  'description can be omitted'
);
select throws_ok(
  $$insert into public.reports (reporter_profile_id, reason)
    values ('29000000-0000-4000-8000-000000000001', 'ABUSE')$$,
  '23514', null, 'a report requires a target'
);
select throws_ok(
  $$insert into public.reports (reporter_profile_id, target_profile_id, target_project_id, reason)
    values ('29000000-0000-4000-8000-000000000001', '29000000-0000-4000-8000-000000000002',
            '11111111-1111-1111-1111-111111111111', 'ABUSE')$$,
  '23514', null, 'a report cannot have multiple targets'
);
select is(
  (select confdeltype::text from pg_constraint
   where conrelid = 'public.reports'::regclass and conname = 'reports_target_profile_id_fkey'),
  'r',
  'the reported profile reference restricts hard deletion'
);

select * from finish();
rollback;
