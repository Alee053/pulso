# Administrative project review

**Status: Current.** PUL-31 implements the administrative decision after a team
requests publication. It extends the existing `project` feature; team lifecycle
actions retain their separate authorization boundary.

## Access and decisions

The backend resolves the caller from a validated, canonical UUID JWT subject.
It reads `profiles.platform_role`, `account_status`, and `deleted_at` from the
database. Only an active, non-deleted platform `MODERATOR` or `ADMIN` can list
the queue or decide. JWT claims and project membership roles do not grant
platform moderation access.

`GET /api/v1/admin/project-reviews` lists only `PENDING_REVIEW` projects, oldest
creation first with UUID as the tie breaker. The bounded page includes the
project description, value proposition, sector, stage, location, creator name,
active-owner count, and review eligibility. These private fields are available
only through the administrative endpoint. The project's creator and every
active owner may see the queue but cannot decide on their own project, even
when they have a platform moderation role.

`POST /api/v1/admin/project-reviews/{projectId}/decision` accepts:

| Decision | Result | Verification | Note |
| --- | --- | --- | --- |
| `APPROVE` | `PUBLISHED` | Explicit `VERIFIED` or `UNVERIFIED` | Optional |
| `REJECT` | `REJECTED` | Preserves the stored value | Required, nonblank |

Approval requires an active sector and at least one active owner. It sets the
first `published_at` when absent; rejection never invents a publication time.
Both decisions store the authenticated reviewer, server time, and trimmed note.
The default note limit is 2000 characters. Other source states produce `409`;
repeating a completed decision produces no second audit row.

## Transaction and audit

`ReviewProjects.decide` owns one Spring transaction. The JDBC adapter locks the
active reviewer's profile with `FOR SHARE`, locks the project with `FOR UPDATE`,
and reads a fresh membership/state snapshot. The project lock is shared with
team lifecycle and membership administration. The sector receives a share lock
until completion so it cannot be deactivated during approval.

The same transaction updates the project and appends `PROJECT_APPROVED` or
`PROJECT_REJECTED` to `admin_audit_logs`. The audit contains the actor, target
type `PROJECT`, target UUID, decision time, previous/resulting state, and basic
verification. Metadata excludes the note and submitted project content. An
audit write failure rolls back the project update. Concurrent decisions on one
project serialize; the waiting decision sees the completed state and fails.

The migration creates the audit table and queue index without rewriting prior
migrations. Audit RLS is enabled and all direct `anon`/`authenticated` grants
are revoked. The actor foreign key retains the link to the product profile.
Apply the migration before deploying the new backend. Rollback can leave the
additive table/index in place while reverting the application; audit retention
must be considered before any later schema removal.

## Team and browser surfaces

`GET /api/v1/me/projects` additively returns `status`, `verificationStatus`,
`reviewNote`, and `reviewedAt` for the caller's active teams. Angular displays
the note in **Mis equipos**, including for active members. Public project
responses keep their existing publication filter and omit review notes.

The **Perfil** page mounts `ProjectReviewsComponent` for active platform
moderators/admins. `ProjectReviewsApi` uses the backend Auth interceptor. The
component owns loading, empty, failure/retry, pagination, note validation,
verification choice, and decision state. A successful decision reloads the
first queue window because removing a row changes offset pagination. The UI
does not grant authorization and does not access Supabase product tables.

Suspension, re-submission of rejected projects, user management, documentary
verification, and notifications of administrative decisions remain separate
work. The team publication-request endpoint still cannot publish directly.
