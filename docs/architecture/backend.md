# Backend architecture

## Current implementation: Spring Boot bootstrap

The backend currently provides infrastructure and one operational endpoint:

```text
com.pulso.backend/
├── PulsoBackendApplication.java       # application bootstrap
└── api/
    └── HealthController.java          # API liveness contract
```

`application.yml` configures the datasource, server port, and Actuator. PostgreSQL is available locally through Compose, but there is no schema, migration, repository, domain model, or product use case yet.

## Target package structure: feature plus inward layers

The first real product feature should be organized by business capability. For example, once discovery requirements are settled:

```text
com.pulso.backend/
├── PulsoBackendApplication.java
├── config/                            # cross-cutting Spring wiring
├── ops/
│   └── api/                           # liveness, readiness, diagnostics
└── discovery/                         # one business capability
    ├── api/                           # HTTP controllers and DTOs
    ├── application/                   # use cases and ports
    ├── domain/                        # policies, entities, value objects
    └── infrastructure/                # PostgreSQL and external adapters
```

Add another feature package only when there is a real business capability, not just because a new table or controller exists. Spring Boot itself does not require a layout; this feature-oriented structure is a deliberate choice for locality and team ownership. See [Spring's code-structuring guidance](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).

## Layer responsibilities

### `api`

- Translate HTTP requests into application inputs and application results into stable responses.
- Own transport DTOs, validation annotations, status codes, and API error mapping.
- Never contain business policy, SQL, transaction choreography, or calls to external providers.
- Keep controllers thin; the controller interface should be easy to exercise with an MVC slice test.

### `application`

- Expose small use-case interfaces with meaningful inputs and results.
- Coordinate domain policies and output ports.
- Define transaction boundaries and authorization decisions for the use case.
- Do not return JDBC records or HTTP DTOs.

### `domain`

- Own product rules, invariants, value objects, and domain decisions.
- Remain independent of Spring, HTTP, PostgreSQL, logging frameworks, and external SDKs.
- Return explicit results or domain errors; do not hide meaningful failure in `null`.
- Keep the interface small and deep enough that callers do not need to recreate rules.

### `infrastructure`

- Implement output ports through adapters such as PostgreSQL repositories, outbound HTTP clients, or queues.
- Translate database and provider models at the seam.
- Own retries, timeouts, connection details, and provider-specific failure mapping.
- Never make infrastructure types part of the domain interface.

### `config` and `ops`

- `config` wires dependencies and typed configuration; it should not become a general-purpose service layer.
- `ops` contains operational endpoints and health behavior, separate from product capabilities.
- `/api/health` is liveness. Actuator health is the dependency/readiness signal; product endpoints must not be used as probes.

## Persistence rules

- PostgreSQL is the system of record once product persistence is introduced.
- Choose and record one migration tool before adding the first persistent schema. Every schema change must be forward-applied through a reviewed migration; do not rely on application startup DDL.
- Keep transactions around one application use case where possible; avoid holding a transaction across remote calls.
- Use explicit query boundaries, pagination for collections, and indexes justified by query patterns.
- Do not expose entities or database records directly from controllers.
- Keep connection pool size, query timeouts, and retry policy configurable through environment-backed configuration.

## API rules

- New product endpoints start under `/api/v1` and use resource-oriented nouns.
- Define request/response DTOs per interface. Do not reuse persistence records as API contracts.
- Validate at the HTTP seam, then enforce business invariants again in the domain when they matter.
- Return a consistent problem response for client errors and a correlation identifier for diagnosability once the API grows.
- Additive contract changes are preferred. Breaking changes require a new version or a documented compatibility period.

## Testing strategy

- Domain tests: fast unit tests for policies and invariants with no Spring or database.
- Application tests: use-case tests with fake adapters at the seam.
- API tests: MVC slice tests for status, validation, and response contracts.
- Infrastructure tests: PostgreSQL-backed integration tests when repository adapters exist.
- System smoke tests: start Compose dependencies and the application, then exercise health and one critical path.

The existing `HealthControllerTest` is intentionally only an API-slice test; the live Compose smoke test covers application startup and database wiring.
