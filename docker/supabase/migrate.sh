#!/usr/bin/env sh
# This script runs inside Linux containers; keep its line endings as LF.

set -eu

psql_args="--no-psqlrc --set=ON_ERROR_STOP=1"

until pg_isready --host "$PGHOST" --port "$PGPORT" --username "$PGUSER" --dbname "$PGDATABASE" >/dev/null 2>&1; do
  sleep 1
done

psql $psql_args <<'EOSQL'
create schema if not exists supabase_migrations;

create table if not exists supabase_migrations.schema_migrations (
  version text primary key,
  statements text[],
  name text
);
EOSQL

for migration in /runner/migrations/*.sql; do
  [ -f "$migration" ] || continue

  filename=$(basename "$migration")
  version=${filename%%_*}
  migration_name=${filename#*_}
  migration_name=${migration_name%.sql}
  applied=$(psql $psql_args --tuples-only --no-align \
    --command="select 1 from supabase_migrations.schema_migrations where version = '$version' limit 1" \
    | tr -d '[:space:]')

  if [ "$applied" = "1" ]; then
    continue
  fi

  printf '%s\n' "Aplicando migración $filename..."
  psql $psql_args --file "$migration"
  psql $psql_args \
    --command="insert into supabase_migrations.schema_migrations (version, statements, name) values ('$version', array['compose'], '$migration_name')"
done

printf '%s\n' 'Aplicando seed local...'
psql $psql_args --file /runner/seed.sql
printf '%s\n' 'Migraciones y seed local listos.'
