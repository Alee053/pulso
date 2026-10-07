---
name: pulso-workflows
description: Route Pulso work to its shared repository instructions. Use when preparing commits or PRs, changing Angular UI or accessibility, adding or running tests, or changing SQL, Supabase/RLS, or JDBC persistence in Pulso.
---

# Pulso workflows

Read the repository-root `AGENTS.md` first. Locate the root with
`git rev-parse --show-toplevel` if working in a subdirectory. Read and follow
every matching shared skill before carrying out its workflow:

- Commits, pushes, PR creation or metadata: `.agents/skills/pulso-delivery/SKILL.md`.
- Components, styles, forms, responsive UI or accessibility:
  `.agents/skills/pulso-design-system/SKILL.md`.
- Tests, regression reproduction or validation: `.agents/skills/pulso-testing/SKILL.md`.
- Schema, SQL, RLS, seed data or JDBC persistence:
  `.agents/skills/pulso-database/SKILL.md`.

These are paths relative to the repository root, not this router's directory.
Open the matching files using the client's file-reading tool; references alone
do not load them. Execute the shared instructions rather than inventing a
client-specific workflow. A cross-project task may require several skills.
