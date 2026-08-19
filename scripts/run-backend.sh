#!/usr/bin/env sh

set -eu

if [ -f .env ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ""|\#*)
        continue
        ;;
      POSTGRES_DB=*|POSTGRES_USER=*|POSTGRES_PASSWORD=*|POSTGRES_PORT=*|SPRING_DATASOURCE_URL=*|SPRING_DATASOURCE_USERNAME=*|SPRING_DATASOURCE_PASSWORD=*|SERVER_PORT=*)
        export "$line"
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
