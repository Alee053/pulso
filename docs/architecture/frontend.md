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
    ├── features/
    │   └── discovery/
    │       ├── application/             # session state and orchestration
    │       ├── data-access/             # current local mock adapter
    │       ├── domain/                  # startup types and pure recommendation policy
    │       └── ui/                      # onboarding, feed, saved, profile, and modal UI
    └── shared/
        ├── brand-mark/                  # stable Pulso brand primitive
        └── icon/                        # small local icon surface for the prototype
```

The current flow is deliberately local-data driven. [`PulsoSession`](../../frontend/src/app/features/discovery/application/pulso-session.ts) is the application-facing session module: it owns onboarding completion, the active view, discovery feed, saved items, interested items, and learned preference weights. [`recommendations.ts`](../../frontend/src/app/features/discovery/domain/recommendations.ts) is a pure policy module and has no Angular or browser dependency. [`mock-startups.ts`](../../frontend/src/app/features/discovery/data-access/mock-startups.ts) is an adapter for fictional data, not a future database contract.

The former Vite/React demo has been removed from the project. There is one frontend framework and one root development command: `pnpm dev`.

## Boundaries and dependency direction

```text
Angular UI → discovery session/application → domain policy
                                      └──→ data-access adapter
```

- UI components receive inputs and emit user intents. They do not rank startups or own the whole session.
- Application state coordinates feature workflows but does not contain template markup or browser-only rendering details.
- Domain rules accept data and return results. They should remain testable without Angular, HTTP, or a DOM.
- Data-access modules own the transition from mock data today to HTTP DTO mapping later.
- Shared components are intentionally small and stable. A feature-local component should move into `shared/` only after a second feature needs the same interface.

These are deep-module seams: the caller-facing surface is small, while the module hides state transitions, ranking policy, or rendering details. Keep the interface stable and the implementation local.

## Feature rules

### State

- Keep ephemeral presentation state in the smallest component that owns it.
- Keep discovery session state in the discovery application module until another feature genuinely shares it.
- Do not introduce a global store for a single feature. Add one only when state ownership and update semantics are clearer than the existing application service.
- Treat loading, empty, error, and retry states as part of every server-backed feature interface.

### Data access

- Keep HTTP clients, browser storage, analytics, and retry policy behind feature data-access modules.
- Map transport DTOs to frontend domain types at the adapter seam. Backend field names and nullable transport details must not spread through templates.
- Keep API base URLs and credentials in configuration, never in UI components.
- Prefer a small adapter interface that returns domain results over exposing a concrete HTTP client to every consumer.

### UI and accessibility

- Use semantic elements and meaningful labels for all interactive controls.
- Treat keyboard access, focus management, modal focus trapping, reduced-motion behavior, and visible focus states as acceptance criteria.
- Keep component templates focused on composition and user intent. Move non-trivial policy into a class, application module, or pure domain function.
- Use route-level lazy loading once feature routes exist and the feature weight justifies it.

### Naming and files

- Use lower-kebab-case filenames for Angular code.
- Keep a component's TypeScript, template, styles, and test together when separate files are used.
- Name folders after product behavior (`discovery`, `saved`, `profile`), not generic buckets such as `utils`, `helpers`, or `common`.
- Keep feature-local code local until reuse is demonstrated. `shared/` is a curated public surface, not a dumping ground.

## Next frontend seam: real API integration

The next vertical slice should replace the mock data-access implementation while preserving the domain and UI interfaces:

1. Define the first `/api/v1` request and response contract with the backend.
2. Add a discovery HTTP adapter and DTO-to-domain mapper under `features/discovery/data-access/`.
3. Model loading, empty, error, retry, and stale-data behavior in the application module.
4. Add adapter and component tests at the public interfaces.
5. Remove mock data only after the real endpoint has a verified local integration path.

Do not create a shared TypeScript package for the first endpoint. Generate or share contract artifacts only after repeated cross-project duplication proves that it reduces rather than increases coupling.

See the [Angular style guide](https://angular.dev/style-guide), [Angular file structure reference](https://angular.dev/reference/configs/file-structure), and [current stack](../stack.md).
