# Pulso agent guidance

## Working in this repository

- Read [`CONTEXT.md`](CONTEXT.md) for product vocabulary and
  [`docs/engineering/working-agreements.md`](docs/engineering/working-agreements.md)
  for engineering standards. Read the relevant architecture document before
  changing a module boundary, HTTP contract, or state ownership.
- Inspect the working tree and current branch before editing. Preserve changes
  made by others and keep the diff within the requested issue.
- Use the versions and commands declared in `package.json`, `.nvmrc`,
  `frontend/package.json`, and the backend Gradle Wrapper. Preserve the lockfile.
- Frontend product data goes through backend HTTP adapters. Supabase in the
  browser is for Auth only; the backend validates JWTs and owns authorization.
- For a nontrivial change, establish the expected behavior, inspect its callers
  and tests, implement the smallest complete solution, and validate the affected
  behavior. Report actual results and remaining uncertainty.
- Commit, push, and open a PR when requested or authorized by the delivery task.
  A request to create a PR authorizes the necessary scoped commits and branch
  push. It does not authorize merging, deploying, rewriting history, or modifying
  shared data. Ask before destructive operations or material requirement changes.
- Keep secrets and personal data out of logs, documentation, commits, and PRs.
- Never add statements that work was developed or generated with AI, assistant
  signatures, attribution badges, or AI co-author trailers anywhere in project
  deliverables. Write about the change and its evidence.

## Required skills

Read the matching `SKILL.md` before performing the workflow, even if the client
does not automatically discover skills. All paths in the skills are relative to
the repository root unless stated otherwise.

| Task | Instructions |
| --- | --- |
| Create commits, prepare/open/update a PR | [pulso-delivery](.agents/skills/pulso-delivery/SKILL.md) |
| Change components, styles, forms, responsive layouts, or accessibility | [pulso-design-system](.agents/skills/pulso-design-system/SKILL.md) |
| Add/run tests, validate a change, or reproduce a regression | [pulso-testing](.agents/skills/pulso-testing/SKILL.md) |
| Change schema, SQL, RLS, persistence, or database seed data | [pulso-database](.agents/skills/pulso-database/SKILL.md) |

Load all matching skills for a task that crosses these boundaries. The setup
and client discovery rules are documented in
[`docs/engineering/agent-workflows.md`](docs/engineering/agent-workflows.md).

## Linear issue identity

Before creating a commit or PR, resolve one primary Linear issue:

1. Use the issue explicitly assigned in the current task.
2. Otherwise extract a case-insensitive `PUL-<digits>` identifier from the branch
   name (`git branch --show-current`) and normalize it to uppercase. For example,
   `alejandrocastro0520/pul-102-s26-ui-refactor` gives `PUL-102`.
3. If the identifier is missing, ambiguous, or conflicts with the task, ask for
   the correct issue before committing or publishing. Continue independent local
   work while waiting. Never invent an issue or use `PUL-000` as a fallback.

Use that same primary issue in every new commit and the PR title and scope.
Mention secondary issues in the body only when they are genuinely related.

## Commit and PR titles

Use Conventional Commits with the Linear identifier as the mandatory scope:

```text
<type>(PUL-<number>): <description>
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
`build`, `ci`, `chore`, `revert`. Here `style` means formatting without behavior
changes; a UI redesign normally uses `refactor` or `feat` according to scope.
For an intentional breaking change, use `type(PUL-123)!: description` and explain
the compatibility plan in a `BREAKING CHANGE:` footer and the PR scope.

Write a concise action description in Spanish by default, following an explicit
language preference from the user. Do not end the subject with a period.

```text
feat(PUL-72): autorizar solicitudes de publicación y archivado
fix(PUL-64): rechazar roles enviados por el cliente
docs(PUL-123): definir el flujo de revisión
```

Apply the same format to PR titles and any squash/revert commit authored by an
agent. Keep commits coherent and independently reviewable. Do not rewrite
existing commits merely to retrofit this convention.

## PR body contract

Use [`.github/pull_request_template.md`](.github/pull_request_template.md).
Every PR body has exactly these three level-two headings, in this order, in
Spanish: `Resumen`, `Validación`, `Alcance`.

- **Resumen:** Write concrete bullets describing the resulting behavior and why
  it matters. Describe the final diff; omit conversational history and abandoned
  approaches. Include the affected project when it helps locate the change.
- **Validación:** Use one bullet per check: `` `exact command` — actual result ``.
  For manual checks, state the scenario, environment, and observed result.
  Report failures, skipped checks, and blockers explicitly. Quote test counts
  only when observed; cached/up-to-date tasks are not a fresh test run. Separate
  fixture UI checks, live integration checks, local results, and remote CI status.
  If checks were not run, say so and give the reason. Never copy results from a
  previous PR or present planned commands as completed checks.
- **Alcance:** Write a short paragraph identifying the primary `PUL-<number>`,
  covered behavior, and relevant exclusions or pending work. Include migration,
  compatibility, rollback, or operational implications here when applicable.

Replace every template placeholder before publishing. Use the actual OS command
syntax; the sample's Windows commands are examples, not Linux instructions.
An unresolved check must remain visible in the PR. Local success does not prove
remote CI, deployment, or end-to-end behavior.

## Domain and database references

When a task changes database structure, domain entities, onboarding,
discovery, opportunities, messaging, or their relationships:

1. Read [`database/pulso.dbml`](database/pulso.dbml) for the current logical
   MVP model and its explicit foreign-key relationships.
2. Read [`docs/diagrams/pulso-class-diagram.mmd`](docs/diagrams/pulso-class-diagram.mmd)
   for the conceptual class and relationship overview.
3. Read the relevant SQL files under
   [`database/supabase/migrations/`](database/supabase/migrations/) before
   changing implemented persistence.

The DBML is the design reference, the Mermaid file is a readability aid, and
the ordered Supabase migrations are the source of truth for the deployed
schema. Keep the three aligned when a schema change is intentional; state any
temporary difference explicitly.
