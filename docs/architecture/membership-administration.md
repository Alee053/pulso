# Membership administration

**Status: Current** for the permission matrix in
`com.pulso.backend.project.domain.MembershipAdministration` and the interface
action/error seam in
`frontend/src/app/features/discovery/domain/membership-administration.ts`.
**Current** for the HTTP activate, change-role, and withdraw endpoint.
**Target** for a membership-administration screen.

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

## Errors

| Case | Type | Target HTTP | `code` | Endpoint message | Interface message |
| --- | --- | --- | --- | --- | --- |
| Missing or insufficient membership, including invited actors | `MembershipPermissionException` | `403` | `membership_permission` | `active OWNER or ADMIN membership is required.` | Solo un integrante activo con rol owner o admin puede administrar membresías. |
| Self-elevation | `MembershipPermissionException` | `403` | `membership_permission` | `cannot elevate own role.` | No puedes elevar tu propio rol. |
| Status or identity that the action does not allow | `MembershipInvalidTransitionException` | `409` | `membership_invalid_transition` | `cannot {action} a membership with status {status}.` or `cannot activate a membership without a profile.` | Esa membresía no admite esta acción. |
| Last active owner would be removed or demoted | `MembershipOwnerInvariantException` | `409` | `membership_owner_required` | `project must keep at least one active OWNER.` | El proyecto debe conservar al menos un owner activo. |
| Concurrent membership change makes the expected row stale | `MembershipConflictException` | `409` | `membership_conflict` | `The membership changed before the action completed.` | |
| Unknown role or membership status | `ProjectValidationException` | `400` | | Field-specific validation message | |

The executable backend contract is `MembershipAdministration`. Callers supply
the actor, the target membership, the requested role when changing role, and
the project's existing memberships so the owner invariant can be checked. The
policy does not delete rows, profiles, or read a repository of its own.

The interface seam is `allowedMembershipActions`,
`membershipAdministrationError`, and `membershipAdministrationErrorFromDetail`.
It decides which controls to offer and which copy to show; it does not re-run
the transition matrix.
