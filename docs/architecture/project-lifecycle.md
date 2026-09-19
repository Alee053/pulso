# Project lifecycle

**Status: Current** for the team-action policy in
`com.pulso.backend.project.domain.ProjectLifecycle`. **Target** for HTTP
publication-request and archive endpoints, and for administrative review.

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
