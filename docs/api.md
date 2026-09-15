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
