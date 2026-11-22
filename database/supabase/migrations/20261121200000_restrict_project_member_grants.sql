begin;

-- Membership reads and writes stay behind the Spring API. The local Supabase
-- image grants public table privileges by default, and the original
-- startup_members table never revoked them. RLS already filtered rows, but
-- anon and authenticated kept INSERT, UPDATE, DELETE, and TRUNCATE grants.
-- Keep only the authenticated SELECT grant used with the own-membership policy.
revoke all privileges on table public.project_members from anon, authenticated;
grant select on table public.project_members to authenticated;

commit;
