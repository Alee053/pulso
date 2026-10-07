---
name: pulso-testing
description: Choose and execute meaningful Pulso validation for frontend, backend, database, or full-stack changes and record trustworthy results. Use for adding tests, reproducing regressions, checking a diff, or preparing delivery evidence.
---

# Pulso testing

## Choose checks from the changed behavior

1. Read `AGENTS.md`, the relevant existing tests, and current scripts in the root
   and frontend `package.json`, `frontend/angular.json`, and `backend/build.gradle`.
   These files are the command/toolchain source of truth.
2. For a regression, first characterize the failing path. Add a test that covers
   observable behavior at the affected boundary, including relevant failure and
   authorization cases. For documentation-only work, validate links, paths,
   examples, and diff formatting instead of running the application suites.
3. Start with targeted tests, then run the affected project's suite/build when
   justified. Avoid redundant reruns after final success unless a new edit or
   unresolved concern changes what has been validated.

## Existing entry points

Run from the repository root. Confirm prerequisites and current options first.

| Changed surface | Entry point | What to inspect |
| --- | --- | --- |
| Angular behavior | `pnpm --filter @pulso/frontend test` | Adjacent `*.spec.ts`; Angular TestBed, Vitest, HTTP testing adapters |
| Angular compilation/templates | `pnpm build` | Production output and configured Angular budgets |
| Java domain/use case/MVC/security | `sh ./backend/gradlew -p backend test --tests '<fully-qualified-test-class>'` | Affected layer and callers; existing JUnit/Spring Security patterns |
| Backend regression suite | `pnpm backend:test` | Test report counts, failures, and skipped integration tests |
| Backend compilation/package | `pnpm backend:build` | Build result; whether tests executed or were up-to-date |
| SQL/RLS | `pnpm db:lint`, `pnpm db:test` | Local Supabase prerequisites; `database/supabase/tests/database/` |
| Local service integration | `pnpm backend:verify`, `pnpm auth:verify` | Read the scripts and `README.md` first; ports, local service mode, data writes, process cleanup |
| Full browser flow | `pnpm smoke:verify` | Destructive local reset and process/service side effects described below |

On Windows use `./backend/gradlew.bat -p backend ...` in PowerShell instead of
the Unix wrapper invocation. Select an existing class for `--tests`; placeholder
commands are instructions to fill, never evidence that a check ran.

There are no root lint/typecheck scripts at present. Do not invent `pnpm lint`
or `pnpm typecheck` results. Consult current tooling if those commands are added.

## Test boundaries and environment

- Keep pure frontend policy tests independent of Angular/HTTP; use existing
  TestBed and HttpTestingController patterns for components and adapters.
- For backend changes, distinguish domain and use-case tests, MVC contract
  tests, JWT/security tests, and JDBC integration tests. Authorization needs
  persisted membership and validated identity cases, not only UI visibility.
- JDBC tests use Testcontainers; some have `disabledWithoutDocker = true`.
  Without Docker, a green Gradle result may skip the relevant persistence tests.
  Inspect `backend/build/test-results/test/` and report skips explicitly.
- `pnpm auth:verify` creates local test users; inspect its target and side effects
  before running it. Browser fixtures prove only the scenarios they simulate.
- `pnpm smoke:verify` installs dependencies, starts Supabase, runs `pnpm db:reset`,
  and restarts the local Kong container before testing. `pnpm db:reset` destroys
  local data. Use this flow only with explicit authorization for an expendable
  local database and the affected local services. A general request to validate
  or open a PR does not authorize deleting existing local data.
- Do not run linked/shared database writes or volume deletion as validation.
  Preserve existing volumes when diagnosing a broken local stack; isolation must
  account for ports, project names, credentials, and script assumptions.

## Record evidence

Record the exact command, exit result, observed test counts, skipped tests, and
environment for each relevant check. Diagnose failures before a bounded retry.
If fresh execution is needed because Gradle reports `UP-TO-DATE`, use
`--rerun-tasks` for the relevant task and record the result.

Finish with diff review and `git diff --check`. Use the PR's actual comparison
base for branch diff checks. Report health/readiness, Auth, fixture UI, real
browser flows, and remote CI according to what each check actually proves.
List pending checks with reasons; never turn incomplete checks into a pass.
