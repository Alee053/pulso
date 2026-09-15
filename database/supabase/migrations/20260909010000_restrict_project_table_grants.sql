begin;

-- Project reads stay behind the Spring API. RLS remains defense in depth, but
-- the local Supabase image grants public table privileges by default.
revoke all privileges on table public.projects from anon, authenticated;

commit;
