#!/usr/bin/env sh

set -eu

available_port=${PULSO_VERIFY_BACKEND_PORT:-18080}
unavailable_backend_port=$((available_port + 1))
unavailable_db_port=${PULSO_VERIFY_UNAVAILABLE_DB_PORT:-55432}
backend_env_file=${PULSO_VERIFY_BACKEND_ENV_FILE:-}
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

command -v curl >/dev/null 2>&1 || fail 'Se necesita curl para verificar el backend local.'
command -v node >/dev/null 2>&1 || fail 'Se necesita Node.js para comprobar los puertos de verificación.'
if command -v setsid >/dev/null 2>&1; then
  process_launcher=setsid
fi

if [ ! -f .env ] && [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  fail 'Crea .env desde .env.example o define SPRING_DATASOURCE_PASSWORD antes de verificar el backend.'
fi

if [ -z "$backend_env_file" ]; then
  if [ -f .env ]; then
    backend_env_file=.env
  else
    backend_env_file=/dev/null
  fi
fi

assert_port_free() {
  candidate_port=$1
  if ! node scripts/check-port-free.mjs "$candidate_port"; then
    fail "El puerto $candidate_port ya está ocupado; define otra variable PULSO_VERIFY_* para continuar."
  fi
}

request_code() {
  request_url=$1
  response_file=$2
  curl --silent --show-error --max-time 5 \
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
    sleep 1
  done

  fail "Se esperaba HTTP $expected_code en $request_url y no se obtuvo dentro del tiempo límite."
}

assert_body_contains() {
  expected_fragment=$1
  response_file=$2
  grep -F -- "$expected_fragment" "$response_file" >/dev/null 2>&1 \
    || fail "La respuesta no contiene el fragmento esperado: $expected_fragment"
}

start_available_backend() {
  PULSO_BACKEND_ENV_FILE="$backend_env_file" \
    SERVER_PORT="$available_port" $process_launcher ./scripts/run-backend.sh \
    >"$temporary_dir/backend-available.log" 2>&1 &
  backend_pid=$!
}

start_unavailable_backend() {
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$unavailable_db_port/postgres" \
    SERVER_PORT="$unavailable_backend_port" \
    PULSO_BACKEND_ENV_FILE="$backend_env_file" \
    $process_launcher ./scripts/run-backend.sh \
    >"$temporary_dir/backend-unavailable.log" 2>&1 &
  backend_pid=$!
}

printf '%s\n' 'Verificando el backend con Supabase local disponible...'
pnpm db:status >/dev/null 2>&1
assert_port_free "$available_port"
assert_port_free "$unavailable_backend_port"
assert_port_free "$unavailable_db_port"
start_available_backend

available_base_url="http://127.0.0.1:$available_port"
available_response="$temporary_dir/available-response"
wait_for_code "$available_base_url/api/health" 200 "$available_response"
assert_body_contains '"status":"UP"' "$available_response"
wait_for_code "$available_base_url/actuator/health" 200 "$available_response"
assert_body_contains '"status":"UP"' "$available_response"
wait_for_code "$available_base_url/actuator/health/liveness" 200 "$available_response"
assert_body_contains '"status":"UP"' "$available_response"
wait_for_code "$available_base_url/actuator/health/readiness" 200 "$available_response"
assert_body_contains '"status":"UP"' "$available_response"
wait_for_code "$available_base_url/api/v1/startups?offset=0&limit=2" 200 "$available_response"
assert_body_contains 'AndeSolar' "$available_response"
assert_body_contains 'EcoPack Bolivia' "$available_response"
cleanup_backend

printf '%s\n' 'Verificando la separación entre liveness y dependencia caída...'
start_unavailable_backend

unavailable_base_url="http://127.0.0.1:$unavailable_backend_port"
unavailable_response="$temporary_dir/unavailable-response"
wait_for_code "$unavailable_base_url/api/health" 200 "$unavailable_response"
assert_body_contains '"status":"UP"' "$unavailable_response"
wait_for_code "$unavailable_base_url/actuator/health/liveness" 200 "$unavailable_response"
assert_body_contains '"status":"UP"' "$unavailable_response"
wait_for_code "$unavailable_base_url/actuator/health" 503 "$unavailable_response"
assert_body_contains '"status":"DOWN"' "$unavailable_response"
wait_for_code "$unavailable_base_url/actuator/health/readiness" 503 "$unavailable_response"
assert_body_contains '"status":"DOWN"' "$unavailable_response"

printf '%s\n' 'Backend local validado: conexión JDBC, catálogo seed, liveness y readiness están disponibles.'
