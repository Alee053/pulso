#!/usr/bin/env sh

set -eu

backend_port=${PULSO_VERIFY_AUTH_PORT:-18082}
temporary_dir=$(mktemp -d)
backend_pid=
process_launcher=

fail() {
  printf '%s\n' "$1" >&2
  exit 1
}

cleanup_backend() {
  if [ -n "${backend_pid:-}" ]; then
    if kill -0 "$backend_pid" 2>/dev/null; then
      kill -TERM -- "-$backend_pid" 2>/dev/null || kill -TERM "$backend_pid" 2>/dev/null || true
    fi
    wait "$backend_pid" 2>/dev/null || true
    backend_pid=
  fi
}

cleanup() {
  cleanup_backend
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

command -v curl >/dev/null 2>&1 || fail 'Se necesita curl para verificar Supabase Auth local.'
command -v node >/dev/null 2>&1 || fail 'Se necesita Node.js para validar las respuestas JSON de Auth.'
if command -v setsid >/dev/null 2>&1; then
  process_launcher=setsid
fi

status_file="$temporary_dir/supabase-status.env"
if ! pnpm exec supabase --workdir database status -o env >"$status_file" 2>/dev/null; then
  fail 'Supabase local no está disponible; ejecuta pnpm db:up antes de verificar Auth.'
fi

auth_url=$(read_status_value API_URL)
publishable_key=$(read_status_value PUBLISHABLE_KEY)
if [ -z "$publishable_key" ]; then
  publishable_key=$(read_status_value ANON_KEY)
fi
service_role_key=$(read_status_value SERVICE_ROLE_KEY)

[ -n "$auth_url" ] || fail 'Supabase status no devolvió API_URL.'
[ -n "$publishable_key" ] || fail 'Supabase status no devolvió una clave pública para Auth.'
[ -n "$service_role_key" ] || fail 'Supabase status no devolvió la clave service_role local para la prueba de rechazo.'

assert_port_free() {
  candidate_port=$1
  if ! node scripts/check-port-free.mjs "$candidate_port"; then
    fail "El puerto $candidate_port ya está ocupado; define PULSO_VERIFY_AUTH_PORT para continuar."
  fi
}

request_code() {
  request_url=$1
  response_file=$2
  shift 2
  curl --silent --show-error --max-time 10 \
    --output "$response_file" \
    --write-out '%{http_code}' \
    "$@" \
    "$request_url" 2>/dev/null || true
}

assert_code() {
  actual_code=$1
  expected_code=$2
  description=$3
  [ "$actual_code" = "$expected_code" ] \
    || fail "$description: se esperaba HTTP $expected_code y se obtuvo HTTP $actual_code."
}

assert_body_contains() {
  expected_fragment=$1
  response_file=$2
  description=$3
  grep -F -- "$expected_fragment" "$response_file" >/dev/null 2>&1 \
    || fail "$description: la respuesta no contiene el valor esperado."
}

read_json_string() {
  json_file=$1
  json_property=$2
  node -e '
    const fs = require("node:fs")
    const value = JSON.parse(fs.readFileSync(process.argv[1], "utf8"))[process.argv[2]]
    if (typeof value !== "string" || value.length === 0) process.exit(1)
    process.stdout.write(value)
  ' "$json_file" "$json_property"
}

wait_for_backend() {
  response_file="$temporary_dir/backend-health.json"
  attempt=0

  while [ "$attempt" -lt 45 ]; do
    attempt=$((attempt + 1))
    response_code=$(request_code "$backend_base_url/api/health" "$response_file")
    if [ "$response_code" = '200' ]; then
      return 0
    fi
    if ! kill -0 "$backend_pid" 2>/dev/null; then
      fail 'El backend terminó antes de quedar disponible para verificar Auth.'
    fi
    sleep 1
  done

  fail 'El backend no respondió en el tiempo límite de la verificación de Auth.'
}

assert_port_free "$backend_port"

test_email="pulso-auth-$(date +%s%N)@example.test"
test_password='PulsoLocal-Auth-123456'
signup_body="$temporary_dir/signup.json"
login_body="$temporary_dir/login.json"
signup_response="$temporary_dir/signup-response.json"
login_response="$temporary_dir/login-response.json"
me_response="$temporary_dir/me-response.json"
unauthenticated_response="$temporary_dir/unauthenticated-response.json"
invalid_token_response="$temporary_dir/invalid-token-response.json"
logout_response="$temporary_dir/logout-response.json"

printf '%s' "{\"email\":\"$test_email\",\"password\":\"$test_password\"}" >"$signup_body"

printf '%s\n' 'Registrando usuario de prueba en Supabase Auth local...'
http_code=$(request_code "$auth_url/auth/v1/signup" "$signup_response" \
  --header "apikey: $publishable_key" \
  --header 'Content-Type: application/json' \
  --data-binary "@$signup_body" \
  --request POST)
assert_code "$http_code" '200' 'Registro local'
assert_body_contains '"id"' "$signup_response" 'Registro local'

printf '%s' "{\"email\":\"$test_email\",\"password\":\"$test_password\"}" >"$login_body"

printf '%s\n' 'Iniciando sesión y obteniendo el token de acceso...'
http_code=$(request_code "$auth_url/auth/v1/token?grant_type=password" "$login_response" \
  --header "apikey: $publishable_key" \
  --header 'Content-Type: application/json' \
  --data-binary "@$login_body" \
  --request POST)
assert_code "$http_code" '200' 'Inicio de sesión local'
access_token=$(read_json_string "$login_response" access_token) \
  || fail 'Auth local no devolvió un access token.'

backend_base_url="http://127.0.0.1:$backend_port"
printf '%s\n' 'Iniciando backend temporal con la validación JWKS local...'
SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:54322/postgres' \
SPRING_DATASOURCE_USERNAME='postgres' \
SPRING_DATASOURCE_PASSWORD='postgres' \
SUPABASE_JWT_ISSUER='http://127.0.0.1:54321/auth/v1' \
SUPABASE_JWT_AUDIENCE='authenticated' \
SUPABASE_JWT_JWK_SET_URI='http://127.0.0.1:54321/auth/v1/.well-known/jwks.json' \
SUPABASE_JWT_SECRET='' \
SERVER_PORT="$backend_port" \
$process_launcher ./scripts/run-backend.sh >"$temporary_dir/backend.log" 2>&1 &
backend_pid=$!
wait_for_backend

printf '%s\n' 'Comprobando claims autenticados en GET /api/v1/me...'
http_code=$(request_code "$backend_base_url/api/v1/me" "$me_response" \
  --header "Authorization: Bearer $access_token")
assert_code "$http_code" '200' 'GET /api/v1/me autenticado'
assert_body_contains '"role":"authenticated"' "$me_response" 'Rol autenticado'
assert_body_contains "\"email\":\"$test_email\"" "$me_response" 'Correo autenticado'

printf '%s\n' 'Comprobando rechazos 401 sin token y con token inválido...'
http_code=$(request_code "$backend_base_url/api/v1/me" "$unauthenticated_response")
assert_code "$http_code" '401' 'GET /api/v1/me sin token'
http_code=$(request_code "$backend_base_url/api/v1/me" "$invalid_token_response" \
  --header 'Authorization: Bearer invalid.token.value')
assert_code "$http_code" '401' 'GET /api/v1/me con token inválido'
http_code=$(request_code "$backend_base_url/api/v1/me" "$invalid_token_response" \
  --header "Authorization: Bearer $service_role_key")
assert_code "$http_code" '401' 'GET /api/v1/me con token service_role'

printf '%s\n' 'Cerrando sesión en Supabase Auth local...'
http_code=$(request_code "$auth_url/auth/v1/logout" "$logout_response" \
  --header "apikey: $publishable_key" \
  --header "Authorization: Bearer $access_token" \
  --request POST)
assert_code "$http_code" '204' 'Cierre de sesión local'

printf '%s\n' 'Auth local validado: registro, inicio de sesión, claims del backend, 401 y cierre de sesión.'
