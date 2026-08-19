# Current stack

## Status

This page describes the repository as it exists today. Items marked **Target** are team conventions for the next development phase; they are not claims that the implementation already exists.

## Projects and runtimes

| Area | Current choice | Source of truth | Status |
| --- | --- | --- | --- |
| Repository | pnpm monorepo with `frontend/` and `backend/` projects | [`pnpm-workspace.yaml`](../pnpm-workspace.yaml), root [`package.json`](../package.json) | Current |
| Frontend | Vite 8, React 19, TypeScript 7, Tailwind CSS 4, Framer Motion, Lucide React | [`frontend/package.json`](../frontend/package.json) | Current prototype |
| Frontend target | Angular application replacing the prototype in `frontend/` | [Frontend architecture](./architecture/frontend.md) | Target |
| Backend | Java 21, Spring Boot 4.1, Gradle Wrapper 9.4.1 | [`backend/build.gradle`](../backend/build.gradle), [`gradle-wrapper.properties`](../backend/gradle/wrapper/gradle-wrapper.properties) | Current bootstrap |
| Database | PostgreSQL 16 Alpine for local development | [`compose.yaml`](../compose.yaml) | Current infrastructure |
| Browser-to-backend seam | HTTP/JSON; the prototype does not call the backend yet | [`frontend/src/`](../frontend/src), [`backend/src/main/java/`](../backend/src/main/java) | Target integration |

Spring Boot's official guidance recommends a root application package and domain-oriented packages, which is the direction defined in [backend architecture](./architecture/backend.md). Angular's official style guide recommends feature-oriented structure and colocated related files, which informs the future frontend layout. See [Spring code structure](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html), [Angular style guide](https://angular.dev/style-guide), and [Angular file structure](https://angular.dev/reference/configs/file-structure).

## What is actually implemented

### Frontend

- The Vite entry point is [`frontend/src/main.tsx`](../frontend/src/main.tsx).
- [`frontend/src/App.tsx`](../frontend/src/App.tsx) composes onboarding, discovery, saved, and profile views.
- [`frontend/src/hooks/usePulso.ts`](../frontend/src/hooks/usePulso.ts) owns prototype interaction state and ranking orchestration.
- Startup and interest data are local mock data. There is no HTTP client or server-state cache yet.
- No frontend test runner is configured yet; production feature work must add tests with the first real feature.

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
| `pnpm dev` | Run the current frontend prototype |
| `pnpm build` | Type-check and build the frontend |
| `pnpm db:up` | Start PostgreSQL and wait for health |
| `pnpm backend:run` | Load local environment and run Spring Boot |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend artifact |

The backend runner accepts the safe, unquoted `.env` format documented in the root [`README.md`](../README.md). Credentials must come from the environment; never add real values to tracked files.

## Known gaps before product development

- Replace the prototype UI with Angular while preserving the root development interface.
- Establish the first product feature and its API contract before adding shared client/server types.
- Choose and record a database migration tool before introducing persistent schema.
- Add frontend tests, API integration tests, and CI checks before treating the application as production-ready.
- Define authentication, authorization, error format, observability, and deployment separately from this bootstrap.
