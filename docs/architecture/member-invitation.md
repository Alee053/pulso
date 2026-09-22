# Member invitation

**Status: Current** for the invitation policy in
`com.pulso.backend.project.domain.MemberInvitation`. **Target** for HTTP create
and accept endpoints, email delivery, and token lookup by digest.

S2.4 uses the canonical `project_members` row as both membership and
invitation. There is no separate invitation table. An invitation identifies a
project, stores a normalized `invitee_email`, and keeps `profile_id` empty until
acceptance. The plaintext accept token is generated for the delivery flow and
is persisted only as `invite_token_digest`.

## Canonical row

| Field | Create | After accept |
| --- | --- | --- |
| `project_id` | Required. Identifies the project. | Unchanged |
| `invitee_email` | Required. Trimmed and lowercased | Unchanged |
| `profile_id` | Empty | Bound to the accepting profile |
| `role` | `MEMBER`, unless a later product rule says otherwise | Unchanged |
| `membership_status` | `INVITED` | `ACTIVE` |
| `created_by_profile_id` | The inviting active `OWNER` or `ADMIN` | Unchanged |
| `invite_token_digest` | SHA-256 hex of the accept token. Persistence only; not a field on the public invitation | Cleared by the later accept adapter |
| Accept token | Passed into `MemberInvitation.invite` so it can be hashed. Never stored on the invitation. Never returned by create | Presented by the invitee; looked up by digest |

`created_by_profile_id` is taken from the actor's profile. Callers cannot supply
a different creator. The row keeps its foreign key to `projects`. The conceptual
class diagram omits `invite_token_digest` on purpose; the digest lives only in
DBML, migrations, and the persistence result `CreatedMemberInvitation`.

## Active duplicate

An **active invitation duplicate** exists when the same `project_id` already
has a `project_members` row with `membership_status = INVITED` and the same
normalized `invitee_email`.

`LEFT`, `REMOVED`, and `ACTIVE` rows with that email do not block a new
invitation. Matching an existing active member by Auth email is out of this
slice; the later accept adapter still has to honor the unique
`(project_id, profile_id)` key.

The database enforces the active-duplicate rule with a partial unique index on
`(project_id, invitee_email)` for `INVITED` rows. Emails are stored already
normalized.

## Permissions

Creating an invitation requires an **active** `OWNER` or `ADMIN` membership on
**that** project. The actor carries the project id of the membership used to
authorize the invite. `MEMBER`, `INVITED`, `LEFT`, `REMOVED`, missing
membership, and an actor from another project are insufficient permission.

The later create use case looks up the project first. If no row exists, it
calls `invite` with `projectExists = false`, which raises
`MemberInvitationProjectNotFoundException`. A null project id is a validation
error, not a missing project.

Acceptance is performed by the invitee, not by the inviter. This slice binds
`profile_id` once a valid invited row has been selected; verifying the
presented token against `invite_token_digest` belongs to the later accept
adapter.

## Target HTTP contract

These routes are not implemented here. They are the contract later S2.4 slices
must keep.

### Create

`POST /api/v1/projects/{projectId}/members`

The authenticated profile is the creator. The request body cannot set
`createdByProfileId`, `role`, `membershipStatus`, `profileId`, or any token
field.

```json
{
  "inviteeEmail": "Ada@Example.com"
}
```

`201 Created` returns the invitation without a token or digest:

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

`MemberInvitation.invite` hashes the accept token and returns the digest only
on `CreatedMemberInvitation` for the persistence adapter. The HTTP create
response must omit both the plaintext token and `inviteTokenDigest`. The
plaintext token may be handed only to the delivery adapter. It must not appear
in this response, in logs, or in `project_members`.

### Accept

`POST /api/v1/project-members/accept`

```json
{
  "token": "accept-me"
}
```

The adapter hashes the token, loads the `INVITED` row by digest, and then calls
`MemberInvitation.accept`.

## Errors

| Case | Type | Target HTTP | Message |
| --- | --- | --- | --- |
| Missing project | `MemberInvitationProjectNotFoundException` | `404` | `project does not exist.` |
| Missing or insufficient membership | `MemberInvitationPermissionException` | `403` | `active OWNER or ADMIN membership is required.` |
| Active invitation duplicate | `DuplicateMemberInvitationException` | `409` | `an active invitation already exists for this project and email.` |
| Invalid email or accept token | `ProjectValidationException` | `400` | Field-specific validation message |

The executable contract is `MemberInvitation`. Callers supply whether the
project exists, the actor for that project, the accept token, and the project's
existing memberships; the policy hashes the token and does not read a clock or
a repository of its own.
