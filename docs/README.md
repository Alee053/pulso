# Pulso documentation

This directory is the durable reference for the codebase: what is deployed in the repository today, which seams the architecture protects, and how the team should evolve it.

## Start here

1. [Current stack](./stack.md) — versions, project responsibilities, local services, and known gaps.
2. [Product specification](./especificacion-general.md) — product vision, scope, users, capabilities, and roadmap.
3. [Architecture overview](./architecture/overview.md) — system shape, dependency direction, and integration rules.
4. [Frontend architecture](./architecture/frontend.md) — Angular feature modules, state ownership, and UI boundaries.
5. [Backend architecture](./architecture/backend.md) — Spring package structure, HTTP seams, persistence, and testing.
6. [Scalability](./architecture/scalability.md) — default decisions and triggers for adding complexity.
7. [Working agreements](./engineering/working-agreements.md) — team workflow, review standards, and definition of done.
8. [Architecture decisions](./adr/0001-monorepo-project-seams.md) — decisions that are costly or surprising to reverse.

The product vocabulary is maintained in [`CONTEXT.md`](../CONTEXT.md). It intentionally contains language, not implementation details.

## Documentation rules

- Mark statements as **Current**, **Target**, or **Decision** when their status matters.
- Update the relevant architecture page in the same change as a structural code change.
- Record a short ADR when a decision is hard to reverse, surprising without context, and based on a real trade-off.
- Prefer one canonical term for a product concept. Add alternatives to `CONTEXT.md` under `_Avoid_` rather than allowing synonyms to spread.
- Keep examples small and point to the code that proves the current state.
