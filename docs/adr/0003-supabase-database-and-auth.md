# ADR 0003: Supabase owns PostgreSQL and Auth integration

- Status: Accepted
- Date: 2026-08-19
- Scope: database workflow, authentication boundary, and backend persistence

## Context

The product needs PostgreSQL and user authentication while the team is still
setting up the first external-contributor-ready repository. A local database
container alone would not establish a durable hosted workflow, and allowing the
browser to query PostgreSQL directly would couple the UI to persistence and
make server-side authorization difficult to enforce.

## Decision

- Use hosted PostgreSQL and Auth from Supabase.
- Use the Supabase CLI project under `database/` for local services, ordered SQL
  migrations, seed data, and later linking/pushing to the hosted project.
- Keep the canonical schema in `database/supabase/migrations/`; application
  startup must not create or mutate tables.
- Let Angular use `@supabase/supabase-js` only for Auth session management and
  send access tokens to the Spring Boot API.
- Let Spring Security validate Supabase JWTs. Hosted environments use the
  issuer's JWKS endpoint; current local CLI development uses its local ES256
  JWKS endpoint. An explicit HMAC secret remains a legacy fallback, and exactly
  one verification source must be configured. User API routes require the
  `authenticated` role and reject `service_role` tokens.
- Keep product persistence behind Spring application ports and JDBC adapters.
  The backend owns product authorization and never exposes a service-role key to
  the browser.
- Enable Row Level Security for all application tables. Keep direct Data API
  grants narrow; this baseline does not grant public startup table reads, so
  the backend remains the product data path. RLS is defense in depth for any
  deliberate future direct Supabase access; backend authorization is still
  required for JDBC-backed product use cases.

## Consequences

Positive:

- Local contributors can recreate the database from a reviewed migration
  history with `pnpm db:reset`.
- Auth and Postgres remain in the same platform without making Angular the
  owner of product data access.
- The backend can evolve its API and authorization independently of database
  transport details.
- RLS constrains any deliberately granted direct reads and provides a safe
  boundary for future Supabase Data API use.

Costs and constraints:

- Local development requires Docker and the Supabase service images.
- Hosted deployment must configure database credentials and JWT verification
  secrets outside the repository.
- Direct JDBC access requires a persistent/session connection, not a transaction
  pooler connection that is incompatible with prepared statements.
- The migration history must stay compatible with the hosted Supabase Postgres
  version; update `database/supabase/config.toml` deliberately when the major
  version changes.

## Rejected alternatives

- **JPA/Hibernate as the first persistence layer:** adds entity lifecycle and
  mapping conventions before the domain model is stable; explicit JDBC keeps the
  first read path small and visible.
- **Flyway/Liquibase as a second migration owner:** would duplicate ownership
  with Supabase's native workflow and make local/hosted drift easier.
- **Frontend direct database access:** would expose persistence details and move
  authorization into a client that cannot be trusted.
- **A full auth UI in the bootstrap:** useful for product delivery, but not
  required to establish the token boundary and would obscure the foundation
  being demonstrated.
