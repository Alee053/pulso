begin;

-- Complete the product profile without changing the external Auth identity
-- boundary. Existing rows receive product defaults and keep their timestamps.
alter table public.profiles
  add column profile_kind text not null default 'PERSON',
  add column avatar_storage_path text,
  add column short_bio text,
  add column department text,
  add column profile_visibility text not null default 'PUBLIC',
  add column verification_status text not null default 'UNVERIFIED',
  add column account_status text not null default 'ACTIVE',
  add column platform_role text not null default 'USER',
  add column onboarding_completed_at timestamptz,
  add column deleted_at timestamptz;

update public.profiles
set display_name = 'Usuario'
where display_name is null;

alter table public.profiles
  alter column display_name set not null;

alter table public.profiles
  drop constraint profiles_display_name_length,
  add constraint profiles_display_name_length check (
    char_length(trim(display_name)) between 1 and 120
  ),
  add constraint profiles_profile_kind check (
    profile_kind in ('PERSON', 'ORGANIZATION')
  ),
  add constraint profiles_short_bio_length check (
    short_bio is null or char_length(trim(short_bio)) between 1 and 500
  ),
  add constraint profiles_department_length check (
    department is null or char_length(trim(department)) between 1 and 120
  ),
  add constraint profiles_visibility check (
    profile_visibility in ('PUBLIC', 'MEMBERS_ONLY', 'PRIVATE')
  ),
  add constraint profiles_verification_status check (
    verification_status in ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REVOKED')
  ),
  add constraint profiles_account_status check (
    account_status in ('ACTIVE', 'SUSPENDED', 'DELETED')
  ),
  add constraint profiles_platform_role check (
    platform_role in ('USER', 'MODERATOR', 'ADMIN')
  );

create index profiles_account_status_created_at_idx
  on public.profiles (account_status, created_at);

create index profiles_city_department_idx
  on public.profiles (city, department);

create index profiles_profile_kind_idx
  on public.profiles (profile_kind);

comment on table public.profiles is
  'Product profile keyed by the UUID owned by Supabase Auth; credentials and sessions remain in auth.';
comment on column public.profiles.id is
  'Immutable UUID from auth.users; product data must not create a second identity.';
comment on column public.profiles.short_bio is
  'Optional short product biography, limited to 500 characters.';
comment on column public.profiles.department is
  'Optional Bolivian department or equivalent geographic subdivision.';

-- Direct Supabase access is defense in depth. Users can read their own row and
-- edit profile content, but cannot change identity, timestamps, status, role,
-- verification, or deletion state from the client role.
revoke all privileges on table public.profiles from anon, authenticated;
grant select on table public.profiles to authenticated;
grant insert (
  id,
  profile_kind,
  display_name,
  avatar_storage_path,
  short_bio,
  city,
  department,
  profile_visibility,
  onboarding_completed_at
) on table public.profiles to authenticated;
grant update (
  profile_kind,
  display_name,
  avatar_storage_path,
  short_bio,
  city,
  department,
  profile_visibility,
  onboarding_completed_at
) on table public.profiles to authenticated;

create or replace function public.prevent_profile_identity_change()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.id is distinct from old.id then
    raise exception 'profiles.id is immutable';
  end if;
  return new;
end;
$$;

create trigger profiles_identity_immutable
before update on public.profiles
for each row execute function public.prevent_profile_identity_change();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    left(coalesce(nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''), 'Usuario'), 120)
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

commit;
