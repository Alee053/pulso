# API examples

The backend owns the HTTP contract. Product endpoints start under `/api/v1`;
health endpoints are operational and are not part of the product API.

Base URL during local development: `http://localhost:8080`.

## `GET /api/v1/startups`

Lists published startups. This is the first public endpoint and requires no
access token.

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
returns only `limit` records and orders published rows by `created_at DESC,
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

## `GET /api/v1/me`

Returns claims extracted from the validated Supabase JWT. It is an example of a
protected endpoint, not yet a complete profile endpoint.

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
  "audience": ["authenticated"]
}
```

Without a valid bearer token the response is `401 Unauthorized`. The backend
does not accept a browser Supabase service-role key as an access token.

## Operational endpoints

```sh
curl http://localhost:8080/api/health
curl http://localhost:8080/actuator/health
```

`/api/health` proves that the process is alive. `/actuator/health` includes the
configured datasource health and is the dependency/readiness signal. Neither
endpoint should be used as a product authorization check.

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
3. Use the local Supabase Auth API to create a test user or configure the
   frontend publishable key in `frontend/src/app/core/config/app-config.ts`.

For a hosted project, set `SUPABASE_JWT_ISSUER`,
`SUPABASE_JWT_AUDIENCE=authenticated`, and
`SUPABASE_JWT_JWK_SET_URI=<issuer>/.well-known/jwks.json` in the deployment
secret manager. The JWKS path is preferred for asymmetric Supabase signing
keys; a shared JWT secret is retained only as an explicit legacy fallback and
must be used with an empty JWKS URI. The API accepts only JWTs with the
`authenticated` role; `service_role` is not a user access token.

The browser gets only a publishable/anon key. Database passwords, JWT secrets,
and service-role keys remain server-side.
