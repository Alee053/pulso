# Membership administration

**Status: Current** for the permission matrix in
`com.pulso.backend.project.domain.MembershipAdministration` and the interface
action/error seam in
`frontend/src/app/features/discovery/domain/membership-administration.ts`.
**Current** for the protected roster read and HTTP activate, change-role, and
withdraw endpoints. **Current** for the administration panel under each team in
`Mis equipos`.

S2.5 formalizes how existing `project_members` rows are administered. Roles
are `OWNER`, `ADMIN`, and `MEMBER`. Membership statuses are `INVITED`,
`ACTIVE`, `LEFT`, and `REMOVED`. Invitation create/accept stays in S2.4;
this matrix does not accept an invitation and does not create a new row.

Withdrawing a membership keeps the `project_members` row and the bound
profile identity. The profile record is never deleted by these actions.

## Known values

| Field | Values |
| --- | --- |
| `role` | `OWNER`, `ADMIN`, `MEMBER` |
| `membershipStatus` | `INVITED`, `ACTIVE`, `LEFT`, `REMOVED` |

## Who can administer

Only an **active** `OWNER` or `ADMIN` membership on **that** project can
activate, change role, or withdraw another membership. `MEMBER`, `INVITED`,
`LEFT`, `REMOVED`, missing membership, and an actor from another project are
insufficient permission.

An invited membership cannot administer, even when its role is `OWNER` or
`ADMIN`.

## Allowed actions

| Actor | Activate `LEFT`/`REMOVED` | Change role of `ACTIVE` | Withdraw another | Leave (`ACTIVE` → `LEFT`) |
| --- | --- | --- | --- | --- |
| Active `OWNER` | Yes | Yes, except self-elevation | Yes → `REMOVED` | Yes, if another active owner remains |
| Active `ADMIN` | Yes | Yes, except self-elevation | Yes → `REMOVED` | Yes |
| Active `MEMBER` | No | No | No | Yes |
| `INVITED` / `LEFT` / `REMOVED` | No | No | No | No |

Self-elevation is a change where the actor and target share the same profile
and the new role ranks above the current one: `MEMBER` < `ADMIN` < `OWNER`.
An owner or admin may demote their own role when the owner invariant still
holds. Promoting someone else is allowed.

## Transitions

| Action | From | To | Identity |
| --- | --- | --- | --- |
| Activate | `LEFT`, `REMOVED` with a bound profile | `ACTIVE`, same role | `profileId` unchanged |
| Change role | `ACTIVE` | `ACTIVE`, requested role | `profileId` unchanged |
| Withdraw another | `INVITED` or `ACTIVE` | `REMOVED`, same role | `profileId` unchanged, including empty invited rows |
| Leave | own `ACTIVE` membership | `LEFT`, same role | `profileId` unchanged |

Invalid sources are rejected. Activate is not invitation accept: `INVITED` and
already-`ACTIVE` rows cannot be activated here. A `LEFT` or `REMOVED` row
without `profileId` cannot be activated. Role changes require `ACTIVE`.
Withdraw requires `INVITED` or `ACTIVE`.

## Owner invariant

A project must keep at least one **active** `OWNER`. Withdrawing or demoting
the last active owner is rejected, whether the actor is that owner or another
admin. The application locks the project row while it reads the actor,
target, and current memberships, so concurrent administration requests for the
same project cannot both approve a stale owner state.

## HTTP contract

The backend exposes an authenticated, paginated roster for active project
owners and admins:

`GET /api/v1/projects/{projectId}/members?offset=0&limit=20`

The response includes the membership ID, profile ID, role, status, and profile
display name. It includes `inviteeEmail` only while the row is `INVITED`; it
never returns `inviteTokenDigest` or other invitation token material. Pages use
stable `created_at`, `id` ordering, a default size of 20, and a maximum size of
50. Missing or insufficient project membership returns `403`.

The `Mis equipos` panel shows these controls only when the signed-in user's
persisted membership is an active owner or admin. It loads the roster on demand,
offers activation for bound `LEFT`/`REMOVED` rows, role changes for active rows,
and withdrawal for other invited/active rows. The client hides self-promotion;
the backend remains authoritative for every request and for the last-owner
invariant.

The backend implements the three actions through one authenticated endpoint.
The authenticated profile is the actor. The request body cannot set
`profileId`, `membershipStatus`, or `createdByProfileId`.

`PATCH /api/v1/projects/{projectId}/members/{memberId}`

```json
{
  "action": "activate"
}
```

```json
{
  "action": "changeRole",
  "role": "ADMIN"
}
```

```json
{
  "action": "withdraw"
}
```

`200 OK` returns the same membership row, still identified by `id` and
`profileId` when one was bound.

## MVP audit

Each accepted administration action publishes a `MembershipAdministrationAccepted`
event containing the authenticated actor profile ID, normalized action, project
ID, membership ID, and previous/next role and status. The infrastructure listener
uses `ApplicationLogger` only after the surrounding transaction commits. Rejected
actions, stale updates, failed commits, and transactions rolled back by a caller
do not emit a successful-change audit.

The one-line JSON event has module `project`, operation `administerMembership`,
actor in `userId`, and action/target/transition fields in `metadata`. It contains
no request body, JWT, invitation token or digest, invitee email, or profile data.
This is the MVP's operational audit at `INFO`, subject to `PULSO_LOG_LEVEL` and
the deployment's log retention. The logical `admin_audit_logs` table in the DBML
has not been implemented by migrations; this change does not add a persistent
audit store or a delivery guarantee across process failure after commit.

PostgreSQL integration tests exercise the Spring-proxied use case, project lock,
competing owner demotions/withdrawals, rollback, and protected fields. Testcontainers
uses an isolated disposable database; these checks do not reset the local Supabase
stack or prove a live browser flow.

## Errors

| Case | Type | Target HTTP | `code` | Endpoint message | Interface message |
| --- | --- | --- | --- | --- | --- |
| Missing or insufficient membership, including invited actors | `MembershipPermissionException` | `403` | `membership_permission` | `active OWNER or ADMIN membership is required.` | Solo un integrante activo con rol owner o admin puede administrar membresías. |
| Self-elevation | `MembershipPermissionException` | `403` | `membership_permission` | `cannot elevate own role.` | No puedes elevar tu propio rol. |
| Status or identity that the action does not allow | `MembershipInvalidTransitionException` | `409` | `membership_invalid_transition` | `cannot {action} a membership with status {status}.` or `cannot activate a membership without a profile.` | Esa membresía no admite esta acción. |
| Last active owner would be removed or demoted | `MembershipOwnerInvariantException` | `409` | `membership_owner_required` | `project must keep at least one active OWNER.` | El proyecto debe conservar al menos un owner activo. |
| Concurrent membership change makes the expected row stale | `MembershipConflictException` | `409` | `membership_conflict` | `The membership changed before the action completed.` | La membresía cambió mientras se procesaba la solicitud. Actualiza la lista e inténtalo de nuevo. |
| Unknown role or membership status | `ProjectValidationException` | `400` | | Field-specific validation message | |

The executable backend contract is `MembershipAdministration`. Callers supply
the actor, the target membership, the requested role when changing role, and
the project's existing memberships so the owner invariant can be checked. The
policy does not delete rows, profiles, or read a repository of its own.

The interface seam is `allowedMembershipActions`,
`membershipAdministrationError`, and `membershipAdministrationErrorFromDetail`.
It decides which controls to offer and which copy to show; it does not re-run
the transition matrix.
