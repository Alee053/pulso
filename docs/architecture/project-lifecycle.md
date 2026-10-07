# Project lifecycle

**Status: Current** for the team-action policy in
`com.pulso.backend.project.domain.ProjectLifecycle` and its protected HTTP
publication-request and archive endpoints. Administrative review remains a
separate target outside S2.2.

S2.2 splits **team actions** from **administrative review**. The team can ask
for publication and can archive. Only administrative review may move a project
to `PUBLISHED`. Audit timestamps stay under server control: team actions copy
`published_at` as-is, and `archived_at` is written only when a project enters
`ARCHIVED`.

## Known statuses

| Status | Meaning | Public discovery |
| --- | --- | --- |
| `DRAFT` | Private working copy after creation | No |
| `PENDING_REVIEW` | Team requested publication; waiting for administrative review | No |
| `PUBLISHED` | Approved for public discovery | Yes |
| `REJECTED` | Administrative review declined publication | No |
| `ARCHIVED` | Team withdrew the project; terminal in this flow | No |
| `SUSPENDED` | Administrative hold; outside the S2.2 team flow | No |

Public reads already require `status = 'PUBLISHED'`: the discovery JDBC catalog
and the `Published projects are publicly readable` RLS policy. `ARCHIVED`,
`DRAFT`, `PENDING_REVIEW`, `REJECTED`, and `SUSPENDED` are therefore excluded
from public discovery without a second filter.

## Team actions

Allowed only for an **active** `OWNER` or `ADMIN` membership. `MEMBER`,
`INVITED`, `LEFT`, `REMOVED`, missing membership, unknown roles, and unknown
membership statuses are rejected with an explicit `ProjectValidationException`.

| Action | From | To | `published_at` | `archived_at` |
| --- | --- | --- | --- | --- |
| Request publication | `DRAFT` | `PENDING_REVIEW` | Unchanged. Never set to the request time. | Unchanged |
| Archive | `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED` | `ARCHIVED` | Unchanged, including a previous publication time | Set to the transition instant |

`ARCHIVED` cannot be reopened by requesting publication or archiving again.
There is no team unarchive action in this flow.

The team has no action whose result is `PUBLISHED`. Moving `PENDING_REVIEW` to
`PUBLISHED` or `REJECTED` belongs to administrative review and is out of S2.2.

The HTTP actions resolve the profile exclusively from the validated JWT subject
and join it to `project_members` using the requested `project_id`. The role and
membership status are read from that persisted row; request bodies do not accept
identity, role, target status, or review fields. Missing memberships and all
non-active/non-authorized memberships share the same `403` response so the API
does not disclose private project state.

## Transaction and concurrent membership changes

`ChangeProjectLifecycle` keeps the snapshot and update in one transaction.
`JdbcProjectLifecycleRepository.findForActor` first locks the matching project
row with `FOR UPDATE`, using the same row as membership administration. It then
reads project state and persisted membership in a separate statement, after any
competing administration transaction has committed. A missing project or
membership returns no snapshot and retains the same `403` contract.

The lock is held until the lifecycle transaction completes. A revocation or
demotion that commits first therefore prevents the waiting team action; if the
team action locks first, membership administration waits for its completion.
The update also retains the expected-status and active owner/admin predicates.
This coordination relies on membership administration sharing the project lock;
it does not authorize direct database writes to bypass that protocol.

Regression checks cover both public and transactional boundaries:

- `MembershipAdministrationTransactionTest` runs publication request and archive
  against concurrent withdrawal and demotion using the Spring-proxied services.
- `JdbcPublicProjectRepositoryTest` executes the protected HTTP actions with
  signed test JWTs and PostgreSQL, checks public feed/detail reads after each
  transition, preserves review data, and rejects forbidden bodies and actors.

These checks use isolated Testcontainers PostgreSQL with minimal schemas. They
do not validate deployed migrations, RLS, or a live Supabase browser flow.

## Administrative review (out of S2.2)

These transitions are documented so later review slices do not collide with the
team matrix. They are not implemented here.

| From | To | Notes |
| --- | --- | --- |
| `PENDING_REVIEW` | `PUBLISHED` | First publication should set `published_at` |
| `PENDING_REVIEW` | `REJECTED` | Must not invent `published_at` |
| `*` | `SUSPENDED` | Administrative hold; not a team archive |

## Errors

| Case | Message |
| --- | --- |
| Unknown status | `status contains unknown value '{value}'.` |
| Unknown role | `role contains unknown value '{value}'.` |
| Unknown membership status | `membershipStatus contains unknown value '{value}'.` |
| Missing membership | `active OWNER or ADMIN membership is required.` |
| Membership not `ACTIVE` | `membershipStatus must be ACTIVE.` |
| Role is `MEMBER` | `role must be OWNER or ADMIN.` |
| Publication request from a non-`DRAFT` status | `cannot request publication from {status}.` |
| Archive from `ARCHIVED` or `SUSPENDED` | `cannot archive from {status}.` |
| Archive without an archive instant | `archivedAt is required.` |

The executable matrix is `ProjectLifecycle`. Callers supply `archivedAt`
for archive; the policy does not read a clock of its own.
