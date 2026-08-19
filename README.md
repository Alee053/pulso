# Pulso

Pulso is a pnpm monorepo for the product frontend, backend API, and database
change history.

The documentation index and architecture conventions are in
[`docs/`](docs/README.md). The product context and requirements are in
[`docs/especificacion-general.md`](docs/especificacion-general.md).

## Projects

| Project | Directory | Stack | Role |
| --- | --- | --- | --- |
| Frontend | `frontend/` | Angular 22 standalone, TypeScript 6, Tailwind CSS 4, RxJS 7 | Browser application and feature UI |
| Backend | `backend/` | Java 21, Spring Boot 4.1, Gradle 9.4.1 | Versioned REST API and PostgreSQL integration |
| Database | `database/` | Supabase CLI, PostgreSQL, Supabase Auth | Local services, migrations, seed data, and hosted Supabase workflow |

The browser talks to the backend through `/api/v1`. It uses the Supabase
client only for Auth sessions. The backend validates Supabase JWTs and owns
product authorization; the browser never connects to PostgreSQL directly.

The discovery UI remains a visual prototype backed by fictional local mocks.
The typed `StartupsApi` adapter and backend endpoint are the first integration
seam for replacing those mocks in a later vertical slice.

## Prerequisites

- Node.js 24 LTS or 26 and pnpm 11
- Java 21
- Docker Engine with Compose support

The Supabase CLI is a pinned project dependency, so a separate global CLI is
not required. The repository includes `.nvmrc` and a Gradle Wrapper for
consistent local tooling. The first commands may download Java dependencies and
Supabase Docker images.

## Quick start

Install dependencies and create the ignored backend environment file:

```sh
pnpm install
cp .env.example .env
```

Start the local Supabase stack, apply migrations, and inspect local connection
values:

```sh
pnpm db:up
pnpm db:reset
pnpm db:status
```

The local `.env.example` already points the backend to the Supabase Auth JWKS
endpoint. Keep the JWT secret fallback blank unless a legacy HS256 token is
being tested; never commit it.

Run the backend and frontend in separate terminals:

```sh
pnpm backend:run
pnpm dev
```

The development services are available at:

- Frontend: <http://localhost:4200>
- Backend liveness: <http://localhost:8080/api/health>
- Backend dependency health: <http://localhost:8080/actuator/health>
- Supabase API/Auth: <http://localhost:54321>
- Supabase Studio: <http://localhost:54323>
- Supabase Mailpit: <http://localhost:54324>

Stop local Supabase when finished:

```sh
pnpm db:down
```

## Commands

| Command | Purpose |
| --- | --- |
| `pnpm install` | Install the workspace dependencies |
| `pnpm dev` | Run the Angular development server |
| `pnpm build` | Build the frontend |
| `pnpm --filter @pulso/frontend test` | Run the Angular/Vitest suite |
| `pnpm backend:run` | Load `.env` and run Spring Boot |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend executable JAR |
| `pnpm db:up` | Start the local Supabase services |
| `pnpm db:down` | Stop the local Supabase services |
| `pnpm db:reset` | Recreate the local database and run migrations/seed |
| `pnpm db:status` | Show local URLs and development keys |
| `pnpm db:lint` | Check local SQL migrations |
| `pnpm db:test` | Run Supabase database tests when present |
| `pnpm db:migration:new <name>` | Create an ordered migration file |
| `pnpm db:push:dry-run` | Preview migrations for the linked hosted project |
| `pnpm db:push` | Apply migrations to a linked hosted project after review |

## Configuration

The root `.env` is backend-only. It supports the variables in
[`.env.example`](.env.example):

- `SPRING_DATASOURCE_*` configure the JDBC connection.
- `SUPABASE_JWT_ISSUER` and `SUPABASE_JWT_AUDIENCE` validate token claims.
- Local development uses the JWKS endpoint emitted by the Supabase CLI.
- Hosted Supabase should use `SUPABASE_JWT_JWK_SET_URI` with asymmetric signing
  keys; `SUPABASE_JWT_SECRET` is only an explicit legacy fallback. Do not copy
  a hosted service-role key into the backend or frontend.
- `CORS_ALLOWED_ORIGINS` controls browser access to the API.

Browser Auth configuration lives in
[`frontend/src/app/core/config/app-config.ts`](frontend/src/app/core/config/app-config.ts).
Only the Supabase URL and publishable/anon key belong there; both are public
browser configuration. The gateway intentionally remains inactive while those
values are blank, so the visual prototype can run without an Auth project.

The endpoint examples and response contracts are documented in
[`docs/api.md`](docs/api.md). The database workflow and schema ownership are in
[`database/README.md`](database/README.md).

`/api/health` is an API liveness check and does not claim that PostgreSQL is
available. Use `/actuator/health` for dependency health.

Do not commit `.env` files, hosted credentials, JWT secrets, service-role keys,
or generated Supabase local state.
