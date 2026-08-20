# Database development

Pulso uses the Supabase CLI as the local development and migration interface.
The canonical schema is the ordered SQL history in
[`supabase/migrations/`](supabase/migrations/); application startup does not
create or alter tables.

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

The local stack provides PostgreSQL, Auth, Studio and Mailpit through Docker.
It is for development only and must not be exposed to public traffic.

The foundation migration creates:

- `public.profiles`, linked to `auth.users` and populated by the local profile
  trigger when a user signs up.
- `public.startups`, including the `DRAFT`/`PUBLISHED`/`ARCHIVED` lifecycle
  used by the first API endpoint.
- `public.startup_members`, with owner/admin/member roles for future writes.

RLS is enabled on all three tables. Profiles and memberships have narrow
current-user grants for deliberate Auth/Data API use; startups currently have
no direct public table grant, so the Spring backend remains the only product
data path. The published-startup policy stays in the schema as defense in
depth if a safe view or an intentional direct Data API surface is added later.

Create a migration with:

```sh
pnpm db:migration:new add_startup_field
```

After editing the SQL, replay the complete history:

```sh
pnpm db:reset
pnpm db:lint
pnpm db:test
```

`seed.sql` supplies two fictional published startups. The pgTAP foundation
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
