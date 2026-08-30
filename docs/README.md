# Pulso documentation

This directory is the durable reference for the codebase: what is deployed in the repository today, which seams the architecture protects, and how the team should evolve it.

## Start here

1. [Developer onboarding](../README.md#developer-onboarding) — install, start, verify, and stop the local stack.
2. [Current stack](./stack.md) — versions, project responsibilities, local services, and known gaps.
3. [Product specification](./especificacion-general.md) — product vision, scope, users, capabilities, and roadmap.
4. [Architecture overview](./architecture/overview.md) — system shape, dependency direction, and integration rules.
5. [Logical data model](../database/pulso.dbml) and [class diagram](./diagrams/pulso-class-diagram.mmd) — current MVP entities and relationships.
6. [API examples](./api.md) — current endpoint contracts, authentication, and curl examples.
7. [Frontend architecture](./architecture/frontend.md) — Angular feature modules, state ownership, and UI boundaries.
8. [Backend architecture](./architecture/backend.md) — Spring package structure, HTTP seams, persistence, and testing.
9. [Scalability](./architecture/scalability.md) — default decisions and triggers for adding complexity.
10. [Working agreements](./engineering/working-agreements.md) — team workflow, review standards, and definition of done.
11. [Architecture decisions](./adr/0001-monorepo-project-seams.md) — decisions that are costly or surprising to reverse.
12. [Supabase decision](./adr/0003-supabase-database-and-auth.md) — database, migrations, RLS, and Auth boundaries.
13. [Project/startup compatibility](./adr/0004-project-startup-compatibility.md) — canonical domain naming, current baseline, and migration order.

The database-specific workflow starts at [`database/README.md`](../database/README.md).

The product vocabulary is maintained in [`CONTEXT.md`](../CONTEXT.md). It intentionally contains language, not implementation details.

## Documentation rules

- Mark statements as **Current**, **Target**, or **Decision** when their status matters.
- Update the relevant architecture page in the same change as a structural code change.
- Record a short ADR when a decision is hard to reverse, surprising without context, and based on a real trade-off.
- Prefer one canonical term for a product concept. Add alternatives to `CONTEXT.md` under `_Avoid_` rather than allowing synonyms to spread.
- Keep examples small and point to the code that proves the current state.
