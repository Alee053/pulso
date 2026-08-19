# Pulso documentation

This directory is the durable reference for the codebase: what is deployed in the repository today, which seams the architecture protects, and how the team should evolve it.

## Start here

1. [Current stack](./stack.md) — versions, project responsibilities, local services, and known gaps.
2. [Architecture overview](./architecture/overview.md) — system shape, dependency direction, and integration rules.
3. [Frontend architecture](./architecture/frontend.md) — current React prototype and the path to Angular feature modules.
4. [Backend architecture](./architecture/backend.md) — Spring package structure, HTTP seams, persistence, and testing.
5. [Scalability](./architecture/scalability.md) — default decisions and triggers for adding complexity.
6. [Working agreements](./engineering/working-agreements.md) — team workflow, review standards, and definition of done.
7. [Architecture decisions](./adr/0001-monorepo-project-seams.md) — decisions that are costly or surprising to reverse.

The product vocabulary is maintained in [`CONTEXT.md`](../CONTEXT.md). It intentionally contains language, not implementation details.

## Documentation rules

- Mark statements as **Current**, **Target**, or **Decision** when their status matters.
- Update the relevant architecture page in the same change as a structural code change.
- Record a short ADR when a decision is hard to reverse, surprising without context, and based on a real trade-off.
- Prefer one canonical term for a product concept. Add alternatives to `CONTEXT.md` under `_Avoid_` rather than allowing synonyms to spread.
- Keep examples small and point to the code that proves the current state.
