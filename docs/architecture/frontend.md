# Frontend architecture

## Current implementation: Vite + React prototype

The current frontend is intentionally small and local-data driven:

```text
frontend/src/
├── main.tsx                    # browser bootstrap
├── App.tsx                     # screen composition and navigation state
├── components/                 # reusable UI modules
├── hooks/usePulso.ts           # prototype interaction state and orchestration
├── data/                       # mock interests and startups
├── types/                      # TypeScript product-facing types
└── utils/recommendations.ts    # pure ranking and explanation logic
```

Current responsibilities:

- `App` selects the active screen and coordinates the selected-startup modal.
- `usePulso` owns prototype state transitions such as onboarding, saved items, and decisions.
- `data` is a temporary adapter for mock data, not a future database contract.
- `utils/recommendations.ts` is a pure policy module and should remain independent of React and browser APIs.
- UI modules receive data and callbacks through their interfaces; they should not reach into the hook's internal state.

## Target implementation: Angular by feature

When the real application replaces the prototype, keep `frontend/` as the project seam and organize the Angular source by feature rather than by a repository-wide technical bucket:

```text
frontend/src/
└── app/
    ├── core/                         # app-wide infrastructure only
    ├── shell/                        # layout, navigation, app bootstrap UI
    ├── features/
    │   ├── discovery/
    │   │   ├── data-access/           # HTTP adapters and DTO mapping
    │   │   ├── domain/                # pure feature rules and types
    │   │   └── ui/                    # feature screens and presentational modules
    │   ├── saved/
    │   └── profile/
    ├── shared/                       # proven cross-feature UI primitives only
    ├── app.routes.ts
    └── app.config.ts
└── main.ts
```

This follows Angular's guidance to organize by feature area, colocate related files, and avoid generic dumping grounds such as `helpers.ts`, `utils.ts`, or `common.ts`. See the [Angular style guide](https://angular.dev/style-guide).

## Frontend rules

### Feature ownership

- A feature owns its routes, state, data-access adapters, domain rules, and UI modules.
- `core` is for application-wide infrastructure that has one intentional instance, such as configuration, auth coordination, or telemetry. It is not a second `shared` folder.
- `shared` contains only modules used by at least two features and stable enough to deserve a common interface. Do not move code there preemptively.
- Keep feature-local code feature-local until reuse is demonstrated.

### State and data access

- Keep ephemeral UI state local to the smallest owning module.
- Keep server state in a feature data-access module; do not duplicate the same server entity in several unrelated stores.
- Keep HTTP, browser storage, and analytics behind adapters. Screens and presentational modules should consume interfaces, not concrete clients.
- Map transport DTOs to frontend domain types at the adapter seam. Do not let backend naming or nullability rules leak through every UI module.
- Use loading, empty, error, and retry states as part of the feature interface.

### UI quality and performance

- Treat keyboard access, focus management, semantic HTML, and useful labels as acceptance criteria.
- Prefer route-level and feature-level lazy loading when the application has enough feature weight to justify it.
- Avoid global state and unnecessary subscriptions; measure before adding memoization or caching.
- Keep pure calculations side-effect free so they can be tested without a browser.
- Colocate tests with the module they exercise. A feature's test should cross its public interface rather than reaching into private implementation details.

### Naming and files

- Use lower-kebab-case file names for Angular code.
- Keep a component's TypeScript, template, styles, and test together when separate files are used.
- Name modules after product language from [`CONTEXT.md`](../../CONTEXT.md), not database columns or vague technical categories.
- Avoid `utils`, `helpers`, `common`, and `misc` directories. Name a module after the behavior it owns.

## React-to-Angular migration path

1. Freeze the prototype's observable flows and capture the important ones with browser-level tests.
2. Scaffold Angular in the existing `frontend/` project seam while keeping root commands stable.
3. Migrate one vertical feature at a time: route, state, data adapter, domain policy, and UI.
4. Put all backend calls behind the new adapter interface; do not carry mock-data assumptions into production modules.
5. Run React and Angular side by side only for a bounded migration period with an explicit removal date.
6. Remove React-only dependencies and prototype modules after parity is verified.

Do not introduce a permanent dual-framework architecture. If migration risk is high, use a temporary branch or isolated migration workspace and merge only the finished feature seams.
