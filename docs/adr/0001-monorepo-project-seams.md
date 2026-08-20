# Keep frontend and backend as two projects in one monorepo

**Status: accepted.** Pulso keeps `frontend/` and `backend/` in one repository while preserving separate build tools, dependency graphs, and runtime responsibilities. This gives the team one place for coordinated feature changes and shared development commands without coupling browser code to Java or PostgreSQL internals; the HTTP contract remains the seam between projects.

## Considered options

- **Separate repositories:** stronger repository isolation, but slower coordination for changes that must update a contract and both clients.
- **One combined application project:** simpler top-level navigation, but it would blur runtime ownership and encourage cross-layer imports.

## Consequences

- Root documentation and commands are shared, while project implementation remains isolated.
- A frontend/backend feature may be reviewed together, but each project keeps its own tests and dependency management.
- A future deployment may build and release the projects independently without requiring a repository split first.
