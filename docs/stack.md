# Current stack

## Status

This page describes the repository as it exists today. Items marked **Target** are team conventions for the next development phase; they are not claims that the implementation already exists.

## Projects and runtimes

| Area | Current choice | Source of truth | Status |
| --- | --- | --- | --- |
| Repository | pnpm monorepo with `frontend/` and `backend/` projects | [`pnpm-workspace.yaml`](../pnpm-workspace.yaml), root [`package.json`](../package.json) | Current |
| Frontend | Angular 22 standalone components, TypeScript 6, Tailwind CSS 4, RxJS 7 | [`frontend/package.json`](../frontend/package.json), [`frontend/angular.json`](../frontend/angular.json) | Current example |
| Backend | Java 21, Spring Boot 4.1, Gradle Wrapper 9.4.1 | [`backend/build.gradle`](../backend/build.gradle), [`gradle-wrapper.properties`](../backend/gradle/wrapper/gradle-wrapper.properties) | Current bootstrap |
| Database | PostgreSQL 16 Alpine for local development | [`compose.yaml`](../compose.yaml) | Current infrastructure |
| Browser-to-backend seam | HTTP/JSON; the Angular example currently uses local mock data | [`frontend/src/app/features/discovery/data-access/`](../frontend/src/app/features/discovery/data-access), [`backend/src/main/java/`](../backend/src/main/java) | Target integration |

Spring Boot's official guidance recommends a root application package and domain-oriented packages, which is the direction defined in [backend architecture](./architecture/backend.md). Angular's official style guide recommends feature-oriented structure and colocated related files, which informs the current frontend layout. See [Spring code structure](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html), [Angular style guide](https://angular.dev/style-guide), and [Angular file structure](https://angular.dev/reference/configs/file-structure).

## What is actually implemented

### Frontend

- The Angular entry point is [`frontend/src/main.ts`](../frontend/src/main.ts).
- [`frontend/src/app/app.ts`](../frontend/src/app/app.ts) composes onboarding, discovery, saved, and profile views.
- [`frontend/src/app/features/discovery/application/pulso-session.ts`](../frontend/src/app/features/discovery/application/pulso-session.ts) owns session state and ranking orchestration.
- Discovery domain rules are pure TypeScript in [`frontend/src/app/features/discovery/domain/recommendations.ts`](../frontend/src/app/features/discovery/domain/recommendations.ts), with colocated tests.
- Startup and interest data are local mock data. There is no HTTP client or server-state cache yet.
- `ng test` runs the Vitest-backed Angular test target; the recommendation policy has initial coverage.

### Backend

- [`PulsoBackendApplication`](../backend/src/main/java/com/pulso/backend/PulsoBackendApplication.java) bootstraps Spring Boot.
- [`HealthController`](../backend/src/main/java/com/pulso/backend/api/HealthController.java) provides the API liveness endpoint at `/api/health`.
- Spring Actuator provides dependency health at `/actuator/health`.
- PostgreSQL connectivity is configured, but there are no domain tables, migrations, repositories, or business endpoints yet.
- The existing backend test is an HTTP MVC slice test for the health contract.

## Local development interface

Use the root commands so project-specific tool details stay behind a small team interface:

| Command | Purpose |
| --- | --- |
| `pnpm install` | Install the frontend workspace |
| `pnpm dev` | Run the Angular frontend example |
| `pnpm build` | Type-check and build the frontend |
| `pnpm db:up` | Start PostgreSQL and wait for health |
| `pnpm backend:run` | Load local environment and run Spring Boot |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend artifact |

The backend runner accepts the safe, unquoted `.env` format documented in the root [`README.md`](../README.md). Credentials must come from the environment; never add real values to tracked files.

## Known gaps before product development

- Connect the Angular discovery data-access seam to the first versioned backend endpoint.
- Establish the first product feature and its API contract before adding shared client/server types.
- Choose and record a database migration tool before introducing persistent schema.
- Add frontend tests, API integration tests, and CI checks before treating the application as production-ready.
- Define authentication, authorization, error format, observability, and deployment separately from this bootstrap.
