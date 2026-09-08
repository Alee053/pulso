begin;

select plan(5);

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
