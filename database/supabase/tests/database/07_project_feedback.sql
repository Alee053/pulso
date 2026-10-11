begin;

select plan(6);

select has_table('public', 'project_feedback', 'project feedback table exists');
select has_table('public', 'interaction_events', 'interaction history table exists');
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.project_feedback'::regclass and contype = 'p'),
  1::bigint,
  'one primary key preserves one state per profile and project'
);
select is(
  (select pg_get_constraintdef(oid) like '%FEEDBACK_CLEARED%'
   from pg_constraint
   where conrelid = 'public.interaction_events'::regclass
     and conname = 'interaction_events_event_type'),
  true,
  'event type constraint permits the clearing event'
);
select is(
  has_table_privilege('anon', 'public.project_feedback', 'select'),
  false,
  'anonymous users cannot read private feedback'
);
select is(
  has_table_privilege('authenticated', 'public.interaction_events', 'insert'),
  false,
  'event insertion belongs to the backend, not the browser'
);

select * from finish();
rollback;
