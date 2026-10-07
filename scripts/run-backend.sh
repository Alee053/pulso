#!/usr/bin/env sh

set -eu

set_env_if_unset() {
  key=$1
  value=$2

  case "$key" in
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
    PULSO_LOG_LEVEL)
      if [ -z "${PULSO_LOG_LEVEL+x}" ]; then export "PULSO_LOG_LEVEL=$value"; fi
      ;;
    SUPABASE_JWT_ISSUER)
      if [ -z "${SUPABASE_JWT_ISSUER+x}" ]; then export "SUPABASE_JWT_ISSUER=$value"; fi
      ;;
    SUPABASE_JWT_AUDIENCE)
      if [ -z "${SUPABASE_JWT_AUDIENCE+x}" ]; then export "SUPABASE_JWT_AUDIENCE=$value"; fi
      ;;
    SUPABASE_JWT_JWK_SET_URI)
      if [ -z "${SUPABASE_JWT_JWK_SET_URI+x}" ]; then export "SUPABASE_JWT_JWK_SET_URI=$value"; fi
      ;;
    SUPABASE_JWT_SECRET)
      if [ -z "${SUPABASE_JWT_SECRET+x}" ]; then export "SUPABASE_JWT_SECRET=$value"; fi
      ;;
    CORS_ALLOWED_ORIGINS)
      if [ -z "${CORS_ALLOWED_ORIGINS+x}" ]; then export "CORS_ALLOWED_ORIGINS=$value"; fi
      ;;
    DB_CONNECTION_TIMEOUT_MS)
      if [ -z "${DB_CONNECTION_TIMEOUT_MS+x}" ]; then export "DB_CONNECTION_TIMEOUT_MS=$value"; fi
      ;;
    PULSO_SMTP_PORT)
      if [ -z "${PULSO_SMTP_PORT+x}" ]; then export "PULSO_SMTP_PORT=$value"; fi
      ;;
    SMTP_HOST)
      if [ -z "${SMTP_HOST+x}" ]; then export "SMTP_HOST=$value"; fi
      ;;
    SMTP_PORT)
      if [ -z "${SMTP_PORT+x}" ]; then export "SMTP_PORT=$value"; fi
      ;;
    SMTP_USERNAME)
      if [ -z "${SMTP_USERNAME+x}" ]; then export "SMTP_USERNAME=$value"; fi
      ;;
    SMTP_PASSWORD)
      if [ -z "${SMTP_PASSWORD+x}" ]; then export "SMTP_PASSWORD=$value"; fi
      ;;
    SMTP_AUTH)
      if [ -z "${SMTP_AUTH+x}" ]; then export "SMTP_AUTH=$value"; fi
      ;;
    SMTP_STARTTLS)
      if [ -z "${SMTP_STARTTLS+x}" ]; then export "SMTP_STARTTLS=$value"; fi
      ;;
    PULSO_INVITATION_FRONTEND_URL)
      if [ -z "${PULSO_INVITATION_FRONTEND_URL+x}" ]; then export "PULSO_INVITATION_FRONTEND_URL=$value"; fi
      ;;
    PULSO_INVITATION_FROM)
      if [ -z "${PULSO_INVITATION_FROM+x}" ]; then export "PULSO_INVITATION_FROM=$value"; fi
      ;;
    *)
      printf '%s\n' 'Unsupported variable in the selected environment file.' >&2
      exit 1
      ;;
  esac
}

env_file=${PULSO_BACKEND_ENV_FILE:-}
if [ -z "$env_file" ]; then
  if [ -f backend/.env.development ]; then
    env_file=backend/.env.development
  elif [ -f backend/.env ]; then
    env_file=backend/.env
  elif [ -f .env ]; then
    env_file=.env
  fi
fi

if [ -n "$env_file" ] && [ ! -r "$env_file" ]; then
  printf 'Environment file not found: %s\n' "$env_file" >&2
  exit 1
fi

if [ -n "$env_file" ]; then
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
            printf '%s\n' 'Unsupported environment value syntax; use an unquoted single-line KEY=value entry.' >&2
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
  done < "$env_file"
fi

if [ -z "${SPRING_DATASOURCE_URL+x}" ]; then
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:54322/postgres"
fi

if [ -z "${SPRING_DATASOURCE_USERNAME+x}" ]; then
  export SPRING_DATASOURCE_USERNAME="postgres"
fi

if [ -z "${SPRING_DATASOURCE_PASSWORD+x}" ]; then
  export SPRING_DATASOURCE_PASSWORD=""
fi

: "${SPRING_DATASOURCE_PASSWORD:?Set SPRING_DATASOURCE_PASSWORD in the environment or .env}"

if [ -z "${SUPABASE_JWT_ISSUER+x}" ]; then
  export SUPABASE_JWT_ISSUER="http://127.0.0.1:54321/auth/v1"
fi

if [ -z "${SUPABASE_JWT_AUDIENCE+x}" ]; then
  export SUPABASE_JWT_AUDIENCE="authenticated"
fi

if [ -z "${SUPABASE_JWT_JWK_SET_URI+x}" ]; then
  export SUPABASE_JWT_JWK_SET_URI="http://127.0.0.1:54321/auth/v1/.well-known/jwks.json"
fi

if [ -z "${SUPABASE_JWT_JWK_SET_URI:-}" ] && [ -z "${SUPABASE_JWT_SECRET:-}" ]; then
  printf '%s\n' 'Set SUPABASE_JWT_JWK_SET_URI or SUPABASE_JWT_SECRET in the environment or .env.' >&2
  exit 1
fi

exec ./backend/gradlew -p backend bootRun
