# Pulso

Pulso is a monorepo for the product frontend and backend API.

## Projects

| Project | Directory | Current stack | Role |
| --- | --- | --- | --- |
| Frontend | `frontend/` | Vite + React prototype | Temporary UI while the production frontend is migrated to Angular |
| Backend | `backend/` | Java 21 + Spring Boot 4.1 + Gradle | API boundary and PostgreSQL integration point |

The checked-in frontend is a Vite/React demo. It is intentionally preserved while the future Angular application is designed and implemented.

## Prerequisites

- Node.js 24 or 26 and pnpm 11
- Java 21
- Docker Compose

The repository includes `.nvmrc` and a Gradle Wrapper for consistent local tooling. The first backend command may download the Gradle distribution and Java dependencies.

## Quick start

Install the frontend workspace dependencies:

```sh
cp .env.example .env
# Edit .env and set POSTGRES_PASSWORD to a local-only value.
pnpm install
```

Start PostgreSQL:

```sh
pnpm db:up
```

Run the backend API in a second terminal:

```sh
pnpm backend:run
```

Run the frontend in a third terminal:

```sh
pnpm dev
```

The development services are available at:

- Frontend: <http://localhost:5173>
- Backend liveness: <http://localhost:8080/api/health>
- Backend dependency health: <http://localhost:8080/actuator/health>

Stop PostgreSQL when finished:

```sh
pnpm db:down
```

## Commands

| Command | Purpose |
| --- | --- |
| `pnpm dev` | Run the frontend development server |
| `pnpm build` | Type-check and build the frontend |
| `pnpm preview` | Preview the frontend production build |
| `pnpm backend:run` | Run the Spring Boot API |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend executable JAR |
| `pnpm db:up` | Start the local PostgreSQL container |
| `pnpm db:down` | Stop the local PostgreSQL container |
| `pnpm db:logs` | Follow PostgreSQL logs |

## Configuration

Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` before starting PostgreSQL. Compose loads the file automatically, and `pnpm backend:run` loads the same supported local configuration for the Spring Boot process. The root runner accepts unquoted, single-line `KEY=value` entries for the variables listed in `.env.example`, rejects ambiguous dotenv syntax, and preserves explicitly set shell variables. The backend also accepts `SPRING_DATASOURCE_*` and `SERVER_PORT` environment variables directly when Gradle is run outside the root script.

`/api/health` is an API liveness check and does not claim that PostgreSQL is available. Use `/actuator/health` for dependency health.

Do not commit `.env` files or real credentials.
