# Working agreements

These agreements make the codebase easier to change as the team and product grow. They apply to both projects unless a project document says otherwise.

## Ownership and work shape

- Work in a feature slice where possible: contract, frontend, backend, persistence, and tests should have one visible owner and one issue.
- Keep project-specific implementation in its project. A frontend task should not edit backend internals to bypass a contract, and backend code must not depend on frontend files.
- Name work using the product vocabulary in [`CONTEXT.md`](../../CONTEXT.md), not a temporary database or UI label.
- Keep the root commands stable. Project tooling can evolve behind `pnpm dev`, `pnpm build`, `pnpm backend:test`, and the other documented commands.

## Branches, commits, and review

- Use a short-lived branch containing the Linear issue, such as `feat/pul-72-project-lifecycle`, `fix/pul-64-project-roles`, or `docs/pul-123-api-contracts`. Linear-generated branch names with a username prefix are also valid.
- Keep commits coherent and easy to revert. Follow the mandatory commit and PR title format in [`AGENTS.md`](../../AGENTS.md#commit-and-pr-titles), including the primary Linear issue as the scope.
- Follow the [PR body contract](../../AGENTS.md#pr-body-contract) and [repository template](../../.github/pull_request_template.md). The delivery workflow and client setup are in [agent workflows](./agent-workflows.md).
- Request review from the owner of every changed seam. Cross-project changes need both frontend and backend review.
- Do not merge a breaking API or schema change without a compatibility plan.
- Do not add AI attribution, credentials, generated secrets, or unrelated formatting churn.

## Definition of done

### Every change

- [ ] The implementation is within the intended module and has a small, explicit interface.
- [ ] The relevant tests or a meaningful manual check cover behavior, not only compilation.
- [ ] Errors at input, network, filesystem, and persistence seams are handled intentionally.
- [ ] Documentation and ADRs are updated when team-facing structure or a durable decision changes.
- [ ] The diff has been reviewed for security, unintended scope, and migration impact.

### Frontend change

- [ ] Loading, empty, error, retry, and accessibility behavior are defined where applicable.
- [ ] API access is behind a feature data-access adapter.
- [ ] State is owned by the smallest feature that needs it; global state has a written reason.
- [ ] Pure rules are tested without a browser.

### Backend change

- [ ] HTTP translation, use-case policy, domain rules, and infrastructure details remain in their intended modules.
- [ ] Request validation, authorization, error shape, and idempotency are explicit where relevant.
- [ ] Database changes use a reviewed migration and preserve compatibility with deployed code.
- [ ] Queries are bounded and collection endpoints are paginated.
- [ ] Tests cover the changed interface and the relevant adapter or integration path.

## Dependencies and configuration

- Prefer the existing package manager, lockfiles, wrapper, and runtime versions.
- Add a dependency only when it removes meaningful complexity or provides a required capability. Record the reason in the PR; use an ADR for lock-in choices.
- Keep secrets in environment variables or approved secret management. `.env.example` may document names and safe local shape, never real values.
- Treat environment, request, database, and provider input as untrusted. Validate identifiers and avoid shell interpolation of untrusted values.
- Keep configuration names stable and group them by concern. Fail clearly when required configuration is absent.
- When adding or extracting presentation copy, environment names, or API limits, follow the destination convention in [`configurable-values-inventory.md`](./configurable-values-inventory.md). Do not version secret values.

## API and database coordination

- Backend owns the HTTP contract; frontend consumes it through an adapter.
- Prefer additive API changes and expand/contract database migrations.
- Never make a client depend on an internal table, Java class, or persistence record.
- Define ownership for every persisted field and event before introducing shared data.

## Keeping the agreements useful

When an agreement repeatedly blocks delivery or is routinely bypassed, propose a focused change with evidence. Update this document and the relevant ADR together; do not silently create a second process in a project subfolder.
