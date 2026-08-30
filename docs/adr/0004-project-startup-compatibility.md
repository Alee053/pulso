# Project as the canonical domain name with startup compatibility

**Status: Accepted.** Pulso uses `Project`/`projects` and `ProjectMember`/`project_members` as the canonical domain and persistence names because the MVP model represents more than only startups. The existing discovery vertical slice keeps `/api/v1/startups` and its `Startup*` transport names as a temporary compatibility contract while the database is migrated in place, so current consumers do not break.

## Baseline at the decision point

| Boundary | Current implementation | Canonical target or contract |
| --- | --- | --- |
| Logical model | [`database/pulso.dbml`](../../database/pulso.dbml) and [`pulso-class-diagram.mmd`](../diagrams/pulso-class-diagram.mmd) use `projects` and `project_members`. | Keep these names as the domain model. |
| Database | [`20260819000000_create_pulso_foundation.sql`](../../database/supabase/migrations/20260819000000_create_pulso_foundation.sql) creates `public.startups` and `public.startup_members`; [`seed.sql`](../../database/supabase/seed.sql) and pgTAP currently use them. | S0.3 transforms the foundation in place to `public.sectors`, `public.projects`, and `public.project_members`, preserving identifiers and data where possible. There is no second writable source of truth and no long-lived SQL compatibility view. |
| Backend | `JdbcStartupCatalog` reads `public.projects` and joins `public.sectors`; `StartupSummary` and `StartupResponse` expose the startup-shaped result. | Keep the canonical SQL target behind the adapter while the S0 HTTP response remains stable. |
| Frontend | `StartupsApi` calls `/api/v1/startups`; `PulsoSession` maps that summary into the discovery domain model. Saved and interested interactions remain local until their persistence stories. | Keep the current adapter and transport names until their consumers can be migrated deliberately. |
| HTTP | Public `GET /api/v1/startups` with bounded `offset`/`limit` pagination. | Preserve this route and JSON shape throughout Sprint 0; a future project-named endpoint must be additive or versioned. |

## Decision

- New domain tables, foreign keys, use cases, and feature terminology use `projects` and `project_members`.
- The initial migration is an immutable historical baseline. It is not edited to pretend that the current checkout already contains the target schema.
- S0.3 owns the ordered database cutover: rename or transform the existing foundation in place, backfill any required catalog/member values, update seed and pgTAP, and leave the backend reading the same canonical tables that the tests exercise.
- The unauthenticated system seed has no `profiles` row to own its public examples, so `projects.created_by_profile_id` stays nullable in this foundation. User-created projects must set it; a later profile/project story can tighten the physical constraint once the seed strategy supports an authenticated owner.
- The compatibility boundary is HTTP, not a second database model. `/api/v1/startups`, its response fields, and the existing `Startup*` transport names remain valid while the frontend and future clients are migrated.
- `profiles.id` continues to represent the UUID from Supabase Auth. Auth credentials, sessions, and recovery remain outside the product schema.

## Migration order

1. **S0.1 — Baseline:** record this mapping and freeze the temporary HTTP contract.
2. **S0.2 — Local Supabase:** make the local Docker services and reset/lint/test workflow reproducible without changing the hosted project.
3. **S0.3 — Schema cutover:** apply the ordered in-place migration to canonical sector/project/member tables, then align seed, pgTAP, and the JDBC query with those names.
4. **S0.4 — Spring readiness:** verify that the backend connects to the canonical local schema, reports dependency health, and serves the seeded catalog.
5. **S0.5 — HTTP contract:** formalize the stable route, DTO fields, pagination, CORS, and `400`/`401`/`503` behavior.
6. **S0.6 — Angular environments:** make API and public Supabase configuration explicit without putting secrets in the bundle.
7. **S0.7 — Auth E2E:** connect local Auth, session preservation, token forwarding, and protected `/api/v1/me`.
8. **S0.8 — Real discovery (completed):** replace the discovery feed's mock source while preserving local-only saved and interest interactions until their own stories.
9. **S0.9 — Smoke gate:** run the complete local path and verify health, Auth, `/api/v1/me`, and real discovery before Sprint 1.

The compatibility route can only be deprecated after all known clients use the project-named contract, an additive replacement exists if needed, and the full smoke/regression path passes. That removal is outside S0.
