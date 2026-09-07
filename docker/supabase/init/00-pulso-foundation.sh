#!/usr/bin/env bash

set -Eeuo pipefail

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=pgpass="$POSTGRES_PASSWORD" \
  --set=jwt_secret="${JWT_SECRET:-}" \
  --set=jwt_exp="${JWT_EXP:-3600}" <<'EOSQL'
do $$
begin
  if not exists (select from pg_roles where rolname = 'supabase_admin') then
    create role supabase_admin superuser createdb createrole login;
  end if;
end
$$;

alter role supabase_admin password :'pgpass';

create extension if not exists pgcrypto;
create extension if not exists "uuid-ossp";

create schema if not exists extensions;
create schema if not exists graphql_public;
create schema if not exists auth;
create schema if not exists storage;
create schema if not exists _realtime;

do $$
begin
  if not exists (select from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
  if not exists (select from pg_roles where rolname = 'service_role') then
    create role service_role nologin bypassrls;
  end if;
  if not exists (select from pg_roles where rolname = 'authenticator') then
    create role authenticator noinherit login;
  end if;
  if not exists (select from pg_roles where rolname = 'supabase_auth_admin') then
    create role supabase_auth_admin noinherit createrole login;
  end if;
  if not exists (select from pg_roles where rolname = 'supabase_storage_admin') then
    create role supabase_storage_admin noinherit createrole login;
  end if;
end
$$;

alter role authenticator password :'pgpass';
alter role supabase_auth_admin password :'pgpass';
alter role supabase_storage_admin password :'pgpass';
grant anon, authenticated, service_role to authenticator;

alter schema auth owner to supabase_auth_admin;
alter schema storage owner to supabase_storage_admin;
alter schema _realtime owner to postgres;
grant usage on schema public, extensions, graphql_public, auth to anon, authenticated, service_role;

alter database postgres set "app.settings.jwt_secret" to :'jwt_secret';
alter database postgres set "app.settings.jwt_exp" to :'jwt_exp';
EOSQL
