begin;

-- Complete the catalog invariants without rewriting the migrations that first
-- introduced sectors, tags, and skills.
-- The ordered history creates sectors from distinct source values, and the
-- catalog tables have no direct write grants. Do not silently merge or rename
-- any out-of-band duplicate names during this forward migration.
do $$
begin
  if exists (
    select 1
    from (
      select name from public.sectors group by name having count(*) > 1
      union all
      select name from public.tags group by name having count(*) > 1
      union all
      select name from public.skills group by name having count(*) > 1
    ) as duplicate_names
  ) then
    raise exception using
      errcode = 'check_violation',
      message = 'duplicate catalog names must be resolved before applying catalog uniqueness';
  end if;
end;
$$;

alter table public.sectors
  add constraint sectors_name_key unique (name);

alter table public.tags
  add constraint tags_name_key unique (name);

alter table public.skills
  add constraint skills_name_key unique (name);

-- Keep direct Supabase access read-only. The Spring API remains the product
-- data path, while authenticated clients can read only active rows through
-- the existing RLS policies.
revoke all privileges
  on table public.sectors, public.tags, public.skills
  from anon, authenticated;

grant select
  on table public.sectors, public.tags, public.skills
  to authenticated;

commit;
