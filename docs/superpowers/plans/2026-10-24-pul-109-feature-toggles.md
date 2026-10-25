# PUL-109 Feature Toggles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and enforce the language-switcher and project-creation flags, expose safe controls to platform administrators, and apply them in Angular.

**Architecture:** PostgreSQL stores the two known booleans. Spring Boot provides a public allowlisted read contract and an authenticated write contract that checks the persisted platform role; the project creation use case reads the same flag before writing. Angular loads a central signal-backed HTTP adapter and uses it for the language switcher, project creation, and an administrator panel in the existing profile view.

**Tech Stack:** PostgreSQL/Supabase SQL migration, Java 21/Spring Boot/Spring JDBC, Angular 22/signals/HttpClient, JUnit/Testcontainers, pgTAP, Vitest.

**Spec:** [2026-10-24-pul-109-feature-toggles-design.md](../specs/2026-10-24-pul-109-feature-toggles-design.md)

## Global Constraints

- Use the issue-owned branch from Linear and primary issue `PUL-109` in every commit.
- Preserve the existing lockfile, authentication, project permissions, and the working language preference.
- Expose only `language_switcher` and `project_creation`; both start enabled.
- Only persisted `profiles.platform_role = 'ADMIN'` can change flags.
- Do not gate opportunity management or change unrelated project reads/edits.
- Write each behavior test before its implementation and observe the expected failure.

## Review Focus

- Unknown flag names cannot read or update arbitrary configuration — cover allowlisting and not-found response in the API task.
- Non-admins cannot update a flag — test persisted-role authorization and unauthenticated rejection in the API task.
- A disabled project flag causes no project or membership writes — assert repository write count in the project task.
- A flag load failure never briefly exposes a gated control — start the Angular store disabled and test failure state in the frontend tasks.
- A failed update restores the prior switch state — test rejection and state rollback in the store/panel task.

---

### Task 1: Persist flags and provide the backend application model

**Files:**
- Create: ordered migration under `database/supabase/migrations/`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/domain/FeatureFlag.java`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/domain/FeatureFlagKey.java`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/application/FeatureFlagRepository.java`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/application/FeatureFlagReader.java`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/application/GetFeatureFlags.java`
- Create: `backend/src/main/java/com/pulso/backend/featureflag/infrastructure/JdbcFeatureFlagRepository.java`
- Test: domain and JDBC feature flag tests

**Interfaces:**
- Produces a stable key allowlist, a read-only feature-flag port, and PostgreSQL-backed rows with enabled defaults.

- [ ] Write a failing test for known keys/default values and JDBC reads/updates.
- [ ] Run targeted tests and confirm the failures describe missing behavior.
- [ ] Create the migration with boolean values, primary-key/identifier constraint, RLS enabled, and direct Supabase grants revoked; seed both flags as enabled.
- [ ] Implement the domain, repository, reader, and JDBC adapter.
- [ ] Run domain/JDBC tests with Testcontainers and verify real PostgreSQL persistence.
- [ ] Commit as `feat(PUL-109): persistir las flags iniciales`.

### Task 2: Expose flag reads and admin-only updates

**Files:**
- Create: feature-flag API request/response/controller and `SetFeatureFlag` application use case
- Modify: `backend/src/main/java/com/pulso/backend/discovery/api/ApiExceptionHandler.java`
- Modify: `backend/src/main/java/com/pulso/backend/config/SecurityConfig.java`
- Test: feature-flag MVC/security/use-case tests

**Interfaces:**
- `GET /api/v1/feature-flags` returns only the two allowlisted keys and booleans.
- `PUT /api/v1/feature-flags/{key}` accepts `{"enabled": boolean}`; persisted platform admins may write, unknown keys return 404, and other authenticated profiles receive 403.

- [ ] Test public reads, invalid/unknown input, unauthenticated writes, USER/MODERATOR rejection, and ADMIN success.
- [ ] Observe the expected failures before implementing the contract.
- [ ] Implement transport mapping, use-case authorization through `ProfileRepository`, and exception responses.
- [ ] Run targeted MVC/use-case tests.
- [ ] Commit as `feat(PUL-109): exponer configuración protegida de flags`.

### Task 3: Enforce project creation availability on the backend

**Files:**
- Modify: `backend/src/main/java/com/pulso/backend/project/application/CreateDraftProject.java`
- Test: `CreateDraftProjectTest` and project controller/API error tests

**Interfaces:**
- Read `project_creation` through `FeatureFlagReader` before any project/member write.
- A disabled flag returns a stable 403 problem response and creates no records.

- [ ] Add a failing test proving a disabled flag prevents both repository writes.
- [ ] Implement the use-case guard and API problem mapping.
- [ ] Run focused tests for enabled creation and disabled creation.
- [ ] Commit as `feat(PUL-109): bloquear creación de proyectos desactivada`.

### Task 4: Add the Angular HTTP adapter and central state

**Files:**
- Create: `frontend/src/app/core/feature-flags/feature-flags-api.ts`
- Create: `frontend/src/app/core/feature-flags/feature-flags.service.ts`
- Test: API and service specs

**Interfaces:**
- Expose typed `load()` and `update(key, enabled)` methods.
- State begins disabled/uninitialized, loads from the API, updates after success, and reverts on rejected writes.

- [ ] Add failing HTTP-adapter and service tests for load, update, malformed/failed load, and failed-write rollback.
- [ ] Implement the adapter and signal-owned state.
- [ ] Run the focused frontend specs.
- [ ] Commit as `feat(PUL-109): centralizar el estado de feature toggles`.

### Task 5: Apply flags to language and project creation UI

**Files:**
- Modify: `frontend/src/app/app.ts`, `frontend/src/app/app.html`
- Modify: `frontend/src/app/features/discovery/ui/navigation-bar.ts`
- Modify: relevant language/navigation strings
- Test: app, language-switcher, and navigation specs

- [x] Add failing tests that hide the language selector and project-create entry/flow when their flags are off, preserve normal behavior when on, and avoid enabled flashes during load.
- [x] Load flags at application startup and bind the shared signal to existing controls.
- [x] Redirect an active project-create view to a safe existing view if the flag becomes disabled.
- [x] Run focused frontend specs.
- [x] Commit as `feat(PUL-109): controlar idioma y creación desde flags`.

### Task 6: Add the administrator controls in the profile view

**Files:**
- Create: `frontend/src/app/features/discovery/ui/feature-flag-settings.ts` and its strings/spec
- Modify: `frontend/src/app/features/discovery/ui/profile-view.ts` and profile template
- Test: feature settings and profile specs

- [x] Test that only persisted-profile ADMIN UI shows switches, loads actual values, saves changes, and recovers from API errors.
- [x] Implement accessible switches, loading/error/success states, and the shared state update.
- [x] Run focused frontend specs.
- [x] Commit as `feat(PUL-109): añadir controles para administradores`.

### Task 7: Align references and run final verification

**Files:**
- Modify: `database/pulso.dbml`
- Modify: `docs/diagrams/pulso-class-diagram.mmd`
- Modify: `docs/api.md`, relevant architecture page, and `database/README.md`
- Modify: `database/supabase/tests/database/` pgTAP suite

- [ ] Add pgTAP checks for the migration, initial values, allowlisted keys, and revoked direct client writes; observe failures before changing the assertions' production premise.
- [ ] Run pgTAP in the authorized local Supabase CLI environment if available.
- [ ] Run targeted backend/frontend suites, full backend/frontend suites, builds, and `git diff --check`.
- [ ] Review the complete diff for auth, persistence, and scope.
- [ ] Commit the schema model, pgTAP contract, API contract, architecture guide, and database workflow as separate scoped PUL-109 commits.
