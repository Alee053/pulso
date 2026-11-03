# Backend architecture

## Current implementation: Spring Boot foundation

The backend uses Java 21, Spring Boot 4.1, Spring MVC, Spring Security's OAuth2
resource-server support, Spring JDBC, and PostgreSQL. Its current package shape
is:

```text
com.pulso.backend/
├── PulsoBackendApplication.java       # application bootstrap
├── config/                            # security, CORS, typed provider config
├── ops/
│   └── api/                           # liveness endpoint
├── discovery/
│   ├── api/                           # startup and current-user HTTP contracts
│   ├── application/                   # use cases and output ports
│   ├── domain/                        # transport-independent results
│   └── infrastructure/                # JDBC adapter
├── preference/
│   ├── api/                           # onboarding preference HTTP contract
│   ├── application/                   # preference use cases and output port
│   ├── domain/                        # preference rules and values
│   └── infrastructure/                # JSONB JDBC adapter
├── project/
│   ├── application/                   # draft-project use case and output port
│   ├── domain/                        # project values, team lifecycle, invitation, and membership policy
│   └── infrastructure/                # project JDBC adapter
├── profile/
    ├── api/                           # profile request/response DTOs
    ├── application/                   # profile use cases and output port
    ├── domain/                        # profile rules and values
    └── infrastructure/                # profile JDBC adapter
└── featureflag/
    ├── api/                           # allowlisted feature-flag HTTP contract
    ├── application/                   # reads and platform-admin authorization
    ├── domain/                        # stable flag keys and boolean values
    └── infrastructure/                # PostgreSQL JDBC adapter
```

The public examples are:

- `GET /api/health` — process liveness.
- `GET /api/v1/startups?limit=20` — published startup catalog.
- `GET/PATCH /api/v1/me` — authenticated product-profile contract.
- `GET/PUT /api/v1/me/preferences` — authenticated onboarding preferences.
- `GET /api/v1/feature-flags` — public read of non-sensitive availability flags.
- `PUT /api/v1/feature-flags/{key}` — authenticated update restricted to a persisted platform `ADMIN`.
- `/actuator/health` — datasource/dependency health.
- `/actuator/health/liveness` and `/actuator/health/readiness` — public process
  and dependency probes for local orchestration.

The endpoint schemas and curl examples live in [`docs/api.md`](../api.md). The
startup-named route and `Startup*` DTOs are the temporary compatibility surface
for the first vertical slice; the canonical domain/persistence naming and
cutover sequence are in [ADR 0004](../adr/0004-project-startup-compatibility.md).

The `opportunity/` feature package owns project opportunity contracts, lifecycle
rules, and JDBC persistence. Its management endpoints derive identity from the
validated JWT and read `OWNER`/`ADMIN` permissions from active project
membership. See [project opportunities](./project-opportunities.md) for the
state matrix and public-project invariant.

## Feature plus inward layers

The first real product feature is organized by business capability. Add another
feature package only when there is a real business capability, not merely
because a new table or controller exists. Spring Boot does not require this
layout; it is a deliberate choice for locality and team ownership. See
[Spring's code-structuring guidance](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html).

### `api`

- Translate HTTP requests into application inputs and application results into
  stable responses.
- Own transport DTOs, validation, status codes, and problem-detail mapping.
- Never contain business policy, SQL, transaction choreography, or provider
  calls.
- Keep controllers thin and exercise their contract with MVC slice tests.

### `application`

- Expose small use-case interfaces with meaningful inputs and results.
- Coordinate domain policies and output ports.
- Define transaction boundaries and authorization decisions for the use case.
- Do not return JDBC records or HTTP DTOs.

### `domain`

- Own product rules, invariants, value objects, and domain decisions.
- Remain independent of Spring, HTTP, PostgreSQL, and external SDKs.
- Return explicit results or domain errors; do not hide meaningful failures in
  `null`.

### `infrastructure`

- Implement output ports through adapters such as JDBC repositories, outbound
  HTTP clients, or queues.
- Translate database records at the seam.
- Own connection details, timeouts, and provider-specific failure mapping.
- Never make infrastructure types part of the domain interface.

### `config` and `ops`

- `config` wires cross-cutting concerns and typed configuration; it is not a
  general-purpose service layer.
- `ops` contains health behavior separate from product capabilities.
- `/api/health` is liveness. Actuator health is the dependency/readiness signal.

#### Structured diagnostic logging

Backend modules use [`ApplicationLogger`](../../backend/src/main/java/com/pulso/backend/ops/logging/ApplicationLogger.java)
to emit one-line JSON events with a timestamp, level, module, operation,
optional request and user identifiers, and structured metadata. The helper
redacts sensitive metadata keys and common credential, token, email, phone,
address, identity, payment, and Supabase service-role-key patterns in messages
and exception details. When a sensitive key-value assignment appears in text,
the helper redacts from that field through the end of the message or exception
string so multiword values cannot leak. Pass only operational metadata; keep
messages fixed in code and do not log request or response bodies.

For example, a module can attach identifiers and metadata without assembling a
message string:

```java
import java.util.Map;
import java.util.UUID;

import com.pulso.backend.ops.logging.ApplicationLogger;
import com.pulso.backend.ops.logging.LogContext;

final class ProjectMembershipModule {
    void reportRejectedUpdate(String requestId, UUID profileId, UUID projectId, UUID membershipId) {
        ApplicationLogger.warn(
                new LogContext("project", "updateMembership", requestId, profileId.toString()),
                "Membership update was rejected",
                Map.of("projectId", projectId.toString(), "membershipId", membershipId.toString()));
    }
}
```

For failures, use `ApplicationLogger.error(context, message, metadata,
exception)`. The helper includes the exception type, a redacted message, stack
frames, and its cause chain. The shared API exception handler uses this path
for data-store failures.

Set `PULSO_LOG_LEVEL` to `INFO` (default), `WARN`, `ERROR`, or `OFF` to control
the helper's output without changing calling modules. Request and user
identifiers are optional context supplied by the caller when available.

## Authentication boundary

Supabase Auth is the identity provider. Angular sends the access token in the
`Authorization: Bearer` header; Spring Security validates its signature, issuer,
timestamps, and audience before a protected controller receives a `Jwt`.

- Hosted Supabase: configure `SUPABASE_JWT_JWK_SET_URI` and prefer the project's
  asymmetric JWKS endpoint. Configure exactly one verification source.
- Local Supabase CLI: use the local Auth JWKS endpoint from `.env.example`; the
  current CLI issues ES256 user tokens.
- `SUPABASE_JWT_SECRET` remains available only as an explicit legacy HS256
  fallback.
- Protected user routes accept the `authenticated` JWT role and reject the
  `service_role` role.
- The server must never log or return the JWT secret or a service-role key.
- `GET /api/v1/startups` is public by design for the discovery baseline;
  `GET/PATCH /api/v1/me` and `GET/PUT /api/v1/me/preferences` are protected
  profile routes.
- Every future write use case must make its authorization rule explicit in the
  application layer. Authentication alone is not authorization.
- Feature-flag writes resolve the JWT subject to `profiles.platform_role` and
  require `ADMIN`; project `OWNER`/`ADMIN` membership does not grant global
  configuration access. The allowlisted values are stored in
  `public.feature_flags`, whose direct Supabase table grants remain revoked.

The full database/Auth choice and its trade-offs are recorded in
[ADR 0003](../adr/0003-supabase-database-and-auth.md).

## Persistence rules

- Supabase PostgreSQL is the system of record.
- The canonical schema is the ordered SQL history in
  [`database/supabase/migrations/`](../../database/supabase/migrations/).
- Use explicit JDBC queries behind an application port for the first slices;
  do not introduce JPA/Hibernate before the domain model and aggregate
  boundaries justify it.
- Application startup must not create or alter tables.
- Keep transactions around one application use case where possible and avoid
  holding a transaction across remote calls.
- Use explicit query boundaries, pagination for collections, and indexes
  justified by query patterns.
- Do not expose entities, `Row` objects, or database records directly from
  controllers.
- Keep connection pool size, query timeouts, and retry policy configurable
  through environment-backed configuration.

### Contextual connection requests

`public.connection_intents` is backend-only: row level security is enabled and
direct `anon` and `authenticated` table grants are revoked. The protected
connection-request API derives the sender from the validated JWT and creates
only `PENDING` rows. The JDBC adapter locks the active sender, publicly
available project and sector, and optional open opportunity in the creation
transaction. PostgreSQL partial unique indexes enforce one pending request per
sender and project-wide or opportunity-specific context, including concurrent
requests. Context availability uses the same published-project and active-sector
rules as public project details; an opportunity must be `OPEN` and belong to the
requested project. Request decisions, withdrawal, messaging, and notifications
remain outside the create-request use case.

The backend uses a direct or session-pooled PostgreSQL connection. A transaction
pooler should not be used for this JDBC setup when prepared statements are
enabled.

## API rules

- New product endpoints start under `/api/v1` and use resource-oriented nouns.
- Define request/response DTOs per interface. Do not reuse persistence records
  as API contracts.
- Validate at the HTTP seam, then enforce business invariants again in the
  domain when they matter.
- Collection endpoints use a bounded `offset`/`limit` window with stable
  ordering, or a cursor when offset pagination is no longer adequate.
- Use `ProblemDetail` for client errors and avoid leaking database exception
  details.
- Prefer additive contract changes. Breaking changes require a new version or
  a documented compatibility period.

## Testing strategy

- Domain tests: fast unit tests for policies and invariants with no Spring or
  database. Project publication-request and archive rules live in
  [`ProjectLifecycle`](../../backend/src/main/java/com/pulso/backend/project/domain/ProjectLifecycle.java);
  the documented matrix is [project lifecycle](./project-lifecycle.md).
  Member invitation rules live in
  [`MemberInvitation`](../../backend/src/main/java/com/pulso/backend/project/domain/MemberInvitation.java);
  the documented contract is [member invitation](./member-invitation.md).
  Membership activate, change-role, and withdraw rules live in
  [`MembershipAdministration`](../../backend/src/main/java/com/pulso/backend/project/domain/MembershipAdministration.java);
  the documented matrix is
  [membership administration](./membership-administration.md).
- Application tests: use-case tests with fake adapters at the seam.
- API tests: MVC tests for status, validation, security, and response contracts.
- Infrastructure tests: PostgreSQL-backed integration tests when repository
  adapters exist.
- Database checks: reset, lint, and SQL tests through the Supabase CLI.
- System smoke tests: `pnpm smoke:verify` starts the required local services,
  exercises health, Auth, `/api/v1/me`, and the seeded discovery journey from a
  real browser, then cleans up only its temporary application processes.

The current backend tests cover the health contract, the startup use case and
controller, and the protected `/api/v1/me` behavior. Run
`pnpm backend:verify` with local Supabase and `.env` available to exercise the
real JDBC catalog plus the available/unavailable dependency cases; it is a
local-environment check, not a substitute for tests at the application seams.
