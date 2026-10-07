The backend owns the HTTP contract. Product endpoints start under `/api/v1`;
health endpoints are operational and are not part of the product API.

Base URL during local development: `http://localhost:8080`.

## Naming and compatibility baseline

`Project`/`projects` is the canonical domain and database name described by the
[MVP model](../database/pulso.dbml) and [class diagram](./diagrams/pulso-class-diagram.mmd).
The historical foundation migration uses `startups`/`startup_members`; the
ordered S0.3 migration transforms that schema in place and aligns the seed,
database tests, and JDBC adapter with the canonical names.

The public `GET /api/v1/startups` route and its `Startup*` transport names are
an intentional compatibility contract for the first vertical slice. They stay
unchanged during Sprint 0, so an internal database rename does not break
clients. The API is the compatibility boundary; the database will not keep a
second writable model or a permanent SQL alias.

## `GET /api/v1/startups`

Lists published projects through the temporary startup-named route. This is the
first public endpoint and requires no access token.

Query parameters:

- `offset` — optional integer from `0` to `100000`; default `0`.
- `limit` — optional integer from `1` to `50`; default `20`.

Example:

```sh
curl 'http://localhost:8080/api/v1/startups?offset=0&limit=10'
```

Response `200 OK`:

```json
{
  "data": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "slug": "eco-pack",
      "name": "EcoPack Bolivia",
      "tagline": "Envases biodegradables hechos con residuos agrícolas.",
      "sector": "Sostenibilidad",
      "city": "Cochabamba",
      "stage": "MVP"
    }
  ],
  "meta": {
    "offset": 0,
    "limit": 10,
    "hasMore": false
  }
}
```

The endpoint uses `limit + 1` internally to calculate `hasMore`. The backend
returns only `limit` records and orders published rows by `published_at DESC,
id DESC`; request the next page with `offset + limit` while `hasMore` is true.

Invalid limits return `400` with a problem response:

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "limit must be between 1 and 50."
}
```

If the backend cannot read PostgreSQL, the catalog returns `503 Service
Unavailable` without exposing the database error:

```json
{
  "type": "about:blank",
  "title": "Data store unavailable",
  "status": 503,
  "detail": "The data store is temporarily unavailable."
}
```

## `GET /api/v1/projects/{projectRef}` — public project detail (PUL-74)

Returns the public detail of one canonical `Project`. `projectRef` accepts the
project UUID (`projects.id`) or its public slug (`projects.slug`). The endpoint
does not require an access token: an anonymous visitor and an authenticated
visitor receive the same representation. Authentication never unlocks private
project or membership fields in this response.

Only a project whose canonical `projects.status` is `PUBLISHED` has a public
representation. `DRAFT`, `PENDING_REVIEW`, `REJECTED`, `ARCHIVED`, and
`SUSPENDED` projects are unavailable through this contract. The lookup must
apply the publication predicate before assembling the response so an
unpublished project cannot be distinguished from a missing one.

Response `200 OK`:

```json
{
  "data": {
    "id": "33333333-3333-3333-3333-333333333333",
    "slug": "eco-pack",
    "name": "EcoPack Bolivia",
    "tagline": "Envases biodegradables hechos con residuos agrícolas.",
    "description": "Envases compostables para productores locales.",
    "valueProposition": "Reduce residuos y mejora el valor de los desechos agrícolas.",
    "sector": {
      "id": "11111111-1111-1111-1111-111111111111",
      "slug": "sostenibilidad",
      "name": "Sostenibilidad"
    },
    "stage": "MVP",
    "city": "Cochabamba",
    "department": "Cochabamba",
    "tags": [
      {
        "id": "22222222-2222-2222-2222-222222222222",
        "slug": "economia-circular",
        "name": "Economía circular"
      }
    ],
    "externalLinks": [
      {
        "type": "WEBSITE",
        "url": "https://ecopack.example",
        "label": "Sitio web"
      }
    ],
    "members": [
      {
        "profileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "displayName": "Persona Uno",
        "avatarStoragePath": "avatars/persona-uno.webp",
        "displayTitle": "Co-fundadora",
        "isFeatured": true
      }
    ]
  }
}
```

The response maps only public product data from the canonical model:

| Response field | Canonical source | Contract rule |
| --- | --- | --- |
| `id`, `slug`, `name`, `tagline`, `description`, `valueProposition`, `stage`, `city`, `department` | `projects` | `valueProposition` maps `value_proposition`; nullable descriptive and location fields remain `null`. |
| `sector` | `projects.sector_id` → `sectors` | Includes only an active sector's `id`, `slug`, and `name`; catalog administration fields are omitted. |
| `tags` | `project_tags` → `tags` | Includes only active tags' `id`, `slug`, and `name`; an empty relation is returned as `[]`. |
| `externalLinks` | `projects.external_links` | Includes only public `{type, url, label}` entries; absent links are returned as `[]`. |
| `members` | `project_members` → `profiles` | Includes only members with `membership_status = ACTIVE`, `is_public = true`, and a linked active, public profile without a soft deletion (`account_status = ACTIVE`, `profile_visibility = PUBLIC`, `deleted_at IS NULL`). |

Each public member exposes only `profileId`, `displayName`,
`avatarStoragePath`, `displayTitle`, and `isFeatured`. The contract never
returns `inviteeEmail`, `inviteTokenDigest`, membership identifiers, audit
fields, or other invitation/private data. It also excludes project
`status`, `verificationStatus`, `createdByProfileId`, review notes and review
timestamps, publication/audit timestamps, and any other internal workflow
field. The backend applies the `PUBLISHED` predicate before joining the
sector or assembling any related public data. A project whose sector is not
active is therefore unavailable, while inactive tags, private external links,
private memberships, and non-active members are omitted from the response.

Missing projects and projects that are not `PUBLISHED` return the same
response to avoid revealing whether a private project exists:

```json
{
  "type": "about:blank",
  "title": "Public project not found",
  "status": 404,
  "detail": "The project does not exist or is not publicly available."
}
```

An anonymous request, a request with a valid authenticated user token, and a
request without a token therefore share the same `200`/`404` contract. A
database failure uses the sanitized `503 Service Unavailable` problem
response documented for the other API reads.

## `GET /api/v1/catalogs`

Returns the active reference catalogs used by authenticated product flows. The
route is versioned, requires a valid user access token, and does not expose
database rows directly. Each catalog is bounded internally to 500 entries.

Ordering is stable and defined by the backend:

- sectors: `sort_order ASC, name ASC, id ASC`;
- tags: `name ASC, id ASC`;
- skills: `name ASC, id ASC`.

Only rows with `is_active = true` are returned. The client must not depend on
`sort_order`, timestamps, RLS fields, or other persistence-only columns because
those fields are intentionally absent from the HTTP contract.

```sh
curl \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  http://localhost:8080/api/v1/catalogs
```

Response `200 OK`:

```json
{
  "data": {
    "sectors": [
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "slug": "sostenibilidad",
        "name": "Sostenibilidad",
        "description": "Soluciones que reducen residuos y mejoran el uso de recursos."
      }
    ],
    "tags": [
      {
        "id": "22222222-2222-2222-2222-222222222222",
        "slug": "inteligencia-artificial",
        "name": "Inteligencia artificial",
        "type": "TOPIC"
      }
    ],
    "skills": [
      {
        "id": "33333333-3333-3333-3333-333333333333",
        "slug": "java",
        "name": "Java",
        "category": "DESARROLLO"
      }
    ]
  }
}
```

An existing catalog with no active entries is represented by an empty array;
for example, `"tags": []`. If PostgreSQL cannot be read, the endpoint returns
the same sanitized `503 Service Unavailable` `ProblemDetail` used by the
discovery catalog. Missing or invalid bearer tokens return `401 Unauthorized`.

`tags` and `skills` were already part of the canonical DBML model but were not
created by the ordered migrations before PUL-44. Migration
`20260906000000_create_tag_and_skill_catalogs.sql` closes that implementation
gap using the existing DBML fields (`id`, `slug`, `name`, `tag_type`/`category`,
`is_active`, and timestamps); it does not create profile/project relationship
tables, which belong to the stories that persist those relationships.

## `POST /api/v1/projects`

Creates a project in `DRAFT` for the authenticated user and creates that same
user's active `OWNER` membership in the same transaction. The profile ID is
read from the validated JWT subject; the request cannot provide
`createdByProfileId` or an owner identity.

```sh
curl -X POST \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "eco-pack",
    "name": "EcoPack Bolivia",
    "tagline": "Envases sostenibles",
    "description": "Proyecto de prueba.",
    "sectorId": "11111111-1111-1111-1111-111111111111",
    "city": "Cochabamba",
    "stage": "MVP"
  }' \
  http://localhost:8080/api/v1/projects
```

The request validates required text, the active `sectorId`, and the supported
project stages (`IDEA`, `PROTOTYPE`, `MVP`, `EARLY_STAGE`, `GROWING`, and
`ESTABLISHED`). `description` is optional. A successful request returns
`201 Created` with the project and its owner relation:

```json
{
  "project": {
    "id": "33333333-3333-3333-3333-333333333333",
    "slug": "eco-pack",
    "name": "EcoPack Bolivia",
    "tagline": "Envases sostenibles",
    "description": "Proyecto de prueba.",
    "sectorId": "11111111-1111-1111-1111-111111111111",
    "city": "Cochabamba",
    "stage": "MVP",
    "status": "DRAFT",
    "createdByProfileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
  },
  "owner": {
    "id": "44444444-4444-4444-4444-444444444444",
    "projectId": "33333333-3333-3333-3333-333333333333",
    "profileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "role": "OWNER",
    "membershipStatus": "ACTIVE"
  }
}
```

Invalid input, unknown or inactive sectors, and client-provided identity
fields return `400 Bad Request`. A duplicate project slug returns `409
Conflict`; other PostgreSQL failures return the sanitized `503 Service
Unavailable` response. Missing, invalid, or `service_role` bearer tokens
return `401 Unauthorized`.

## `GET /api/v1/projects/{projectId}/members` — list memberships for administration (PUL-14)

Lists project memberships for an authenticated, active project `OWNER` or
`ADMIN`. The profile identity comes from the validated JWT subject. A regular
`MEMBER`, an invited/inactive role, a user from another project, or a missing
membership receives the same `403 Membership permission` response.

```sh
curl -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  "http://localhost:8080/api/v1/projects/33333333-3333-3333-3333-333333333333/members?offset=0&limit=20"
```

The endpoint returns a bounded page (default 20, maximum 50, offset maximum
100000), ordered by membership creation time and ID. It includes each row's
membership ID, profile ID when bound, display name, role, and status. Pending
invitations include their normalized `inviteeEmail` so an owner/admin can
identify and withdraw them. Other rows return `inviteeEmail: null`. The response
never includes an invitation token digest or plaintext token.

```json
{
  "data": [
    {
      "id": "44444444-4444-4444-4444-444444444444",
      "projectId": "33333333-3333-3333-3333-333333333333",
      "profileId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      "displayName": "Persona Dos",
      "inviteeEmail": null,
      "role": "MEMBER",
      "membershipStatus": "ACTIVE"
    }
  ],
  "meta": { "offset": 0, "limit": 20, "hasMore": false }
}
```

Invalid pagination returns `400 Bad Request`; missing or invalid bearer tokens
return `401 Unauthorized`.

## `PATCH /api/v1/projects/{projectId}/members/{memberId}` — administer membership (PUL-14 / PUL-83)

Updates an existing project membership for an authenticated active `OWNER` or
`ADMIN`. An active `MEMBER` may only use `withdraw` on their own membership.
The authenticated profile is always taken from the JWT subject; the request
cannot change `profileId`, `membershipStatus`, or `createdByProfileId`.

Supported request bodies are:

```json
{ "action": "activate" }
```

```json
{ "action": "changeRole", "role": "ADMIN" }
```

```json
{ "action": "withdraw" }
```

`activate` changes a `LEFT` or `REMOVED` membership with a bound profile to
`ACTIVE`. `changeRole` changes the role of an `ACTIVE` membership. `withdraw`
changes an `INVITED` or `ACTIVE` membership to `REMOVED`, except that an
active member withdrawing their own membership becomes `LEFT`. The project
must retain at least one active `OWNER`. Administration requests for the same
project are serialized while this invariant is checked.

Response `200 OK` returns the updated membership:

```json
{
  "id": "44444444-4444-4444-4444-444444444444",
  "projectId": "33333333-3333-3333-3333-333333333333",
  "profileId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "role": "ADMIN",
  "membershipStatus": "ACTIVE",
  "createdByProfileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

Missing or insufficient membership returns `403` with code
`membership_permission`. Invalid transitions and attempts to remove or demote
the last active owner return `409` with codes
`membership_invalid_transition` or `membership_owner_required`, respectively.
Unknown action, role, or membership status returns `400`. A concurrent change
that makes the expected membership state stale returns `409` with code
`membership_conflict`.

Committed membership changes emit an `INFO` audit event through the backend's
structured logger, identifying the authenticated actor, action, project, and
membership. Rollback and rejected actions do not emit successful-change events.
See [MVP audit](architecture/membership-administration.md#mvp-audit) for fields
and log-retention limitations.

## `PATCH /api/v1/projects/{projectId}` — project information update (PUL-11 / S2.1)

Updates an existing canonical project for the authenticated project team. The
profile identity comes exclusively from the validated JWT subject, and the
server reads the requested project's `project_members` row. Only an active
`OWNER` or `ADMIN` can perform the update; the client cannot submit a role,
profile identity, or another project identifier to change that decision.

The request accepts only these editable properties:

```json
{
  "name": "EcoPack actualizado",
  "tagline": "Envases biodegradables mejorados.",
  "description": "Nueva descripción.",
  "valueProposition": "Reduce residuos y mejora el valor local.",
  "stage": "EARLY_STAGE",
  "sectorId": "11111111-1111-1111-1111-111111111111",
  "city": "Santa Cruz",
  "department": "Santa Cruz"
}
```

The operation is partial: omitted properties keep their stored values. Text
and stage values are trimmed/normalized and validated against the same project
limits and stage catalog used during creation. `name`, `tagline`, `stage`, and
`sectorId` cannot be explicitly null; the nullable descriptive fields and
`department` may be cleared with `null`. The deployed `projects.city` column
is required, so an explicitly null city is rejected while an omitted city is
preserved.

The update writes only the canonical `projects` table. Its existing
`projects_set_updated_at` trigger advances `updated_at`; `id`, `slug`,
`created_by_profile_id`, `created_at`, `status`, `verification_status`, review
data, publication data, and archival data are not assigned by the request.
Project information edits preserve membership identities, project/profile
references, roles, and membership statuses in `project_members`.

Response `200 OK`:

```json
{
  "project": {
    "id": "33333333-3333-3333-3333-333333333333",
    "slug": "eco-pack",
    "name": "EcoPack actualizado",
    "tagline": "Envases biodegradables mejorados.",
    "description": "Nueva descripción.",
    "valueProposition": "Reduce residuos y mejora el valor local.",
    "sectorId": "11111111-1111-1111-1111-111111111111",
    "stage": "EARLY_STAGE",
    "city": "Santa Cruz",
    "department": "Santa Cruz",
    "status": "DRAFT",
    "verificationStatus": "UNVERIFIED",
    "createdByProfileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "createdAt": "2026-09-01T12:00:00Z",
    "updatedAt": "2026-09-08T12:00:00Z"
  }
}
```

Unknown or protected properties, invalid values, and inactive/unknown sectors
return `400 Bad Request`. Missing memberships, members, invited/left/removed
memberships, external profiles, a different project's membership, and a
missing project all return the same `403 Membership permission` problem so the
endpoint does not disclose private project existence. Missing, invalid, or
`service_role` bearer tokens return `401 Unauthorized`. Angular calls this
contract through the feature's `ProjectsApi` HTTP adapter; it does not read
project rows directly from Supabase.

### Acceptance coverage

[PUL-11](https://linear.app/pulso-bo/issue/PUL-11/s21-editar-la-informacion-basica-de-un-proyecto)
is implemented by PUL-62 (editable contract), PUL-63 (update operation), PUL-64
(authorization), and PUL-65 (regression coverage). The current tests exercise
these acceptance criteria:

| Criterion | Test boundary |
| --- | --- |
| Active owner/admin can edit all eight information fields | [JDBC update tests](../backend/src/test/java/com/pulso/backend/project/infrastructure/JdbcProjectUpdateRepositoryTest.java) |
| Identity, creator, lifecycle/review data, and memberships are preserved while `updated_at` advances | JDBC update tests compare persisted project data and membership rows before and after the operation |
| Member, external, inactive, and another project's memberships cannot authorize an edit | JDBC update tests assert rejection with unchanged project and membership rows |
| Required fields, lengths, stage, sector, and protected properties are validated | [Domain validation](../backend/src/test/java/com/pulso/backend/project/domain/ProjectUpdatePatchTest.java), [application validation](../backend/src/test/java/com/pulso/backend/project/application/UpdateProjectInformationTest.java), and [HTTP contract tests](../backend/src/test/java/com/pulso/backend/project/api/ProjectUpdateControllerTest.java) |
| Only a valid authenticated user token reaches the update operation | [JWT/security tests](../backend/src/test/java/com/pulso/backend/project/api/ProjectUpdateSecurityTest.java) |

The JDBC checks use isolated Testcontainers PostgreSQL with a minimal schema;
they do not prove deployed migration/RLS behavior or a live Supabase browser flow.

## `POST /api/v1/projects/{projectId}/publication-request`

Requests publication review for a project in `DRAFT`. The profile identity is
always read from the validated JWT subject. The backend authorizes the action
only when the matching `project_members` row for that exact project and profile
has `role = OWNER` or `ADMIN` and `membership_status = ACTIVE`.

The request body is empty (or `{}`). It cannot provide `profileId`, `owner`,
`role`, `status`, `publishedAt`, `reviewedAt`, `reviewedByProfileId`, or
`reviewNote`. The endpoint changes only `projects.status` to `PENDING_REVIEW`;
it never publishes the project or changes review data.

Response `200 OK`:

```json
{
  "projectId": "33333333-3333-3333-3333-333333333333",
  "status": "PENDING_REVIEW"
}
```

## `POST /api/v1/projects/{projectId}/archive`

Archives a project from `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, or `REJECTED`
under the same persisted-membership authorization rule. The server sets
`archived_at`, preserves `published_at`, and returns only the project reference
and new status:

```json
{
  "projectId": "33333333-3333-3333-3333-333333333333",
  "status": "ARCHIVED"
}
```

`MEMBER`, invited, left, removed, missing, external, or wrong-project
memberships return the same `403 Membership permission` problem. Invalid
action fields return `400`; invalid lifecycle sources return `400`; a concurrent
state change returns the sanitized `409 Project state conflict`. There is no
team endpoint that accepts a direct `PUBLISHED` transition.

## `GET /api/v1/me/preferences`

Returns the onboarding preferences of the authenticated profile. The profile
ID is always derived from the validated JWT subject; there is no profile ID
parameter that a client can use to read another person's preferences. If the
profile has not saved preferences yet, the endpoint returns three empty arrays.

```sh
curl \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  http://localhost:8080/api/v1/me/preferences
```

Response `200 OK`:

```json
{
  "profileId": "11111111-1111-1111-1111-111111111111",
  "intentions": ["DISCOVER", "COLLABORATE"],
  "preferredSectors": ["sostenibilidad"],
  "preferredStages": ["MVP"]
}
```

## `PUT /api/v1/me/preferences`

Replaces all onboarding preferences for the authenticated profile. Repeating
the same request is safe and keeps one row in
`public.profile_preferences` because `profile_id` is the primary key and the
repository uses an upsert.

The request does not accept `profileId`; the server takes it from the JWT.
`preferredSectors` is optional and defaults to `[]`; the other preference
properties must be arrays, although an empty array is valid.

```sh
curl -X PUT \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "intentions": ["discover", "collaborate"],
    "preferredSectors": ["sostenibilidad"],
    "preferredStages": ["mvp"]
  }' \
  http://localhost:8080/api/v1/me/preferences
```

The backend normalizes intention and stage codes to uppercase. A preferred
sector is accepted as an active catalog UUID or slug, using the active-sector
lookup from `GET /api/v1/catalogs` (PUL-44), and is normalized to lowercase.
The supported intention codes are `DISCOVER`, `BUY`, `COLLABORATE`, `WORK`,
`INVEST`, `FIND_PARTNERS`, `FIND_CUSTOMERS`, `FIND_PROVIDERS`,
and `EXPLORE_ECOSYSTEM`. Supported project stage codes are `IDEA`,
`PROTOTYPE`, `MVP`, `EARLY_STAGE`, `GROWING`, and `ESTABLISHED`.

Each preference array accepts at most 10 items. Blank, malformed, unknown or
inactive values, and duplicates after normalization or active-sector resolution
return `400 Bad Request`:

```json
{
  "type": "about:blank",
  "title": "Invalid preferences",
  "status": 400,
  "detail": "preferredSectors contains unknown or inactive sector 'desconocido'."
}
```

Database failures return the sanitized `503 Service Unavailable` response
documented above. Missing or invalid bearer tokens return `401 Unauthorized`.

## `GET /api/v1/me`

Returns the authenticated product profile. The profile lookup always uses the
validated JWT subject; the client cannot choose a different profile ID.

```sh
curl \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  http://localhost:8080/api/v1/me
```

Response `200 OK`:

```json
{
  "subject": "user-uuid",
  "email": "person@example.com",
  "role": "authenticated",
  "audience": ["authenticated"],
  "profile": {
    "id": "user-uuid",
    "profileKind": "PERSON",
    "displayName": "Persona Uno",
    "avatarStoragePath": null,
    "shortBio": "Una biografía breve.",
    "city": "Cochabamba",
    "department": "Cochabamba",
    "profileVisibility": "PUBLIC",
    "verificationStatus": "UNVERIFIED",
    "accountStatus": "ACTIVE",
    "platformRole": "USER",
    "onboardingCompletedAt": null,
    "createdAt": "2026-09-08T12:00:00Z",
    "updatedAt": "2026-09-08T12:00:00Z"
  }
}
```

Without a valid bearer token the response is `401 Unauthorized`. The backend
does not accept a browser Supabase service-role key as an access token.

Clients should treat `401` as an expired, missing, or invalid user session and
must not retry it with a service-role key.

## `PATCH /api/v1/me`

Updates only the authenticated user's product-content fields and returns the
same response as `GET /api/v1/me`. The request uses camelCase fields:

```sh
curl -X PATCH \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Persona actualizada","profileVisibility":"MEMBERS_ONLY"}' \
  http://localhost:8080/api/v1/me
```

Editable fields are `profileKind`, `displayName`, `avatarStoragePath`,
`shortBio`, `city`, `department`, `profileVisibility`, and
`onboardingCompletedAt`. Fields that are absent or `null` remain unchanged.
Unknown fields and invalid lengths or enum values return `400 Bad Request`.
Identity, credentials, verification, account status, platform role, deletion
state, and timestamps are server-managed and cannot be changed through this
endpoint.

## Profile persistence contract for PUL-54

The product profile is stored in `public.profiles`; Supabase Auth remains the
owner of credentials, sessions, recovery tokens, and email identity data.
`profiles.id` is the immutable UUID from `auth.users` and must be derived from
the validated JWT subject on the server. The client must not choose or replace
that identifier.

The persistence fields available to the profile use case are:

- product content: `profile_kind`, `display_name`, `avatar_storage_path`,
  `short_bio`, `city`, `department`, `profile_visibility`, and
  `onboarding_completed_at`;
- server-managed state: `verification_status`, `account_status`,
  `platform_role`, `created_at`, `updated_at`, and `deleted_at`.

The direct `authenticated` database role can read only its own row and can
write only the product-content fields. It cannot delete a profile, change the
external UUID, or change server-managed state. The PUL-54 API must preserve the
same authorization rule even though the backend uses its own PostgreSQL
connection; RLS is a database defense-in-depth boundary, not a replacement for
server-side authorization.

Migration `20260907000000_complete_profiles.sql` establishes this contract and
keeps the signup trigger responsible for creating one profile per Auth user.

## CORS

The backend reads `CORS_ALLOWED_ORIGINS` as a comma-separated list of exact
browser origins. Local development defaults to `http://localhost:4200`; origins
not in the configured list receive no `Access-Control-Allow-Origin` response
header. Credentials are disabled, and the allowed methods and headers are
limited to the API methods used by the application.

## Operational endpoints

```sh
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

`/api/health` proves that the process is alive. `/actuator/health` includes the
configured datasource health and is the dependency/readiness signal. The
`liveness` probe remains `UP` when PostgreSQL is unavailable; the `readiness`
probe returns `503` when the datasource cannot be reached. Neither endpoint
should be used as a product authorization check.

## Contract evolution

Any incompatible change must update the Spring DTO/controller, the Angular
data-access adapter, this document, and their tests in the same change. Prefer
additive fields or a new versioned route; keep `/api/v1/startups` and its
startup-shaped response stable while the compatibility boundary remains active.

## Authentication configuration

The frontend session boundary is
[`SupabaseAuthGateway`](../frontend/src/app/core/auth/supabase-auth.gateway.ts).
`authInterceptor` asks it for the current access token and adds a Bearer header
when one exists. It does not add credentials to public requests when the local
Auth configuration is blank.

For local development:

1. Run `pnpm db:up` and use the local values in `.env.example`.
2. The backend verifies the current local Auth tokens through
   `http://127.0.0.1:54321/auth/v1/.well-known/jwks.json`.
3. Configure the frontend with the local publishable/anon key only for the
   browser build. The Auth panel supports registration, password sign-in, and
   sign-out; the gateway persists the session through Supabase Auth storage.
4. Run `pnpm auth:verify` for a reproducible API-boundary check using a
   temporary local user; it also confirms that no token, an invalid token, and
   the local `service_role` token all receive `401`. The verifier does not
   print or retain its key, token, password, or response files.

For a hosted project, set `SUPABASE_JWT_ISSUER`,
`SUPABASE_JWT_AUDIENCE=authenticated`, and
`SUPABASE_JWT_JWK_SET_URI=<issuer>/.well-known/jwks.json` in the deployment
secret manager. The JWKS path is preferred for asymmetric Supabase signing
keys; a shared JWT secret is retained only as an explicit legacy fallback and
must be used with an empty JWKS URI. The API accepts only JWTs with the
`authenticated` role; `service_role` is not a user access token.

The browser gets only a publishable/anon key. Database passwords, JWT secrets,
and service-role keys remain server-side.

## `POST /api/v1/projects/{projectId}/members` — create member invitation (PUL-79)

Creates an invitation for an existing project. The authenticated profile is
resolved from the JWT and must have an active `OWNER` or `ADMIN` membership in
that project. The request cannot provide the role, membership status, profile,
creator, token, or token digest.

```sh
curl -X POST \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inviteeEmail":"Ada@Example.com"}' \
  http://localhost:8080/api/v1/projects/33333333-3333-3333-3333-333333333333/members
```

The email is trimmed and lowercased before duplicate detection and
persistence. The server generates an acceptance token and stores only its
SHA-256 digest; the token and digest are never returned in the HTTP response
or written to logs. It also sends the plaintext token to the invitee through
the configured SMTP transport as a frontend link. If delivery fails, the
invitation transaction is rolled back.

Response `201 Created`:

```json
{
  "id": "55555555-5555-5555-5555-555555555555",
  "projectId": "33333333-3333-3333-3333-333333333333",
  "inviteeEmail": "ada@example.com",
  "profileId": null,
  "role": "MEMBER",
  "membershipStatus": "INVITED",
  "createdByProfileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

An unknown project returns `404` with `project does not exist.`. A missing or
insufficient project membership returns `403` with `active OWNER or ADMIN
membership is required.`. A second active invitation for the same project and
normalized email returns `409` with code `invitation_duplicate`. Invalid email
input and client-controlled fields return `400`.

## `POST /api/v1/project-members/accept` — accept member invitation (PUL-80)

Accepts an invitation for the authenticated account and activates the existing
`project_members` row. The endpoint requires a valid user JWT. The profile ID
and email are read from the validated JWT; the request cannot provide either
identity value.

```sh
curl -X POST \
  -H "Authorization: Bearer $SUPABASE_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"token":"accept-me"}' \
  http://localhost:8080/api/v1/project-members/accept
```

The backend hashes the presented token with the existing SHA-256 digest
algorithm and looks up `project_members.invite_token_digest`. It normalizes the
JWT email with the invitation rules and compares it before assigning
`profile_id`. A successful request changes the same row from `INVITED` to
`ACTIVE`, preserves `role = MEMBER`, records `joined_at`, and clears the digest
so the token cannot be replayed.

Response `200 OK`:

```json
{
  "id": "55555555-5555-5555-5555-555555555555",
  "projectId": "33333333-3333-3333-3333-333333333333",
  "inviteeEmail": "ada@example.com",
  "profileId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "role": "MEMBER",
  "membershipStatus": "ACTIVE",
  "createdByProfileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
}
```

Invalid or unavailable tokens, already accepted invitations, email mismatches,
and profiles that already have a membership return the same sanitized `404`
problem response and do not modify the row:

```json
{
  "type": "about:blank",
  "title": "Member invitation",
  "status": 404,
  "detail": "invitation not found or already used."
}
```

The conditional update is transactional and serialized by the invitation row;
concurrent acceptance attempts therefore produce at most one active profile
association. The response and errors never include the plaintext token or its
digest.
