# Pulso

Pulso is a monorepo for the product frontend and backend API.

## Projects

| Project | Directory | Current stack | Role |
| --- | --- | --- | --- |
| Frontend | `frontend/` | Vite + React prototype | Temporary UI while the production frontend is migrated to Angular |
| Backend | `backend/` | Java 21 + Spring Boot 4.1 + Gradle | API boundary and PostgreSQL integration point |

The checked-in frontend is a Vite/React demo. It is intentionally preserved while the future Angular application is designed and implemented.

## Prerequisites

- Node.js 24 or 26 and pnpm 11
- Java 21
- Docker Compose

The repository includes `.nvmrc` and a Gradle Wrapper for consistent local tooling. The first backend command may download the Gradle distribution and Java dependencies.

## Quick start

Install the frontend workspace dependencies:

```sh
pnpm install
```

Start PostgreSQL:

```sh
pnpm db:up
```

Run the backend API in a second terminal:

```sh
pnpm backend:run
```

Run the frontend in a third terminal:

```sh
pnpm dev
```

The development services are available at:

- Frontend: <http://localhost:5173>
- Backend health: <http://localhost:8080/api/health>
- Spring Actuator health: <http://localhost:8080/actuator/health>

Stop PostgreSQL when finished:

```sh
pnpm db:down
```

## Commands

| Command | Purpose |
| --- | --- |
| `pnpm dev` | Run the frontend development server |
| `pnpm build` | Type-check and build the frontend |
| `pnpm preview` | Preview the frontend production build |
| `pnpm backend:run` | Run the Spring Boot API |
| `pnpm backend:test` | Run backend tests |
| `pnpm backend:build` | Build the backend executable JAR |
| `pnpm db:up` | Start the local PostgreSQL container |
| `pnpm db:down` | Stop the local PostgreSQL container |
| `pnpm db:logs` | Follow PostgreSQL logs |

## Configuration

Copy `.env.example` to `.env` when you need to customize the Docker Compose database values. The backend has matching local defaults and accepts the `SPRING_DATASOURCE_*` and `SERVER_PORT` environment variables directly.

Do not commit `.env` files or real credentials.
