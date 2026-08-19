#!/usr/bin/env sh

set -eu

set_env_if_unset() {
  key=$1
  value=$2

  case "$key" in
    POSTGRES_DB)
      if [ -z "${POSTGRES_DB+x}" ]; then export "POSTGRES_DB=$value"; fi
      ;;
    POSTGRES_USER)
      if [ -z "${POSTGRES_USER+x}" ]; then export "POSTGRES_USER=$value"; fi
      ;;
    POSTGRES_PASSWORD)
      if [ -z "${POSTGRES_PASSWORD+x}" ]; then export "POSTGRES_PASSWORD=$value"; fi
      ;;
    POSTGRES_PORT)
      if [ -z "${POSTGRES_PORT+x}" ]; then export "POSTGRES_PORT=$value"; fi
      ;;
    SPRING_DATASOURCE_URL)
      if [ -z "${SPRING_DATASOURCE_URL+x}" ]; then export "SPRING_DATASOURCE_URL=$value"; fi
      ;;
    SPRING_DATASOURCE_USERNAME)
      if [ -z "${SPRING_DATASOURCE_USERNAME+x}" ]; then export "SPRING_DATASOURCE_USERNAME=$value"; fi
      ;;
    SPRING_DATASOURCE_PASSWORD)
      if [ -z "${SPRING_DATASOURCE_PASSWORD+x}" ]; then export "SPRING_DATASOURCE_PASSWORD=$value"; fi
      ;;
    SERVER_PORT)
      if [ -z "${SERVER_PORT+x}" ]; then export "SERVER_PORT=$value"; fi
      ;;
    *)
      printf '%s\n' 'Unsupported variable in .env.' >&2
      exit 1
      ;;
  esac
}

if [ -f .env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ""|\#*)
        continue
        ;;
      *=*)
        key=${line%%=*}
        value=${line#*=}
        case "$value" in
          *[[:space:]]*|*\"*|*\'*)
            printf '%s\n' 'Unsupported .env value syntax; use an unquoted single-line KEY=value entry.' >&2
            exit 1
            ;;
        esac
        set_env_if_unset "$key" "$value"
        ;;
      *)
        printf '%s\n' 'Invalid .env entry; expected a single-line KEY=value entry.' >&2
        exit 1
        ;;
    esac
  done < .env
fi

if [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
  export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-pulso}"
fi

if [ -z "${SPRING_DATASOURCE_USERNAME:-}" ]; then
  export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-pulso}"
fi

if [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-}"
fi

: "${SPRING_DATASOURCE_PASSWORD:?Set POSTGRES_PASSWORD or SPRING_DATASOURCE_PASSWORD in the environment or .env}"

exec ./backend/gradlew -p backend bootRun
