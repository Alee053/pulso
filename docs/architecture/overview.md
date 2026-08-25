# Architecture overview

## Scope

This is the working architecture for the monorepo. It constrains dependencies
and ownership while the product model is still being established. The first
vertical slice is intentionally small: a public startup catalog, a protected
JWT example, and a Supabase schema that can be recreated locally.

## System shape

```text
┌──────────────────────────────┐
│ Browser                      │
└──────────────┬───────────────┘
               │ HTTP/JSON
┌──────────────▼───────────────┐
│ frontend/                    │  Angular UI, feature state, API adapters
│ Angular standalone           │
└──────────────┬───────────────┘
               │ versioned HTTP interface
┌──────────────▼───────────────┐
│ backend/                     │  API adapters, use cases, domain rules
│ Spring Boot                  │
└──────────────┬───────────────┘
               │ repository adapters
┌──────────────▼───────────────┐
│ Supabase PostgreSQL + Auth   │  system of record and identity provider
└──────────────────────────────┘
```

The frontend and backend are separate projects with a single integration seam: the HTTP contract. The frontend never reads PostgreSQL directly, and the backend never imports frontend code.

The current logical MVP persistence model is documented in
[`database/pulso.dbml`](../../database/pulso.dbml), with its conceptual
relationships visualized in the [class diagram](../diagrams/pulso-class-diagram.mmd).
The deployed schema remains defined by the ordered Supabase migrations.

## Ownership

| Concern | Owner | Rule |
| --- | --- | --- |
| UI composition and accessibility | Frontend | Keep browser concerns in `frontend/`; do not duplicate them in the backend. |
| User-facing feature state | Frontend feature module | Keep state near the feature that owns it; promote it only when multiple features truly share it. |
| API contract | Backend HTTP interface | The backend owns request/response compatibility and error semantics. |
| Business rules | Backend domain module | Rules must be testable without HTTP, Spring, or PostgreSQL. |
| Persistence | Backend infrastructure adapter | Database details stay behind a repository interface and never leak into the frontend. |
| Database schema and seed | `database/` through Supabase CLI | Schema changes require an ordered migration and a review of backward compatibility. |
| Authentication | Supabase Auth + backend security configuration | Angular holds a session; Spring validates the JWT and owns product authorization. |
| Cross-project types | Contract tooling | Do not create a shared package until duplication is proven; prefer generated clients from a versioned contract. |

## Dependency direction

The dependency graph points toward stable policy and away from changeable details:

```text
Frontend UI → feature state/use cases → HTTP adapter → backend HTTP interface

Backend HTTP adapter → application use case → domain policy
Backend infrastructure adapter ────────────────┘

Angular Auth gateway → Supabase Auth session
Spring Security ← validated Supabase JWT
database/ migrations → Supabase PostgreSQL
```

The backend domain must not depend on Spring MVC, JDBC, PostgreSQL types, or external providers. The frontend feature policy must not depend on a concrete HTTP library when a small adapter interface can hide it.

## Deep modules and seams

Use a **module** for any unit with an interface and an implementation. Prefer deep modules: a small interface should hide meaningful behavior, giving callers leverage and maintainers locality.

- Put the **interface** at the seam where callers and tests need stability.
- Use an **adapter** for HTTP, persistence, browser storage, or external providers.
- Keep implementation details private to the module; do not expose database records or transport DTOs as domain objects.
- Add a seam only when behavior varies or needs independent testing. One adapter can remain a concrete implementation; two genuinely different adapters justify a seam.
- Test through the module interface first. Tests that reach deep into implementation are a signal to redesign the interface.

## Cross-project contract rules

- Start real product endpoints under `/api/v1`; keep operational endpoints such as `/api/health` and Actuator separate.
- Treat request and response schemas as public interfaces: validate input, define error behavior, and preserve compatibility deliberately.
- Use additive changes first. For breaking changes, introduce a new version or a migration window and document it.
- Keep API calls in frontend data-access adapters. UI modules should not scatter raw URLs or duplicate response mapping.
- Do not share database models, Java classes, or TypeScript implementation types across projects.
- Keep the Supabase browser client behind an Auth gateway. It may manage a
  session, but it must not become a second product data-access path.
- Treat RLS as a database defense-in-depth boundary for direct Supabase access;
  JDBC-backed API use cases still perform server-side authorization.

## How architecture changes

When a change alters a project seam, dependency direction, persistence strategy, API versioning, authentication boundary, or deployment shape:

1. Write or update an ADR in [`docs/adr/`](../adr/0001-monorepo-project-seams.md).
2. Update the affected architecture page.
3. Add a focused test at the changed interface.
4. Record migration and rollback concerns in the pull request.

Small local refactors do not need an ADR. They still need tests and documentation updates when the team-facing structure changes.
