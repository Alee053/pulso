---
name: pulso-delivery
description: Prepare Pulso commits and create or update pull requests with a resolved Linear issue, Conventional Commits titles, and the required Spanish PR body. Use whenever asked to commit, push, open a PR, or revise PR metadata in this repository.
---

# Pulso delivery

## Establish scope

1. Read `AGENTS.md` for issue resolution, title format, authorization, and the PR
   body contract. Those rules are authoritative; this skill defines the sequence.
2. Inspect `git status --short`, the current branch, remotes, and the relevant
   diff. Resolve the primary Linear issue before writing commit or PR metadata.
3. Establish the PR base from the task or repository default branch. Pulso uses
   `master`; verify the remote/default configuration rather than assuming `main`.
   Fetch the relevant remote branch when network access is available; if it is
   unavailable, disclose that the comparison uses the last local remote ref.
4. Inspect both uncommitted changes and the complete branch diff against the
   merge-base of the chosen base. Review relevant issue requirements when they
   are provided or available through an existing Linear connection. Do not invent
   acceptance criteria if the issue text cannot be read.

Scope is established when the issue, base, changed behavior, and ownership of
every file to include are understood. Do not publish unrelated working-tree
changes or commit directly on the default branch; use an issue branch.

## Prepare the change

1. Load `pulso-testing` and any implementation skill applicable to the diff.
   Run the relevant safe checks and keep exact commands, results, and limitations.
2. Review the final diff for regressions, security, secrets, scope, and required
   documentation. Run `git diff --check` for uncommitted edits and
   `git diff --check <remote>/<base>...HEAD` for existing branch commits, substituting
   verified remote/base values.
3. When commits are authorized, stage only intended paths and inspect
   `git diff --cached` before committing. Use the title contract in `AGENTS.md`;
   verify the resulting commit subject and working-tree state. Avoid blanket
   staging in a tree containing changes by others.
4. Do not retrofit historical subjects through amend/rebase/force-push. Report
   incompatible existing subjects if relevant; new commits follow the contract.

The change is ready when the scoped diff is reviewed and each required check
has an observed outcome or an explicit blocker. Preserve unresolved blockers
in the validation section; use a draft PR when it is not ready for review.

## Create or update the PR

1. Inspect existing PRs for this head branch before creating another. Use an
   available GitHub connector or `gh`; if neither is authenticated, finish the
   local title/body and report the publishing blocker without exposing tokens.
2. Write the title in the mandatory commit format. Fill
   `.github/pull_request_template.md` from the final diff and recorded evidence.
   Keep exactly `Resumen`, `Validación`, and `Alcance`; remove every placeholder.
3. With an authorized PR request, push the scoped branch normally, without
   force. Use a structured API body or a temporary UTF-8 file with `gh pr create
   --body-file <file>` / `gh pr edit --body-file <file>`. Preserve real newlines
   and avoid shell interpolation of the body. Pass the verified head/base
   explicitly when creating a PR.
4. Read the published PR back. Verify its URL, title, body, head, base, and draft
   state. Inspect remote checks if available and distinguish pending CI from
   completed success. Do not merge or deploy as part of PR creation.
5. Report the PR URL, included changes, actual validation, and outstanding work.

Delivery is complete when the requested commit/PR exists with the verified
metadata, or when a concrete external blocker is reported with the prepared
local artifact. A request for a draft body alone ends with that body and does
not publish anything.
