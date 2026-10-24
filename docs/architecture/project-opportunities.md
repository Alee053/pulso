# Project opportunities

PUL-18 adds structured project needs to the canonical `project_opportunities`
table. Each opportunity belongs to one project and records its type, title,
description, location, remote availability, lifecycle status, creator, and
timestamps. PUL-22 adds active catalog skill associations with `REQUIRED` or
`PREFERRED` importance.

## Authorization

All opportunity management endpoints require a validated user access token.
The profile ID comes from the JWT subject. An active `OWNER` or `ADMIN`
membership on the requested project is required to list, create, edit, or change
an opportunity's status. `MEMBER`, invited, left, removed, and external users
receive the same membership-permission response. Client role, profile, project,
creator, and timestamp fields are not accepted as authority.

The same membership check protects skill listing and changes. Each write locks
the project before resolving membership and repeats the active `OWNER`/`ADMIN`
predicate in the SQL statement. Skills must be active when associated;
PostgreSQL's `(opportunity_id, skill_id)` primary key is the final duplicate
guard. Classification changes update that row and never create another link.

The repository locks the project row before resolving membership, following
the project lifecycle and membership-administration transaction order. Writes
repeat the membership predicate in SQL and compare the opportunity's expected
status and `updated_at`, so a concurrent revocation or edit cannot silently
overwrite the actor's view.

## Opportunity state

The database model defines `DRAFT`, `OPEN`, `PAUSED`, `FILLED`, `CLOSED`, and
`ARCHIVED`. PUL-18 accepts only `DRAFT` or `OPEN` as a creation state and exposes
these transitions:

| Action | Allowed source | Result |
| --- | --- | --- |
| `OPEN` | `DRAFT`, `PAUSED` | `OPEN` |
| `PAUSE` | `OPEN` | `PAUSED` |
| `CLOSE` | `OPEN`, `PAUSED` | `CLOSED` |
| `ARCHIVE` | `DRAFT`, `OPEN`, `PAUSED`, `FILLED`, `CLOSED` | `ARCHIVED` |

`ARCHIVED` is terminal. Closing sets `closed_at` on the server. Editing changes
only opportunity content and is unavailable for archived rows; project ID,
creator, lifecycle status, and creation timestamps remain server managed.

## Profile and opportunity match

`GET /api/v1/projects/{projectRef}/opportunity-matches` explains how well each
open opportunity fits the authenticated person. It lives in the `matching`
module: `OpportunityMatcher` is a pure domain function over a `MatchProfile` and
a `MatchOpportunity`, `GetOpportunityMatches` orchestrates it, and
`JdbcOpportunityMatchRepository` reads the data. Nothing is written: the match
is computed per request from `profile_preferences`, `profile_tags`,
`profile_skills`, `project_opportunities`, `opportunity_skills`, `project_tags`,
the project sector, and the profile and opportunity locations. There is no
machine learning, no embeddings, and no `match_results` table.

Only open opportunities of a published project with an active sector are
returned, with the same visibility rules as the public project detail. The
endpoint requires authentication because the result is personal; the profile
always comes from the JWT subject. The weights and the intention-to-type mapping
are domain rules in `OpportunityMatcher` and `MatchFactorKey`, documented in
[`docs/api.md`](../api.md). A factor the person has no data for keeps its weight
without credit and is reported as missing, so completing onboarding can only
raise a score; a factor the opportunity does not state is left out.

## Public-project invariant

The public project detail contract exposes a project only when its status is
`PUBLISHED` and its sector is active. An opportunity can enter `OPEN` only
while both conditions hold. The check runs in the application before the write
and in a database trigger, with project and sector locks to serialize it with
visibility changes. If a project becomes unpublished or its sector becomes
inactive, its open opportunities move to `PAUSED` in the same database
transaction. They remain paused when visibility returns and require an
authorized `OPEN` action to become public again.

`project_opportunities` is readable and writable through the backend only.
RLS is enabled and direct `anon` and `authenticated` table grants are revoked.
The browser never accesses opportunity rows through Supabase.

`opportunity_skills` follows the same backend-only boundary. Its UUID foreign
keys cascade when an opportunity is deleted and restrict deletion of a catalog
skill; deactivating a skill does not delete existing associations. Public
project details include up to the 50 most recent `OPEN` opportunities and names
of active `REQUIRED` skills. They omit opportunity/skill identifiers,
importance labels, audit fields, and all non-open opportunities.
