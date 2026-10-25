# Database development

Pulso uses the ordered Supabase SQL history as its schema source of truth.
Compose is the default local development interface; the pinned Supabase CLI
remains available for migration tooling and the full database/browser checks.
The canonical schema is the ordered SQL history in
[`supabase/migrations/`](supabase/migrations/); application startup does not
create or alter tables.

The current logical MVP model is documented in
[`pulso.dbml`](pulso.dbml), with a conceptual overview in the
[Pulso class diagram](../docs/diagrams/pulso-class-diagram.mmd). The DBML and
diagram describe the intended domain shape; the ordered SQL migrations remain
the source of truth for what is implemented in Supabase. The naming boundary
between the logical model and the current foundation is recorded in
[ADR 0004](../docs/adr/0004-project-startup-compatibility.md).

The S3 discovery-persistence migration adds profile tag/skill selections,
saved-project state, feedback state, and interaction history. PUL-18 adds the
canonical `project_opportunities` table and then constrains
`interaction_events.opportunity_id` to that table. Opportunity records are
managed through the authenticated backend API; direct Supabase table grants
remain revoked. PUL-22 adds `opportunity_skills` with a unique
opportunity/skill key, constrained `REQUIRED`/`PREFERRED` importance, cascading
opportunity deletion, restricted catalog-skill deletion, and the same
backend-only RLS/grant boundary.

For the complete developer onboarding flow, including the backend and Angular
dev server, start at [`../README.md#developer-onboarding`](../README.md#developer-onboarding).

## Local workflow with Compose

The repository root contains [`compose.yaml`](../compose.yaml), which is the
short path for day-to-day local development. From the repository root:

```sh
docker compose up -d
docker compose ps
```

The Compose `migrator` waits for local Auth, applies every pending SQL file in
`supabase/migrations/`, records it in `supabase_migrations.schema_migrations`,
and runs `supabase/seed.sql`. Edit the seed and rerun it with:

```sh
pnpm db:compose:seed
```

The database and storage volumes persist between `docker compose up -d` runs.
For a disposable full rebuild only, run `docker compose down -v` before
starting again; this removes local data.

The pinned Supabase CLI workflow remains available for SQL linting, pgTAP and
the complete browser smoke test. Do not run both local stacks simultaneously,
because they bind the same default ports.

## Local workflow with the Supabase CLI

From the repository root, start the local database services:

```sh
pnpm db:up
pnpm db:reset
pnpm db:status
```

The CLI stack generates its own local ES256 Auth keys. The root `.env.example`
is configured for the Compose stack's local HS256 fixture, so use the values
reported by `pnpm db:status` in an ignored backend environment file when
running the backend manually against the CLI stack.

Keep the services running in the background or in one terminal. In another
terminal, run `pnpm backend:run`; in a third terminal, run `pnpm dev`. The
backend command automatically loads `backend/.env.development` when present.
Use `PULSO_BACKEND_ENV_FILE` to select another ignored environment file.

After resetting the local database, run `pnpm db:verify` to repeat the local
acceptance checks without printing development keys. It verifies service
status, migration linting, pgTAP tests, the two seeded projects, and the local
Auth health endpoint. It does not reset data, link a hosted project, or call
`db:push`.

The local stack provides PostgreSQL, Auth, Studio and Mailpit through Docker.
It is for development only and must not be exposed to public traffic.

The first migration creates the historical foundation:

- `public.profiles`, linked to `auth.users` and populated by the local profile
  trigger when a user signs up.
- `public.startups`, the temporary compatibility table for the first API
  endpoint and its `DRAFT`/`PUBLISHED`/`ARCHIVED` lifecycle.
- `public.startup_members`, the temporary compatibility membership table with
  owner/admin/member roles.

The ordered S0.3 migration transforms that foundation in place to the
canonical `public.sectors`, `public.projects`, and `public.project_members`
tables, then aligns the seed, pgTAP checks, and JDBC adapter. After a full
reset, those canonical tables are the schema consumed by the backend. Do not
add new product tables or writes that depend on the temporary startup names.
The existing `/api/v1/startups` route remains stable across that database
migration; the API, not a duplicate SQL model, is the compatibility boundary.

The two public seed projects are system data and are created before any
authenticated profile exists, so their `created_by_profile_id` is intentionally
nullable in this foundation. User-created projects must always provide that
profile reference; a later profile/project migration can make the column
strict after the seed strategy has an authenticated owner.

RLS is enabled on the canonical application tables. Profiles and project
memberships have narrow current-user grants, active sectors are readable by
authenticated users, and projects have no direct public table grant, so the
Spring backend remains the product data path for projects. The published-
project policy stays in the schema as defense in depth for any deliberate
future direct Supabase access.

Feature availability is persisted in `public.feature_flags` and read or
modified through the backend API. The table enables RLS and revokes direct
`anon` and `authenticated` grants; API writes require a persisted platform
`ADMIN` profile. The initial allowlist contains `language_switcher` and
`project_creation`, both enabled by default. See the
[feature-flag contract](../docs/api.md#feature-toggles-pul-109).

Create a migration with:

```sh
pnpm db:migration:new add_startup_field
```

After editing the SQL, replay the complete history:

```sh
pnpm db:reset
pnpm db:lint
pnpm db:test
pnpm db:verify
```

`seed.sql` supplies six active sectors, eight active tags, eight active skills,
and two fictional published projects. Catalog rows use stable slugs and are
updated by slug, so the seed can be rerun without duplicating reference data or
breaking the project fixtures. The pgTAP foundation checks live in
`supabase/tests/database/01_foundation.sql`, while catalog and seed behavior
checks live in `supabase/tests/database/03_catalog_seed.sql`; both run against
the local database after a reset.

`pnpm db:reset` is the normal way to make a new checkout deterministic or to
replay a migration. It removes local database data before applying the complete
migration and seed history; never run it against a linked hosted project.

Use `pnpm db:push` only after linking this checkout to the intended hosted
Supabase project. Review `pnpm db:push:dry-run` before applying a remote
change. The local database commands in this section do not validate a hosted
staging database.

## Backend connection

The Spring Boot backend connects to PostgreSQL through JDBC. The browser uses
Supabase Auth, but never receives the database password or a service-role key.
Keep staging credentials in `backend/.env.development` or another ignored file;
do not put them in the frontend environment.

Hosted Supabase credentials belong in the deployment secret manager. Do not
commit `.env`, project passwords, JWT secrets, or service-role keys.
