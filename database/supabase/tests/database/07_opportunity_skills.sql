begin;

select plan(17);

select has_table('public', 'opportunity_skills', 'opportunity skills use a canonical table');
select has_column('public', 'opportunity_skills', 'importance', 'associations store importance');
select has_index(
  'public',
  'opportunity_skills',
  'opportunity_skills_skill_id_idx',
  'skills can be located from opportunity associations'
);
select is(
  (select pg_get_constraintdef(oid)
   from pg_constraint
   where conrelid = 'public.opportunity_skills'::regclass
     and conname = 'opportunity_skills_pkey'),
  'PRIMARY KEY (opportunity_id, skill_id)',
  'an opportunity cannot contain the same skill more than once'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.opportunity_skills'::regclass
     and confrelid = 'public.project_opportunities'::regclass
     and contype = 'f'),
  1::bigint,
  'each association references an opportunity'
);
select is(
  (select count(*) from pg_constraint
   where conrelid = 'public.opportunity_skills'::regclass
     and confrelid = 'public.skills'::regclass
     and contype = 'f'),
  1::bigint,
  'each association references a catalog skill'
);
select is(
  (select confdeltype from pg_constraint
   where conrelid = 'public.opportunity_skills'::regclass
     and confrelid = 'public.project_opportunities'::regclass
     and contype = 'f'),
  'c',
  'deleting an opportunity cascades to its associations'
);
select is(
  (select confdeltype from pg_constraint
   where conrelid = 'public.opportunity_skills'::regclass
     and confrelid = 'public.skills'::regclass
     and contype = 'f'),
  'r',
  'deleting a referenced catalog skill is restricted'
);
select is(
  (select relrowsecurity from pg_class where oid = 'public.opportunity_skills'::regclass),
  true,
  'row level security is enabled'
);
select is(
  has_table_privilege('anon', 'public.opportunity_skills', 'select'),
  false,
  'anonymous clients cannot read associations directly'
);
select is(
  has_table_privilege('authenticated', 'public.opportunity_skills', 'select'),
  false,
  'authenticated clients cannot read associations directly'
);
select is(
  has_table_privilege('authenticated', 'public.opportunity_skills', 'insert'),
  false,
  'authenticated clients cannot bypass the backend to create associations'
);

insert into auth.users (
  id, aud, role, email, encrypted_password, raw_app_meta_data,
  raw_user_meta_data, created_at, updated_at
)
values (
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  'authenticated',
  'authenticated',
  'opportunity-skills-owner@example.test',
  '$2a$10$abcdefghijklmnopqrstuu',
  '{"provider":"email","providers":["email"]}',
  '{"display_name":"Opportunity Skills Owner"}',
  timezone('utc', now()),
  timezone('utc', now())
);

insert into public.project_members (
  project_id, profile_id, role, membership_status, created_by_profile_id, joined_at
)
values (
  '11111111-1111-1111-1111-111111111111',
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  'OWNER',
  'ACTIVE',
  'dddddddd-dddd-dddd-dddd-dddddddddddd',
  timezone('utc', now())
);

insert into public.project_opportunities (
  project_id, opportunity_type, title, description, status, created_by_profile_id
)
values (
  '11111111-1111-1111-1111-111111111111',
  'TALENT',
  'PUL-22 skill association',
  'A draft opportunity for the association constraints.',
  'DRAFT',
  'dddddddd-dddd-dddd-dddd-dddddddddddd'
);

insert into public.opportunity_skills (opportunity_id, skill_id, importance)
select opportunity.id, skill.id, 'REQUIRED'
from public.project_opportunities opportunity
cross join public.skills skill
where opportunity.title = 'PUL-22 skill association'
  and skill.slug = 'desarrollo-web';

select is(
  (select count(*) from public.opportunity_skills association
   join public.project_opportunities opportunity on opportunity.id = association.opportunity_id
   where opportunity.title = 'PUL-22 skill association'
     and association.importance = 'REQUIRED'),
  1::bigint,
  'required skills can be associated'
);
insert into public.opportunity_skills (opportunity_id, skill_id, importance)
select opportunity.id, skill.id, 'PREFERRED'
from public.project_opportunities opportunity
cross join public.skills skill
where opportunity.title = 'PUL-22 skill association'
  and skill.slug = 'desarrollo-movil';
select is(
  (select count(*) from public.opportunity_skills association
   join public.project_opportunities opportunity on opportunity.id = association.opportunity_id
   where opportunity.title = 'PUL-22 skill association'
     and association.importance = 'PREFERRED'),
  1::bigint,
  'preferred skills can be associated'
);
select throws_ok(
  $$insert into public.opportunity_skills (opportunity_id, skill_id, importance)
    select opportunity.id, skill.id, 'PREFERRED'
    from public.project_opportunities opportunity
    cross join public.skills skill
    where opportunity.title = 'PUL-22 skill association'
      and skill.slug = 'desarrollo-web'$$,
  '23505',
  'duplicate key value violates unique constraint "opportunity_skills_pkey"',
  'duplicate opportunity/skill pairs are rejected'
);
select throws_ok(
  $$insert into public.opportunity_skills (opportunity_id, skill_id, importance)
    select opportunity.id, skill.id, 'OPTIONAL'
    from public.project_opportunities opportunity
    cross join public.skills skill
    where opportunity.title = 'PUL-22 skill association'
      and skill.slug = 'analisis-de-datos'$$,
  '23514',
  'new row for relation "opportunity_skills" violates check constraint "opportunity_skills_importance"',
  'importance accepts only REQUIRED or PREFERRED'
);

delete from public.project_opportunities
where title = 'PUL-22 skill association';
select is(
  (select count(*) from public.opportunity_skills),
  0::bigint,
  'deleting an opportunity removes only its associations'
);

select * from finish();
rollback;
