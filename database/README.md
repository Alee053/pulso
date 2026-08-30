# Database development

Pulso uses the Supabase CLI as the local development and migration interface.
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

For the complete developer onboarding flow, including the backend and Angular
dev server, start at [`../README.md#developer-onboarding`](../README.md#developer-onboarding).

## Local workflow

From the repository root, start the local database services:

```sh
pnpm db:up
pnpm db:reset
pnpm db:status
```

Keep the services running in the background or in one terminal. In another
terminal, run `pnpm backend:run`; in a third terminal, run `pnpm dev`. The
backend expects the local PostgreSQL password and Auth JWKS values from the
ignored root `.env` created from [`.env.example`](../.env.example).

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

`seed.sql` supplies two fictional published projects. The pgTAP foundation
checks live in `supabase/tests/database/01_foundation.sql` and run against the
local database after a reset.

`pnpm db:reset` is the normal way to make a new checkout deterministic or to
replay a migration. It removes local database data before applying the complete
migration and seed history; never run it against a linked hosted project.

Use `pnpm db:push` only after linking this checkout to the intended hosted
Supabase project. Review `pnpm db:push:dry-run` before applying a remote
change.

## Backend connection

Run `pnpm db:status` to obtain the local database URL, then copy the values into
the ignored root `.env`. The Spring Boot backend connects to PostgreSQL through
JDBC. The browser uses Supabase Auth, but never receives the database password
or a service-role key.

Hosted Supabase credentials belong in the deployment secret manager. Do not
commit `.env`, project passwords, JWT secrets, or service-role keys.
