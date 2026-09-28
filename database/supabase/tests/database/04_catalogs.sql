begin;

select plan(49);

select has_table('public', 'sectors', 'sectors catalog table exists');
select has_table('public', 'tags', 'tags catalog table exists');
select has_table('public', 'skills', 'skills catalog table exists');

select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.sectors'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'slug'),
  1::bigint,
  'sector slugs are unique'
);
select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.sectors'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'name'),
  1::bigint,
  'sector names are unique'
);
select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.tags'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'slug'),
  1::bigint,
  'tag slugs are unique'
);
select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.tags'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'name'),
  1::bigint,
  'tag names are unique'
);
select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.skills'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'slug'),
  1::bigint,
  'skill slugs are unique'
);
select is(
  (select count(*)
   from pg_constraint c
   join pg_attribute a
     on a.attrelid = c.conrelid
    and a.attnum = any(c.conkey)
   where c.conrelid = 'public.skills'::regclass
     and c.contype = 'u'
     and cardinality(c.conkey) = 1
     and a.attname = 'name'),
  1::bigint,
  'skill names are unique'
);

select is(
  (select relrowsecurity
   from pg_class
   where oid = 'public.sectors'::regclass),
  true,
  'row level security is enabled for sectors'
);
select is(
  (select relrowsecurity
   from pg_class
   where oid = 'public.tags'::regclass),
  true,
  'row level security is enabled for tags'
);
select is(
  (select relrowsecurity
   from pg_class
   where oid = 'public.skills'::regclass),
  true,
  'row level security is enabled for skills'
);

select is(
  (select count(*)
   from pg_policies
   where schemaname = 'public'
     and tablename = 'sectors'
     and policyname = 'Active sectors are readable by authenticated users'),
  1::bigint,
  'sectors expose an authenticated active-row policy'
);
select is(
  (select count(*)
   from pg_policies
   where schemaname = 'public'
     and tablename = 'tags'
     and policyname = 'Active tags are readable by authenticated users'),
  1::bigint,
  'tags expose an authenticated active-row policy'
);
select is(
  (select count(*)
   from pg_policies
   where schemaname = 'public'
     and tablename = 'skills'
     and policyname = 'Active skills are readable by authenticated users'),
  1::bigint,
  'skills expose an authenticated active-row policy'
);

select is(
  has_table_privilege('authenticated', 'public.sectors', 'select'),
  true,
  'authenticated users can read sectors subject to RLS'
);
select is(
  has_table_privilege('authenticated', 'public.tags', 'select'),
  true,
  'authenticated users can read tags subject to RLS'
);
select is(
  has_table_privilege('authenticated', 'public.skills', 'select'),
  true,
  'authenticated users can read skills subject to RLS'
);
select is(
  has_table_privilege('anon', 'public.sectors', 'select'),
  false,
  'anonymous clients cannot read sectors directly'
);
select is(
  has_table_privilege('anon', 'public.tags', 'select'),
  false,
  'anonymous clients cannot read tags directly'
);
select is(
  has_table_privilege('anon', 'public.skills', 'select'),
  false,
  'anonymous clients cannot read skills directly'
);
select is(
  has_table_privilege('authenticated', 'public.sectors', 'insert'),
  false,
  'authenticated users cannot insert sectors directly'
);
select is(
  has_table_privilege('authenticated', 'public.tags', 'insert'),
  false,
  'authenticated users cannot insert tags directly'
);
select is(
  has_table_privilege('authenticated', 'public.skills', 'insert'),
  false,
  'authenticated users cannot insert skills directly'
);
select is(
  has_table_privilege('authenticated', 'public.sectors', 'update'),
  false,
  'authenticated users cannot update sectors directly'
);
select is(
  has_table_privilege('authenticated', 'public.tags', 'update'),
  false,
  'authenticated users cannot update tags directly'
);
select is(
  has_table_privilege('authenticated', 'public.skills', 'update'),
  false,
  'authenticated users cannot update skills directly'
);
select is(
  has_table_privilege('authenticated', 'public.sectors', 'delete'),
  false,
  'authenticated users cannot delete sectors directly'
);
select is(
  has_table_privilege('authenticated', 'public.tags', 'delete'),
  false,
  'authenticated users cannot delete tags directly'
);
select is(
  has_table_privilege('authenticated', 'public.skills', 'delete'),
  false,
  'authenticated users cannot delete skills directly'
);

insert into public.sectors (id, slug, name, is_active, sort_order)
values
  ('00000000-0000-0000-0000-0000000000a1', 'catalog-sector-active', 'Catalog Sector Active', true, 99),
  ('00000000-0000-0000-0000-0000000000a2', 'catalog-sector-inactive', 'Catalog Sector Inactive', false, 100);
insert into public.tags (id, slug, name, tag_type, is_active)
values
  ('10000000-0000-0000-0000-0000000000a1', 'catalog-tag-active', 'Catalog Tag Active', 'TOPIC', true),
  ('10000000-0000-0000-0000-0000000000a2', 'catalog-tag-inactive', 'Catalog Tag Inactive', 'TOPIC', false);
insert into public.skills (id, slug, name, is_active)
values
  ('20000000-0000-0000-0000-0000000000a1', 'catalog-skill-active', 'Catalog Skill Active', true),
  ('20000000-0000-0000-0000-0000000000a2', 'catalog-skill-inactive', 'Catalog Skill Inactive', false);

select ok(
  (select created_at is not null and updated_at is not null
   from public.sectors
   where id = '00000000-0000-0000-0000-0000000000a1'),
  'sector timestamps have defaults'
);
select ok(
  (select created_at is not null and updated_at is not null
   from public.tags
   where id = '10000000-0000-0000-0000-0000000000a1'),
  'tag timestamps have defaults'
);
select ok(
  (select created_at is not null and updated_at is not null
   from public.skills
   where id = '20000000-0000-0000-0000-0000000000a1'),
  'skill timestamps have defaults'
);

update public.sectors
set updated_at = '2000-01-01 00:00:00+00'
where id = '00000000-0000-0000-0000-0000000000a1';
update public.tags
set updated_at = '2000-01-01 00:00:00+00'
where id = '10000000-0000-0000-0000-0000000000a1';
update public.skills
set updated_at = '2000-01-01 00:00:00+00'
where id = '20000000-0000-0000-0000-0000000000a1';

select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.sectors
   where id = '00000000-0000-0000-0000-0000000000a1'),
  'sector updates refresh updated_at'
);
select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.tags
   where id = '10000000-0000-0000-0000-0000000000a1'),
  'tag updates refresh updated_at'
);
select ok(
  (select updated_at > '2000-01-01 00:00:00+00'
   from public.skills
   where id = '20000000-0000-0000-0000-0000000000a1'),
  'skill updates refresh updated_at'
);

set local role authenticated;
select set_config(
  'request.jwt.claim.sub',
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  true
);

select is(
  (select slug from public.sectors
   where id = '00000000-0000-0000-0000-0000000000a1'),
  'catalog-sector-active',
  'authenticated users can read active sectors'
);
select is(
  (select count(*) from public.sectors
   where id = '00000000-0000-0000-0000-0000000000a2'),
  0::bigint,
  'authenticated users cannot read inactive sectors'
);
select is(
  (select slug from public.tags
   where id = '10000000-0000-0000-0000-0000000000a1'),
  'catalog-tag-active',
  'authenticated users can read active tags'
);
select is(
  (select count(*) from public.tags
   where id = '10000000-0000-0000-0000-0000000000a2'),
  0::bigint,
  'authenticated users cannot read inactive tags'
);
select is(
  (select slug from public.skills
   where id = '20000000-0000-0000-0000-0000000000a1'),
  'catalog-skill-active',
  'authenticated users can read active skills'
);
select is(
  (select count(*) from public.skills
   where id = '20000000-0000-0000-0000-0000000000a2'),
  0::bigint,
  'authenticated users cannot read inactive skills'
);

set local role postgres;

select is(
  (select count(*)
   from public.projects p
   left join public.sectors s on s.id = p.sector_id
   where s.id is null),
  0::bigint,
  'all existing projects reference a sector'
);
select is(
  (select is_nullable
   from information_schema.columns
   where table_schema = 'public'
     and table_name = 'projects'
     and column_name = 'sector_id'),
  'NO',
  'project sector references are required'
);
select is(
  (select count(*)
   from pg_constraint
   where conrelid = 'public.projects'::regclass
     and confrelid = 'public.sectors'::regclass
     and contype = 'f'),
  1::bigint,
  'projects reference the canonical sectors table'
);
select throws_ok(
  $$insert into public.projects (slug, name, tagline, sector_id, city, stage)
    values (
      'catalog-invalid-project',
      'Catalog Invalid Project',
      'Invalid sector reference',
      '99999999-9999-9999-9999-999999999999',
      'La Paz',
      'MVP'
    )$$,
  '23503',
  'insert or update on table "projects" violates foreign key constraint "projects_sector_id_fkey"',
  'projects reject unknown sector references'
);

select throws_ok(
  $$insert into public.sectors (slug, name, sort_order)
    values ('catalog-sector-duplicate-name', 'Catalog Sector Active', 101)$$,
  '23505',
  'duplicate key value violates unique constraint "sectors_name_key"',
  'sector names reject duplicates'
);
select throws_ok(
  $$insert into public.tags (slug, name, tag_type)
    values ('catalog-tag-duplicate-name', 'Catalog Tag Active', 'TOPIC')$$,
  '23505',
  'duplicate key value violates unique constraint "tags_name_key"',
  'tag names reject duplicates'
);
select throws_ok(
  $$insert into public.skills (slug, name)
    values ('catalog-skill-duplicate-name', 'Catalog Skill Active')$$,
  '23505',
  'duplicate key value violates unique constraint "skills_name_key"',
  'skill names reject duplicates'
);

select * from finish();
rollback;
