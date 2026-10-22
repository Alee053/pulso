# Frontend architecture

## Current implementation: Angular standalone

The frontend is an Angular 22 application in the `frontend/` project seam. It uses standalone components, strict TypeScript, Tailwind CSS for utility styling, and feature-oriented source layout.

```text
frontend/src/
├── main.ts                              # Angular browser bootstrap
├── index.html                           # document shell
├── index.css                            # global theme and shared visual primitives
└── app/
    ├── app.ts                           # application composition
    ├── app.config.ts                    # application-wide providers
    ├── core/
    │   ├── auth/                        # Supabase session gateway and interceptor
    │   └── config/                      # public API/Auth configuration
    ├── features/
    │   └── discovery/
    │       ├── application/             # session state and orchestration
    │       ├── data-access/             # mock fixture, HTTP adapter, and view-model mapper
    │       ├── domain/                  # startup types, recommendation and membership policy
    │       └── ui/                      # onboarding, feed, saved, profile, and modal UI
    └── shared/
        ├── brand-mark/                  # stable Pulso brand primitive
        └── icon/                        # small local icon surface for the prototype
```

[`PulsoSession`](../../frontend/src/app/features/discovery/application/pulso-session.ts) is the application-facing session module: it owns onboarding completion, the active view, the API-backed discovery feed, saved items, interested items, and learned preference weights. [`recommendations.ts`](../../frontend/src/app/features/discovery/domain/recommendations.ts) is a pure policy module and has no Angular or browser dependency. [`mock-startups.ts`](../../frontend/src/app/features/discovery/data-access/mock-startups.ts) remains a fixture for domain tests, not the runtime catalog source.

[`membership-administration.ts`](../../frontend/src/app/features/discovery/domain/membership-administration.ts) is the interface seam for membership administration: which actions an owner, admin, or member may be offered, and the permission, invalid-transition, and owner-invariant copy. The backend remains the source of truth for transitions.

`ProjectTeamsComponent` offers the opportunity-skill manager only for active
project owners and admins. Its feature adapter calls the authenticated
opportunity and catalog APIs; the public project detail has a separate DTO
containing only open opportunities and active required-skill names.

[`StartupsApi`](../../frontend/src/app/features/discovery/data-access/startups-api.ts) adapts the public `GET /api/v1/startups` catalog contract. Authenticated discovery and product state use [`ProjectInteractionsApi`](../../frontend/src/app/features/discovery/data-access/project-interactions-api.ts) and [`PreferencesApi`](../../frontend/src/app/features/discovery/data-access/preferences-api.ts), which read and write through the Spring API. [`SupabaseAuthGateway`](../../frontend/src/app/core/auth/supabase-auth.gateway.ts) hides the provider client, owns the current session, exposes sign-up/sign-in/sign-out operations, and returns only the current access token to data-access code. [`AuthPanelComponent`](../../frontend/src/app/core/auth/auth-panel.ts) is the small UI surface for local Auth. The global [`authInterceptor`](../../frontend/src/app/core/auth/auth.interceptor.ts) adds that token only to backend API requests without placing Auth or raw URLs in discovery UI components.

[`app-config.ts`](../../frontend/src/app/core/config/app-config.ts) is compiled
with safe local defaults by `angular.json`. The frontend build wrapper
[`build-frontend.mjs`](../../frontend/scripts/build-frontend.mjs) replaces those
values from `PULSO_API_BASE_URL`, `PULSO_SUPABASE_URL`, and
`PULSO_SUPABASE_PUBLISHABLE_KEY` for deployment builds. Only the publishable
key is allowed in the browser bundle; backend credentials are rejected by the
wrapper and are never read as frontend configuration.

The `Startup*` names in this first adapter match the temporary HTTP contract,
not the canonical domain name. The database cutover to `projects` is documented
in [ADR 0004](../adr/0004-project-startup-compatibility.md); the adapter can be
renamed only when its consumers are migrated deliberately.

The former Vite/React demo has been removed from the project. There is one frontend framework and one root development command: `pnpm dev`.

## Boundaries and dependency direction

```text
Angular UI → discovery session/application → domain policy
                                      └──→ feature data-access adapter → HTTP API

Angular HTTP interceptor → Auth gateway → Supabase Auth session
```

- UI components receive inputs and emit user intents. They do not rank startups or own the whole session.
- Application state coordinates feature workflows but does not contain template markup or browser-only rendering details.
- Domain rules accept data and return results. They should remain testable without Angular, HTTP, or a DOM.
- Data-access modules own HTTP requests, transport-to-domain mapping, and test
  fixtures. `StartupsApi` is wired into `PulsoSession`; API failures remain
  visible through the discovery error state instead of falling back to mocks.
- Supabase is an Auth dependency only in the browser. Product data goes through backend adapters so the server remains the authorization boundary.
- Shared components are intentionally small and stable. A feature-local component should move into `shared/` only after a second feature needs the same interface.

These are deep-module seams: the caller-facing surface is small, while the module hides state transitions, ranking policy, or rendering details. Keep the interface stable and the implementation local.

## Feature rules

### State

- Keep ephemeral presentation state in the smallest component that owns it.
- Keep discovery session state in the discovery application module until another feature genuinely shares it.
- Do not introduce a global store for a single feature. Add one only when state ownership and update semantics are clearer than the existing application service.
- Treat loading, empty, error, and retry states as part of every server-backed feature interface.

### Data access

- Keep HTTP clients, Auth session storage, analytics, and retry policy behind feature data-access modules. Product preferences and discovery interactions are stored by the backend; the browser does not keep a second product-data copy in local storage.
- Map transport DTOs to frontend domain types at the adapter seam. Backend field names and nullable transport details must not spread through templates.
- Keep API base URLs and public Auth configuration in the build configuration,
  never in UI components. Missing public Auth values leave the gateway
  inactive; the session gateway persists the browser session and data-access
  errors must remain visible to their caller.
- Prefer a small adapter interface that returns domain results over exposing a concrete HTTP client to every consumer.

### UI and accessibility

- Use semantic elements and meaningful labels for all interactive controls.
- Treat keyboard access, focus management, modal focus trapping, reduced-motion behavior, and visible focus states as acceptance criteria.
- Keep component templates focused on composition and user intent. Move non-trivial policy into a class, application module, or pure domain function.
- Use route-level lazy loading once feature routes exist and the feature weight justifies it.

### Visual system

- `frontend/src/index.css` owns semantic color, spacing, radius, and navigation tokens, plus stable presentation primitives: `page`, `page-header`, `panel`, `field-*`, `state-panel`, and button styles.
- Feature templates compose these primitives with Tailwind layout utilities. Feature state, events, validators, and data access remain in their existing modules.
- Dark navy/slate surfaces and sky-blue actions carry the Pulso identity. Essential text uses readable opaque colors; imagery sits beside or behind a controlled overlay rather than determining text contrast.
- Mobile discovery retains gestures on the cover and offers the same labeled actions above the bottom navigation; the card text allows vertical panning and pinch zoom. Long pages use normal document scrolling and reserve room for these controls and device safe areas.
- Project dialogs scroll as one surface, keep dismissal reachable, contain keyboard focus, and restore the opener on close. Nonessential motion is disabled throughout when reduced motion is requested.

The design research and scope are recorded in [UI/UX refactor research](../research/ui-ux-refactor.md).

### Naming and files

- Use lower-kebab-case filenames for Angular code.
- Keep a component's TypeScript, template, styles, and test together when separate files are used.
- Name folders after product behavior (`discovery`, `saved`, `profile`), not generic buckets such as `utils`, `helpers`, or `common`.
- Keep feature-local code local until reuse is demonstrated. `shared/` is a curated public surface, not a dumping ground.

## Project teams and invitations

`CurrentProjectsApi` reads the authenticated account's paginated active teams.
`ProjectTeamsComponent` owns its page, loading/error/retry state, and account
changes. `MemberInvitationFormComponent` uses the existing membership policy to
show owner/admin controls, while `MemberInvitationsApi` sends create/accept
requests through the Auth interceptor and translates server failures into safe
Spanish copy. No browser product query goes to Supabase.

`ProjectMembersComponent` owns each team's roster, pagination, inline action
confirmation, and mutation feedback. `ProjectMembersApi` consumes the protected
GET roster and existing PATCH membership contract, validates responses, and maps
recognized failures to Spanish copy. Controls and the invitation form use the
roster's persisted actor role; a member sees only the public read projection.
Successful mutations replace the row by membership ID and reload its page for
public fields while preserving other loaded pages. Own role changes update the
parent team permissions; leaving removes that team. Requests and confirmations
are cancelled when the project or authenticated account changes.

`InvitationLink` consumes the initial `invitation` or SMTP `invite` URL fragment before Auth
initialization, removes it from the location, and holds its token privately in
memory for the acceptance request. The app composes `InvitationAcceptanceComponent`
before onboarding so an invited account can authenticate, accept, or switch
accounts. These components clear stale results on identity changes while keeping
session refreshes for the same account stable. See [member invitation](./member-invitation.md)
for the HTTP contract, token lifecycle, and delivery limitation.

## Current discovery integration

The authenticated discovery feed and its profile collections use the backend API while preserving the domain and UI interfaces:

1. Map the API summary DTO to the discovery domain type without leaking transport fields into templates.
2. Model loading, empty, error, retry, and stale-data behavior in the application module.
3. Decide how much server data is needed for the full startup detail before changing the response contract.
4. Persist onboarding preferences, tag/skill selections, saved projects, feedback decisions, and learned recommendation weights through authenticated backend APIs.
5. Reload saved and interested collections for the active account and discard stale responses when the account changes.
6. Extend the response contract deliberately if the detail view needs more than the current summary.

Do not create a shared TypeScript package for the first endpoint. Generate or share contract artifacts only after repeated cross-project duplication proves that it reduces rather than increases coupling.

See the [Angular style guide](https://angular.dev/style-guide), [Angular file structure reference](https://angular.dev/reference/configs/file-structure), and [current stack](../stack.md).

## Onboarding completion and editing

The catalog adapter loads supported intentions/stages from the backend and active
sectors/tags/skills from its catalog endpoint. Interests use selected tags, as in
the existing domain model. `PulsoSession` owns the draft skill levels, validates
that each selected skill has a level, and saves with `completeOnboarding: true`.
The persisted `onboardingCompletedAt` determines whether onboarding is complete.

The profile opens the same onboarding component for later edits. Cancelling
restores the last persisted selections without a request; saving uses the same
validation and returns to the profile. Controls are locked during load/save and
responses from previous authenticated accounts are discarded. Legacy preference
calls using skill IDs remain supported by the backend; see [API](../api.md).
