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

## Developer onboarding

This is the canonical first-run path for a full local environment. It starts
the Supabase services, the Spring Boot API, and the Angular dev server. The
first `pnpm db:up` may download several Docker images.

### 1. Check the local toolchain

Use Node 24 LTS or 26, Java 21, pnpm 11, and a running Docker daemon:

```sh
node --version
pnpm --version
java --version
docker info
docker compose version
```

If you use `nvm`, the repository includes the recommended Node major version:

```sh
nvm install
nvm use
```

If pnpm is not installed, enable the version declared by the repository:

```sh
corepack enable
corepack prepare pnpm@11.17.0 --activate
```

### 2. Install dependencies and create local configuration

Run these commands from the repository root. Create `.env` only if it does not
already exist; it is ignored by Git and contains local backend defaults.

```sh
pnpm install
test -f .env || cp .env.example .env
```

The checked-in example points to the local Supabase database and Auth JWKS.
Do not replace the JWKS configuration with a service-role key. The frontend
can run with its Supabase values blank because the current visual prototype
uses fictional local data; configure the publishable key later in
`frontend/src/app/core/config/app-config.ts` when Auth UI is introduced.

### 3. Start Supabase — Terminal 1

Keep this terminal or the Docker services running while using the backend:

```sh
pnpm db:up
pnpm db:reset
pnpm db:verify
```

`db:reset` recreates the local database, applies every migration, and loads the
two fictional seed projects. It is safe for this local development database,
but it deletes local data created by previous runs. Use `pnpm db:status` when
you need the local URLs or development keys; do not paste those keys into
issues, logs, or commits.

### 4. Start the backend — Terminal 2

Open a second terminal in the repository root:

```sh
pnpm backend:run
```

The runner loads `.env`, connects Spring Boot to local Supabase PostgreSQL,
and starts the API on port `8080`. Wait until the logs show that the
application has started before testing API requests.

### 5. Start the frontend — Terminal 3

Open a third terminal in the repository root:

```sh
pnpm dev
```

Open <http://localhost:4200>. The Angular development server does not start the
backend, so its terminal must remain running for server-backed requests.

### 6. Verify the local stack

Run these checks from any terminal:

```sh
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
curl 'http://localhost:8080/api/v1/startups?offset=0&limit=2'
```

The local services are available at:

- Frontend: <http://localhost:4200>
- Backend liveness: <http://localhost:8080/api/health>
- Backend dependency health: <http://localhost:8080/actuator/health>
- Supabase API/Auth: <http://localhost:54321>
- Supabase Studio: <http://localhost:54323>
- Supabase Mailpit: <http://localhost:54324>

### Stop the environment

Press `Ctrl+C` in the frontend and backend terminals, then stop Supabase:

```sh
pnpm db:down
```

### Frontend-only quick path

If you only need to work on the current visual prototype, Supabase and Spring
Boot are not required:

```sh
pnpm install
pnpm dev
```

The prototype uses local mock data. Start the full stack when working on the
real API, database migrations, or Auth integration.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `pnpm: command not found` | Enable Corepack or install pnpm 11, then rerun `pnpm install`. |
| Node engine or Angular version warning | Use Node 24 LTS or 26; run `nvm install && nvm use` if using nvm. |
| Docker connection error during `pnpm db:up` | Start Docker Engine/Desktop and confirm `docker info` succeeds. |
| Backend says `Set SPRING_DATASOURCE_PASSWORD` | Create `.env` from `.env.example`, or provide the variable in the shell. |
| `/actuator/health` is down | Start Supabase first, then restart `pnpm backend:run`; `/api/health` only checks process liveness. |
| Port `4200`, `8080`, or `54321` is busy | Stop the process using the port before starting the corresponding service. |
| Local data or migrations are stale | Run `pnpm db:reset`; this deletes local database data and reapplies migrations/seed. |

For database-specific workflows, see [`database/README.md`](database/README.md).
The current logical MVP model is in
[`database/pulso.dbml`](database/pulso.dbml), with a visual overview in the
[Pulso class diagram](docs/diagrams/pulso-class-diagram.mmd).
For endpoint contracts and curl examples, see [`docs/api.md`](docs/api.md).

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
| `pnpm db:verify` | Verify local Supabase status, seed, tests, and Auth health |
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
