begin;

create table public.feature_flags (
  flag_key text primary key
    check (flag_key ~ '^[a-z][a-z0-9_]{0,63}$'),
  enabled boolean not null default true
);

alter table public.feature_flags enable row level security;
revoke all privileges on table public.feature_flags from anon, authenticated;

insert into public.feature_flags (flag_key, enabled)
values
  ('language_switcher', true),
  ('project_creation', true);

comment on table public.feature_flags is
  'Boolean availability controls are exposed through the Pulso API only.';
comment on column public.feature_flags.flag_key is
  'Stable public feature identifier; responses are allowlisted in the backend.';

commit;
