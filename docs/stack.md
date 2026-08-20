# Final project stack

This is the baseline approved for the first development phase. “Current” means
the repository contains a working example or configuration; “Convention” means
the rule new features should follow even when the visual prototype still uses
local data.

## Projects and runtimes

| Area | Decision | Source of truth | Status |
| --- | --- | --- | --- |
| Repository | pnpm monorepo with `frontend/`, `backend/`, and `database/` | [`pnpm-workspace.yaml`](../pnpm-workspace.yaml), [`package.json`](../package.json) | Current |
| Frontend | Angular 22 standalone, TypeScript 6, Tailwind CSS 4, RxJS 7 | [`frontend/package.json`](../frontend/package.json) | Current |
| Frontend Auth | `@supabase/supabase-js`; browser uses Auth only | [`supabase-auth.gateway.ts`](../frontend/src/app/core/auth/supabase-auth.gateway.ts) | Current seam |
| Backend | Java 21, Spring Boot 4.1, Gradle Wrapper 9.4.1 | [`backend/build.gradle`](../backend/build.gradle) | Current |
| API | Stateless REST/JSON under `/api/v1`; operational endpoints stay separate | [`docs/api.md`](./api.md), [`backend/src/main/java/com/pulso/backend`](../backend/src/main/java/com/pulso/backend) | Current example |
| Database | Hosted PostgreSQL on Supabase; local Supabase stack for development | [`database/supabase/config.toml`](../database/supabase/config.toml) | Current |
| Migrations | Supabase CLI ordered SQL migrations and seed | [`database/supabase/migrations`](../database/supabase/migrations) | Current |
| Authorization | Supabase Auth issues JWTs; Spring Security validates user JWTs; Postgres RLS guards any deliberate direct Data API access | [`SecurityConfig.java`](../backend/src/main/java/com/pulso/backend/config/SecurityConfig.java), migration | Current baseline |
| Backend persistence | Spring JDBC `JdbcClient` behind feature ports; explicit SQL, no JPA/Hibernate | [`JdbcStartupCatalog.java`](../backend/src/main/java/com/pulso/backend/discovery/infrastructure/JdbcStartupCatalog.java) | Convention |
| Local orchestration | Docker through the Supabase CLI; root `compose.yaml` is intentionally not used | [`database/README.md`](../database/README.md) | Current |

The versioned minimum is Node 24 LTS or 26, Java 21, pnpm 11, Angular 22,
Spring Boot 4.1, PostgreSQL via Supabase, and the pinned Supabase CLI in the
root `package.json`. Node 25 may run some commands with warnings but is outside
the declared engine range.

## Architecture conventions

```text
Browser / Angular
  ├─ UI and feature state
  ├─ Supabase Auth session gateway
  └─ HTTP adapters + Bearer interceptor
        │ versioned JSON API
Spring Boot API
  ├─ controllers / transport DTOs
  ├─ application use cases and ports
  ├─ domain policy
  └─ JDBC infrastructure adapters
        │
Supabase PostgreSQL + Auth
  ├─ migrations and seed are owned by database/
  ├─ RLS guards deliberate direct Supabase access
  └─ backend connects with a server-side JDBC credential
```

The frontend never connects to PostgreSQL. The backend does not import the
Supabase JavaScript SDK or expose database records directly. Product endpoints
are versioned; health and Actuator endpoints are operational only.

## Implemented baseline

### Frontend

- [`app.config.ts`](../frontend/src/app/app.config.ts) registers Angular
  `HttpClient` and the Auth interceptor.
- [`SupabaseAuthGateway`](../frontend/src/app/core/auth/supabase-auth.gateway.ts)
  exposes the smallest session boundary needed by the API client.
- [`StartupsApi`](../frontend/src/app/features/discovery/data-access/startups-api.ts)
  is a typed adapter for `GET /api/v1/startups` with input validation.
- [`PulsoSession`](../frontend/src/app/features/discovery/application/pulso-session.ts)
  still owns the visual prototype using fictional mock data. Switching it to
  the API is a later vertical slice, after loading/error behavior is designed.

### Backend

- `/api/health` is liveness and `/actuator/health` reports dependency health.
- `GET /api/v1/startups?offset=0&limit=20` is public and reads published rows
  through a bounded JDBC adapter.
- `GET /api/v1/me` is protected and demonstrates Supabase JWT extraction.
- Security uses the local Supabase Auth JWKS endpoint in development and a
  configured hosted JWKS URI in deployment. An explicit HMAC secret remains a
  legacy fallback.
- Backend tests cover the use-case, API response contract, validation, and
  protected endpoint behavior without requiring a running database.

### Database

- The first migration creates `profiles`, `startups`, and `startup_members`.
- Row Level Security is enabled on all three tables; profile/membership access
  is current-user scoped, and startup rows are not granted directly to the
  public Data API in this base.
- `seed.sql` inserts two fictional published startups for local endpoint
  examples.
- `database/README.md` defines reset, lint, test, linking, and push commands.

## What is intentionally not in this base

- Full signup/login UI and password recovery flows.
- Complete product features from the specification (profiles, submissions,
  matching, messaging, notifications, moderation, and analytics).
- Storage, realtime, Edge Functions, background jobs, deployment manifests, or
  hosted project credentials.
- A shared Java/TypeScript model package or generated OpenAPI client. The first
  endpoint is small enough to keep contracts explicit at each seam.
- Production observability, rate limiting, CI policy, and environment-specific
  deployment configuration.

These are deliberate follow-ups, not missing pieces of the minimum stack.
