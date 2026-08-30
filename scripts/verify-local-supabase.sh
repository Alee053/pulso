#!/usr/bin/env sh

set -eu

db_container=supabase_db_pulso
auth_health_url=http://127.0.0.1:54321/auth/v1/health

printf '%s\n' 'Verificando los servicios locales de Supabase...'
pnpm db:status >/dev/null

printf '%s\n' 'Verificando el esquema y las pruebas pgTAP...'
pnpm db:lint
pnpm db:test

seed_count=$(docker exec "$db_container" psql \
  -U postgres \
  -d postgres \
  -Atc "select count(*) from public.startups where status = 'PUBLISHED';")

if [ "$seed_count" -ne 2 ]; then
  printf 'Se esperaban 2 proyectos publicados y se encontraron %s.\n' "$seed_count" >&2
  exit 1
fi

auth_status=$(curl -sS -o /dev/null -w '%{http_code}' "$auth_health_url")

if [ "$auth_status" != '200' ]; then
  printf 'La salud de Auth local devolvio HTTP %s.\n' "$auth_status" >&2
  exit 1
fi

printf '%s\n' 'Supabase local validado: servicios, migraciones, seed y Auth están disponibles.'
