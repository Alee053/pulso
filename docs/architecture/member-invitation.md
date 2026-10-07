# Member invitation

**Status: Current** for the invitation policy in
`com.pulso.backend.project.domain.MemberInvitation`, the protected HTTP create
endpoint, and the authenticated acceptance endpoint. **Target** for email
delivery.

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

Acceptance is performed by the invitee, not by the inviter. The accepting
profile ID and email come from the validated JWT; request fields cannot choose
either identity value. The accept adapter derives the same SHA-256 digest used
at creation, locks the matching row, verifies the normalized email before
binding `profile_id`, and updates the existing row in one transaction. It
clears `invite_token_digest` when the row becomes active, so a successful token
cannot be replayed by the same or another profile.

## HTTP contract

The backend implements the protected create and acceptance endpoints. Email
delivery remains a later slice.

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
`MemberInvitation.accept`. A matching authenticated email is required before
the profile is associated. The response is the active canonical membership
representation without the token or digest.

`INVITED` rows are claimed with a conditional update inside a transaction. The
row lock and cleared digest make a retry or concurrent claim leave exactly one
active association; invalid tokens, unavailable rows, already accepted rows,
email mismatches, and profiles that already belong to the project are rejected
without changing the invitation.

## Errors

| Case | Type | Target HTTP | Message |
| --- | --- | --- | --- |
| Missing project | `MemberInvitationProjectNotFoundException` | `404` | `project does not exist.` |
| Missing or insufficient membership | `MemberInvitationPermissionException` | `403` | `active OWNER or ADMIN membership is required.` |
| Active invitation duplicate | `DuplicateMemberInvitationException` | `409` | `an active invitation already exists for this project and email.` |
| Invalid, unavailable, already accepted, or email-mismatched invitation | `MemberInvitationNotFoundException` | `404` | `invitation not found or already used.` |
| Invalid email or missing accept token | `ProjectValidationException` | `400` | Field-specific validation message |

The executable create contract is `MemberInvitation`. The acceptance use case
receives the authenticated profile and email plus the presented token; it
never accepts identity from the request body. Persistence performs the digest
lookup and conditional row update without returning token material.

## Angular invitation and acceptance

PUL-81 adds a **Mis equipos** view with loading, empty, error, retry, and
pagination states. It reads only the authenticated account's active project
memberships from `GET /api/v1/me/projects?offset=0&limit=20`. The backend derives
the profile from the validated JWT; no request parameter chooses an identity.
Results are ordered by project name and ID, with a default limit of 20, maximum
limit of 50, and offset between 0 and 100000. Limits are configured under
`pulso.current-projects`.

```json
{
  "data": [
    {
      "projectId": "33333333-3333-3333-3333-333333333333",
      "name": "EcoPack",
      "role": "OWNER",
      "membershipStatus": "ACTIVE"
    }
  ],
  "meta": { "offset": 0, "limit": 20, "hasMore": false }
}
```

The read endpoint exposes no other membership, invitation email, profile ID,
token, or digest. It requires authentication (`401` otherwise), rejects invalid
pagination (`400`), and returns an empty page when the actor has no active
memberships. It uses the existing schema without migrations. The frontend
shows the invitation form only for active owner/admin memberships. The create
endpoint remains the authorization boundary if permissions change after a read.
The form submits only the normalized invitee email, offers no role selector,
and confirms registration without promising email delivery.

The Angular acceptance entry point is the app URL with an `invitation` fragment
parameter. A future delivery adapter can construct that link; PUL-81 does not
implement delivery or return token material from creation. On application
initialization, Angular captures the token privately in memory and removes that
parameter with `history.replaceState` before initializing Auth. The token never
appears in template inputs, storage, logs, or error text. It is sent only in the
accept request body. Acceptance is offered before onboarding, with sign-in and
account switching available. Successful acceptance or dismissal clears the
in-memory token; refreshing requires reopening the original invitation link.

Acceptance `404` remains deliberately indistinguishable for an unavailable,
used, mismatched, or already-associated invitation. Angular gives combined
account-check/new-invitation guidance rather than claiming a specific cause.
Network/server failures offer an explicit retry with the same in-memory token;
if an earlier response was lost after the server accepted, the existing backend
rejects the retry without applying a second association. Changing accounts or
destroying a view cancels its pending subscription and clears account-specific
presentation state. A cancelled request may already have reached the server;
it does not undo a committed invitation or acceptance.
