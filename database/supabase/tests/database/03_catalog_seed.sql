begin;

select plan(11);

select ok(
  (select count(*) >= 2 from public.sectors where is_active),
  'local seed contains at least two active sectors'
);
select is(
  (select count(*) from public.sectors),
  (select count(distinct slug) from public.sectors),
  'sector slugs are unique'
);
select is(
  (select count(*) from public.sectors where is_active),
  (select count(*) from public.sectors),
  'seeded sectors are active'
);
select ok(
  (select count(*) >= 8 from public.tags where is_active),
  'local seed contains at least eight active tags'
);
select is(
  (select count(*) from public.tags where is_active),
  (select count(*) from public.tags),
  'seeded tags are active'
);
select is(
  (select count(*) from public.tags),
  (select count(distinct slug) from public.tags),
  'tag slugs are unique'
);
select ok(
  (select count(*) >= 8 from public.skills where is_active),
  'local seed contains at least eight active skills'
);
select is(
  (select count(*) from public.skills where is_active),
  (select count(*) from public.skills),
  'seeded skills are active'
);
select is(
  (select count(*) from public.skills),
  (select count(distinct slug) from public.skills),
  'skill slugs are unique'
);
select is(
  (select count(*) from public.projects where status = 'PUBLISHED' and sector_id is not null),
  (select count(*) from public.projects where status = 'PUBLISHED'),
  'published projects reference a canonical sector'
);
select is(
  (select count(*)
   from public.projects p
   join public.sectors s on s.id = p.sector_id
   where p.status = 'PUBLISHED'),
  (select count(*) from public.projects where status = 'PUBLISHED'),
  'published projects resolve their canonical sector'
);

select * from finish();
rollback;
