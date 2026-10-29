begin;

select plan(10);

-- These checks describe the opportunity fixtures in seed.sql (UUID prefix
-- 40000000 for opportunities, 50000000 for their creator). The pgTAP runner does
-- not mount seed.sql, so they run against a database where the seed was applied.

select is(
  (select count(*) from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'),
  25::bigint,
  'the seed provides 25 demo opportunities'
);

select is(
  (select count(distinct opportunity_type) from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'
     and status = 'OPEN'),
  9::bigint,
  'open demo opportunities cover every opportunity type'
);

select is(
  (select count(distinct status) from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'),
  5::bigint,
  'demo opportunities cover OPEN, DRAFT, PAUSED, CLOSED, and FILLED'
);

select is(
  (select count(*) from public.project_opportunities opportunity
   join public.projects project on project.id = opportunity.project_id
   where opportunity.id::text like '40000000-0000-4000-8000-0000000000%'
     and opportunity.status = 'OPEN'
     and project.status <> 'PUBLISHED'),
  0::bigint,
  'open demo opportunities belong to published projects'
);

select ok(
  (select bool_or(remote_allowed) and bool_or(not remote_allowed)
   from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'
     and status = 'OPEN'),
  'open demo opportunities include remote and in-person ones'
);

select is(
  (select count(*) from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'
     and not remote_allowed
     and city is null
     and department is null),
  0::bigint,
  'in-person demo opportunities state a city or a department'
);

select ok(
  (select bool_or(importance = 'REQUIRED') and bool_or(importance = 'PREFERRED')
   from public.opportunity_skills
   where opportunity_id::text like '40000000-0000-4000-8000-0000000000%'),
  'demo opportunities use required and preferred skills'
);

select is(
  (select count(*) from public.opportunity_skills association
   join public.skills skill on skill.id = association.skill_id
   where association.opportunity_id::text like '40000000-0000-4000-8000-0000000000%'
     and not skill.is_active),
  0::bigint,
  'demo opportunities only reference active skills'
);

select is(
  (select count(*) from public.project_opportunities
   where id::text like '40000000-0000-4000-8000-0000000000%'
     and ((status = 'CLOSED') <> (closed_at is not null))),
  0::bigint,
  'only the closed demo opportunity has a closing date'
);

select ok(
  (select profile.id is not null
          and auth_user.encrypted_password = ''
          and not exists (
            select 1 from public.project_members member
            where member.profile_id = profile.id)
   from public.profiles profile
   join auth.users auth_user on auth_user.id = profile.id
   where profile.id = '50000000-0000-4000-8000-000000000001'),
  'the demo creator profile cannot sign in and belongs to no project'
);

select * from finish();
rollback;
