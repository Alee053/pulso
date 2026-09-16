#!/usr/bin/env sh

set -eu

frontend_port=${PULSO_SMOKE_FRONTEND_PORT:-4200}
backend_port=${PULSO_SMOKE_BACKEND_PORT:-8080}
cdp_port=${PULSO_SMOKE_CDP_PORT:-9222}
temporary_dir=$(mktemp -d)
backend_pid=
frontend_pid=
chrome_pid=
python_command=
browser_command=${PULSO_SMOKE_BROWSER:-}
process_launcher=

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

cleanup_process() {
  process_pid=$1
  if [ -n "$process_pid" ] && kill -0 "$process_pid" 2>/dev/null; then
    kill -TERM -- "-$process_pid" 2>/dev/null || kill -TERM "$process_pid" 2>/dev/null || true
    attempt=0
    while kill -0 "$process_pid" 2>/dev/null && [ "$attempt" -lt 20 ]; do
      attempt=$((attempt + 1))
      sleep 0.1
    done
    if kill -0 "$process_pid" 2>/dev/null; then
      kill -KILL -- "-$process_pid" 2>/dev/null || kill -KILL "$process_pid" 2>/dev/null || true
    fi
    wait "$process_pid" 2>/dev/null || true
  fi
}

cleanup() {
  cleanup_process "$chrome_pid"
  cleanup_process "$frontend_pid"
  cleanup_process "$backend_pid"
  rm -rf "$temporary_dir"
}

trap cleanup EXIT INT TERM

read_status_value() {
  variable_name=$1
  value=$(sed -n "s/^${variable_name}=//p" "$status_file" | tail -n 1)
  case "$value" in
    \"*\")
      value=${value#\"}
      value=${value%\"}
      ;;
  esac
  printf '%s' "$value"
}

command -v curl >/dev/null 2>&1 || fail 'Se necesita curl para ejecutar el smoke test.'
command -v docker >/dev/null 2>&1 || fail 'Se necesita Docker para reiniciar el gateway de Supabase local.'
command -v node >/dev/null 2>&1 || fail 'Se necesita Node.js para automatizar el navegador.'
command -v pnpm >/dev/null 2>&1 || fail 'Se necesita pnpm para instalar y verificar el monorepo.'

for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 \
    && "$candidate" --version >/dev/null 2>&1; then
    python_command=$candidate
    break
  fi
done
[ -n "$python_command" ] || fail 'Se necesita Python 3 para servir el artefacto frontend temporal.'

if command -v setsid >/dev/null 2>&1; then
  process_launcher=setsid
fi

if [ -z "$browser_command" ]; then
  for candidate in google-chrome google-chrome-stable chrome chromium chromium-browser; do
    if command -v "$candidate" >/dev/null 2>&1; then
      browser_command=$(command -v "$candidate")
      break
    fi
  done
fi

if [ -z "$browser_command" ]; then
  for candidate in \
    '/c/Program Files/Google/Chrome/Application/chrome.exe' \
    '/c/Program Files (x86)/Google/Chrome/Application/chrome.exe' \
    "/c/Users/${USERNAME:-}/AppData/Local/Google/Chrome/Application/chrome.exe"; do
    if [ -x "$candidate" ]; then
      browser_command=$candidate
      break
    fi
  done
fi

[ -n "$browser_command" ] \
  || fail 'Se necesita Google Chrome (o define PULSO_SMOKE_BROWSER) para verificar el flujo desde el navegador.'

assert_port_free() {
  candidate_port=$1
  if ! node scripts/check-port-free.mjs "$candidate_port"; then
    fail "El puerto $candidate_port ya está ocupado; define PULSO_SMOKE_*_PORT o detén el proceso antes de continuar."
  fi
}

request_code() {
  request_url=$1
  response_file=$2
  curl --silent --show-error --max-time 10 \
    --output "$response_file" \
    --write-out '%{http_code}' \
    "$request_url" 2>/dev/null || true
}

wait_for_code() {
  request_url=$1
  expected_code=$2
  response_file=$3
  attempt=0

  while [ "$attempt" -lt 45 ]; do
    attempt=$((attempt + 1))
    response_code=$(request_code "$request_url" "$response_file")
    if [ "$response_code" = "$expected_code" ]; then
      return 0
    fi
    if [ -n "${backend_pid:-}" ] && ! kill -0 "$backend_pid" 2>/dev/null; then
      fail "El backend terminó antes de responder en $request_url."
    fi
    if [ -n "${frontend_pid:-}" ] && ! kill -0 "$frontend_pid" 2>/dev/null; then
      fail 'El servidor frontend terminó antes de responder.'
    fi
    sleep 1
  done

  fail "Se esperaba HTTP $expected_code en $request_url y no se obtuvo dentro del tiempo límite."
}

assert_body_contains() {
  expected_fragment=$1
  response_file=$2
  description=$3
  grep -F -- "$expected_fragment" "$response_file" >/dev/null 2>&1 \
    || fail "$description: la respuesta no contiene el valor esperado."
}

assert_port_free "$frontend_port"
assert_port_free "$backend_port"
assert_port_free "$cdp_port"

printf '%s\n' 'Instalando dependencias con el lockfile del checkout...'
pnpm install --frozen-lockfile

printf '%s\n' 'Levantando Supabase local y recreando migraciones/seed...'
if ! pnpm db:up >"$temporary_dir/db-up.log" 2>&1; then
  cat "$temporary_dir/db-up.log" >&2
  fail 'No se pudo levantar Supabase local; revisa el log anterior.'
fi
pnpm db:reset
# db reset recrea Auth y puede dejar al Kong ya iniciado apuntando a su IP anterior.
docker restart supabase_kong_pulso >/dev/null
auth_attempt=0
auth_ready=0
while [ "$auth_attempt" -lt 30 ]; do
  auth_attempt=$((auth_attempt + 1))
  auth_code=$(curl --silent --show-error --max-time 2 \
    --output /dev/null \
    --write-out '%{http_code}' \
    http://127.0.0.1:54321/auth/v1/health 2>/dev/null || true)
  if [ "$auth_code" = '200' ]; then
    auth_ready=1
    break
  fi
  sleep 1
done
[ "$auth_ready" -eq 1 ] || fail 'Auth local no respondió HTTP 200 después de recrear el stack.'
pnpm db:lint
pnpm db:test
pnpm db:verify

status_file="$temporary_dir/supabase-status.env"
pnpm exec supabase --workdir database status -o env >"$status_file" 2>/dev/null \
  || fail 'No se pudo leer la configuración pública de Supabase local.'
auth_url=$(read_status_value API_URL)
publishable_key=$(read_status_value PUBLISHABLE_KEY)
if [ -z "$publishable_key" ]; then
  publishable_key=$(read_status_value ANON_KEY)
fi
[ -n "$auth_url" ] || fail 'Supabase status no devolvió API_URL.'
[ -n "$publishable_key" ] || fail 'Supabase status no devolvió una clave pública para Auth.'

printf '%s\n' 'Ejecutando pruebas frontend, backend y Auth contra el stack local...'
pnpm --filter @pulso/frontend test
SPRING_DATASOURCE_PASSWORD=postgres pnpm backend:test
SPRING_DATASOURCE_PASSWORD=postgres pnpm backend:verify
pnpm auth:verify

printf '%s\n' 'Construyendo el frontend con API y Auth públicos locales...'
PULSO_API_BASE_URL="http://127.0.0.1:$backend_port" \
PULSO_SUPABASE_URL="$auth_url" \
PULSO_SUPABASE_PUBLISHABLE_KEY="$publishable_key" \
pnpm build

printf '%s\n' 'Iniciando backend y artefacto frontend para el recorrido de navegador...'
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:54322/postgres' \
SPRING_DATASOURCE_USERNAME='postgres' \
SPRING_DATASOURCE_PASSWORD='postgres' \
SUPABASE_JWT_ISSUER='http://127.0.0.1:54321/auth/v1' \
SUPABASE_JWT_AUDIENCE='authenticated' \
SUPABASE_JWT_JWK_SET_URI='http://127.0.0.1:54321/auth/v1/.well-known/jwks.json' \
SUPABASE_JWT_SECRET='' \
CORS_ALLOWED_ORIGINS="http://localhost:$frontend_port,http://127.0.0.1:$frontend_port" \
SERVER_PORT="$backend_port" \
$process_launcher ./scripts/run-backend.sh >"$temporary_dir/backend.log" 2>&1 &
backend_pid=$!
backend_base_url="http://localhost:$backend_port"
wait_for_code "$backend_base_url/api/health" '200' "$temporary_dir/backend-health.json"
wait_for_code "$backend_base_url/actuator/health/readiness" '200' "$temporary_dir/backend-readiness.json"
wait_for_code "$backend_base_url/api/v1/startups?offset=0&limit=2" '200' "$temporary_dir/startups.json"
assert_body_contains 'EcoPack Bolivia' "$temporary_dir/startups.json" 'Catálogo seed'
assert_body_contains 'AndeSolar' "$temporary_dir/startups.json" 'Catálogo seed'

$process_launcher "$python_command" -m http.server "$frontend_port" --bind 127.0.0.1 \
  --directory frontend/dist/pulso-frontend/browser >"$temporary_dir/frontend.log" 2>&1 &
frontend_pid=$!
frontend_base_url="http://127.0.0.1:$frontend_port"
wait_for_code "$frontend_base_url" '200' "$temporary_dir/frontend.html"

$process_launcher "$browser_command" \
  --headless=new \
  --no-sandbox \
  --disable-gpu \
  --disable-dev-shm-usage \
  --no-first-run \
  --user-data-dir="$temporary_dir/chrome-profile" \
  --remote-debugging-address=127.0.0.1 \
  --remote-debugging-port="$cdp_port" \
  --remote-allow-origins='*' \
  about:blank >"$temporary_dir/chrome.log" 2>&1 &
chrome_pid=$!

attempt=0
cdp_ready=0
while [ "$attempt" -lt 30 ]; do
  attempt=$((attempt + 1))
  if curl --silent --max-time 2 "http://127.0.0.1:$cdp_port/json" >/dev/null 2>&1; then
    cdp_ready=1
    break
  fi
  if ! kill -0 "$chrome_pid" 2>/dev/null; then
    fail 'Chrome terminó antes de abrir el puerto de depuración.'
  fi
  sleep 1
done
[ "$cdp_ready" -eq 1 ] || fail 'Chrome no abrió el endpoint de depuración dentro del tiempo límite.'

PULSO_SMOKE_FRONTEND_URL="$frontend_base_url" \
PULSO_SMOKE_BACKEND_URL="$backend_base_url" \
PULSO_SMOKE_CDP_PORT="$cdp_port" \
node scripts/verify-browser-smoke.mjs

printf '%s\n' 'Smoke completo validado. Supabase local permanece encendido; backend, frontend y Chrome temporales serán detenidos.'
