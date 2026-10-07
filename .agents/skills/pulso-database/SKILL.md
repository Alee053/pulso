---
name: pulso-database
description: Change Pulso PostgreSQL/Supabase schema, migrations, RLS, seed data, or JDBC persistence while keeping domain references and authorization boundaries aligned. Use for database design, SQL, persistence bugs, or relational domain changes.
---

# Pulso database and persistence

## Establish the implemented model

1. Read the domain/database reference section in `AGENTS.md`, then
   `database/pulso.dbml`, `docs/diagrams/pulso-class-diagram.mmd`, and the relevant
   ordered migrations in `database/supabase/migrations/`.
2. Read `database/README.md`, `docs/architecture/backend.md`, and
   `docs/adr/0003-supabase-database-and-auth.md`. Inspect affected JDBC adapters,
   application ports, callers, and SQL/JDBC tests.
3. Identify identities, ownership, foreign keys, lifecycle states, uniqueness,
   nullable fields, transaction boundaries, and RLS/grants involved in the change.
   Confirm the target environment before executing a database command.

Proceed when the actual schema, invariant, and authorization boundary are
understood. Obtain clarification for material changes to the domain/data model.

## Make the smallest persistence change

- Use a new ordered migration for an intentional schema change; preserve applied
  migration history. Follow the repository's migration naming and current
  `db:migration:new` command rather than fabricating an applied version.
- Keep DBML and the class diagram aligned with intentional schema changes and
  update the relevant API/architecture documentation. State temporary differences.
- Use additive/expand-contract changes when deployed callers need compatibility.
  Identify migration/rollback implications and data backfills in the PR scope.
- Derive acting identity from validated JWTs and authorization from persisted
  membership. Treat client role/state fields as untrusted. Keep RLS and grants
  at least as restrictive as the intended contract and test unauthorized cases.
- Keep SQL parameterized, queries bounded, and multi-step invariant updates
  transactional. Consider conflicts/concurrent updates and explicit error
  mapping. Avoid leaking persistence or private fields through public responses.
- Keep operational logging within `ApplicationLogger` conventions; log only
  necessary metadata and preserve its redaction boundary.
- A seed/backfill must have a bounded target, defined duplicate/collision behavior,
  and post-write invariants. Rehearse on an authorized isolated local database.
  Local implementation does not authorize applying it to shared environments.

## Validate safely

Read `pulso-testing` and select affected SQL/RLS and JDBC integration checks.
Inspect the real database test target and Docker/Testcontainers availability.
Do not use a reset to make an unexplained migration failure disappear.

Before shared database writes, linked migrations, deletion, or credential changes,
obtain explicit authorization for the exact target/action and establish a recovery
plan. Use `db:push:dry-run` only after confirming the linked destination; it is
planning evidence, not proof a migration has been applied.

Complete the change with reviewed migration/adapter diffs, aligned references,
observed test outcomes or blockers, and explicit pending application steps.
