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

The discovery UI loads its published catalog through the typed `StartupsApi`
adapter and the Spring/Supabase backend. Saved projects, interest decisions,
and preference weights remain local session interactions until their
persistence stories are implemented.

## Prerequisites

- Node.js 24 LTS or 26 and pnpm 11
- Java 21
- Docker Engine with Compose support
- Google Chrome, available as `google-chrome`, `chrome`, or through
  `PULSO_SMOKE_BROWSER`, for the browser smoke test

The Supabase CLI is a pinned project dependency, so a separate global CLI is
not required. The repository includes `.nvmrc` and a Gradle Wrapper for
consistent local tooling. The first commands may download Java dependencies and
Supabase Docker images.

## Developer onboarding

This is the canonical first-run path for a full local environment. It starts
the Supabase services, the Spring Boot API, and the Angular dev server. The
first `docker compose up -d` may download several Docker images.

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

Run these commands from the repository root. Environment files are ignored by
Git and must never contain values committed to the repository.

```sh
pnpm install
cp .env.example .env
cp frontend/.env.development.example frontend/.env.development
```

`pnpm backend:run` automatically loads `backend/.env.development` when it is
present, then falls back to `backend/.env` and the root `.env`. Set
`PULSO_BACKEND_ENV_FILE` when another backend environment file is needed. The
checked-in [`.env.example`](.env.example) is a local-only HS256 fixture for the
repository Compose stack; never reuse it for a hosted environment.

The frontend `dev`, `start`, `preview`, and `build` commands automatically load
`frontend/.env.development`. Shell environment variables take precedence over
the file. Only the frontend API URL, Supabase URL, and publishable/anon key are
accepted; server passwords, JWT secrets, and service-role keys are rejected.

### 3. Start Supabase with Compose — Terminal 1

Keep this terminal or the Docker services running while using the backend:

```sh
docker compose up -d
docker compose ps
docker compose logs --no-color migrator
```

Compose starts Postgres, Auth, REST, Realtime, Storage, Studio and Mailpit. The
`migrator` service applies the ordered files in
[`database/supabase/migrations/`](database/supabase/migrations/) and then runs
[`database/supabase/seed.sql`](database/supabase/seed.sql). The seed is
idempotent and can be rerun after editing it:

```sh
pnpm db:compose:seed
```

When a new migration is added, run `docker compose up -d` again. To rebuild the
throwaway local database from zero, use the destructive local-only command:

```sh
docker compose down -v
docker compose up -d
```

Do not run the Compose stack and the alternative `pnpm db:up` Supabase CLI stack
at the same time; both use the default local Supabase ports.

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
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl 'http://localhost:8080/api/v1/startups?offset=0&limit=2'
```

To repeat the complete backend verification with the database available and
with the PostgreSQL connection deliberately unavailable, run
`pnpm backend:verify` with Supabase running and `.env` created from
`.env.example`. The script uses temporary ports `18080` and `18081` and stops
only the processes it starts.

To verify the Auth path with a real local user, run `pnpm auth:verify` while
Supabase is running. The script obtains the local publishable/anon key in
memory, creates a unique temporary user, validates its JWT through
`GET /api/v1/me`, checks the unauthenticated and invalid-token responses, and
rejects the local `service_role` token before logging the user out. It uses port
`18082` for a temporary backend and removes all temporary responses,
credentials, and logs when it finishes.

To verify the complete Sprint 0 stack from a real browser, run
`pnpm smoke:verify`. The command runs, in order, dependency installation from
the lockfile, local Supabase startup, database reset/lint/tests/verification,
frontend/backend/Auth checks, a production frontend build, a temporary Spring
Boot backend, the built frontend, and a headless Chrome journey. The journey
registers and persists a local Auth session, calls `/api/v1/me`, loads the
seeded discovery catalog through the browser, confirms the bearer request, and
logs out.

The smoke test needs no hosted Supabase project, linked project, `.env` file,
or manually supplied secret. It reads the local public Auth URL/key from the
Supabase CLI and supplies the local backend connection values internally. Its
temporary services use ports `4200` (frontend), `8080` (backend), and `9222`
(Chrome DevTools). Override them with `PULSO_SMOKE_FRONTEND_PORT`,
`PULSO_SMOKE_BACKEND_PORT`, and `PULSO_SMOKE_CDP_PORT` when necessary.
On Windows, `pnpm smoke:verify` uses the `sh` command provided by Git Bash;
the script also accepts `PULSO_SMOKE_BROWSER` when Chrome is not on `PATH`.
The script removes its temporary users, logs, responses, browser profile, and
child processes; Supabase remains running for subsequent development commands.
Because it runs `pnpm db:reset`, it deletes data in the local database and
reapplies the migrations and seed. It does not touch a hosted project.

The `pnpm db:*` commands and `pnpm smoke:verify` retain the pinned Supabase CLI
workflow for linting, pgTAP and the complete browser smoke test. Use that
workflow or the repository Compose stack, not both at the same time.

The local services are available at:

- Frontend: <http://localhost:4200>
- Backend liveness: <http://localhost:8080/api/health>
- Backend dependency health: <http://localhost:8080/actuator/health>
- Supabase API/Auth: <http://localhost:54321>
- Supabase Studio: <http://localhost:54323>
- Supabase Mailpit: <http://localhost:54324>

### Stop the environment

Press `Ctrl+C` in the frontend and backend terminals, then stop Compose:

```sh
docker compose down
```

### Frontend-only quick path

If you only need to work on frontend UI or tests, Supabase and Spring Boot are
not required:

```sh
pnpm install
pnpm dev
```

The discovery screen shows its loading, error, or empty state when the real API
is unavailable; it does not silently fall back to fictional data. Start the
full stack when working on the real discovery feed, database migrations, or
Auth integration.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `pnpm: command not found` | Enable Corepack or install pnpm 11, then rerun `pnpm install`. |
| Node engine or Angular version warning | Use Node 24 LTS or 26; run `nvm install && nvm use` if using nvm. |
| Docker connection error during `docker compose up -d` | Start Docker Engine/Desktop and confirm `docker info` succeeds. |
| Backend says `Set SPRING_DATASOURCE_PASSWORD` | Create `.env` from `.env.example`, or provide the variable in the shell. |
| `/actuator/health` is down | Start Supabase first, then restart `pnpm backend:run`; `/api/health` only checks process liveness. |
| Port `4200`, `8080`, `54321`, or `9222` is busy | Stop the process using the port before starting the corresponding service or set the smoke-test port override. |
| Local data or migrations are stale | Run `docker compose down -v` and then `docker compose up -d`; this deletes local database data and reapplies migrations/seed. |
| `pnpm smoke:verify` cannot find Chrome | Install Google Chrome, add it to `PATH`, or set `PULSO_SMOKE_BROWSER` to its executable. On Windows, run from a shell with Git Bash `sh` available. |

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
| `pnpm backend:verify` | Verify JDBC, seed, liveness, and readiness with local Supabase |
| `pnpm auth:verify` | Verify local Supabase signup, login, JWT claims, 401 responses including `service_role`, and logout |
| `pnpm smoke:verify` | Run the complete local database, backend, frontend, Auth, and browser smoke test |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend executable JAR |
| `pnpm db:up` | Start the local Supabase services |
| `pnpm db:down` | Stop the local Supabase services |
| `pnpm db:reset` | Recreate the local database and run migrations/seed |
| `pnpm db:status` | Show local URLs and development keys |
| `pnpm db:lint` | Check local SQL migrations |
| `pnpm db:test` | Run Supabase database tests when present |
| `pnpm db:verify` | Verify local Supabase status, seed, tests, and Auth health |
| `docker compose up -d` | Start the repository Compose stack and apply pending migrations/seed |
| `pnpm db:compose:seed` | Rerun the editable local seed without resetting the database |
| `pnpm db:compose:status` | Show Compose service status |
| `pnpm db:migration:new <name>` | Create an ordered migration file |
| `pnpm db:push:dry-run` | Preview migrations for the linked hosted project |
| `pnpm db:push` | Apply migrations to a linked hosted project after review |

## Configuration

The backend environment file supports the variables in
[`.env.example`](.env.example):

- `SPRING_DATASOURCE_*` configure the JDBC connection.
- `SUPABASE_JWT_ISSUER` and `SUPABASE_JWT_AUDIENCE` validate token claims.
- The repository Compose stack uses the local-only HS256 fixture from
  [`.env.example`](.env.example); hosted Supabase should use
  `SUPABASE_JWT_JWK_SET_URI` with asymmetric signing keys instead. Do not copy
  a hosted service-role key into the backend or frontend.
- `CORS_ALLOWED_ORIGINS` controls browser access to the API.

Frontend commands read these public values at startup/build time through
Angular's `define` mechanism:

- `PULSO_API_BASE_URL` defaults to `http://localhost:8080`.
- `PULSO_SUPABASE_URL` defaults to `http://127.0.0.1:54321`.
- `PULSO_SUPABASE_PUBLISHABLE_KEY` defaults to empty, which keeps the Auth
  gateway inactive until a publishable/anon key is supplied.

For Compose-based local Auth, copy
[`frontend/.env.development.example`](frontend/.env.development.example) to
`frontend/.env.development`. Its key is a public, local-only fixture; it must
never be replaced with a service-role key.

They are public browser configuration, so never use a service-role key,
database password, or JWT secret in them. If the publishable key is missing, the
Auth gateway returns no session; it does not invent credentials. `StartupsApi`
does not replace API errors with mock data, and the discovery session keeps that
failure visible while using the API-backed catalog as its runtime source.

Browser Auth configuration is exposed through
[`frontend/src/app/core/config/app-config.ts`](frontend/src/app/core/config/app-config.ts).
Only the Supabase URL and publishable/anon key belong in the frontend build;
both are public browser configuration. The gateway intentionally remains
inactive while the publishable key is blank, so the frontend can run without an
Auth project.

The endpoint examples and response contracts are documented in
[`docs/api.md`](docs/api.md). The database workflow and schema ownership are in
[`database/README.md`](database/README.md).

`/api/health` is an API liveness check and does not claim that PostgreSQL is
available. Use `/actuator/health` or its public `liveness` and `readiness`
probes for dependency health; readiness includes the PostgreSQL datasource.

Do not commit `.env` files, hosted credentials, JWT secrets, service-role keys,
or generated Supabase local state.
