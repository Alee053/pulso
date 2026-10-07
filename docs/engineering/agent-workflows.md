# Agent workflows

## Shared instructions

[`AGENTS.md`](../../AGENTS.md) owns the mandatory issue resolution, commit/PR
titles, PR body contract, and repository boundaries. Detailed workflows live in
`.agents/skills/`; read the relevant file before performing that task.

| Workflow | Canonical skill |
| --- | --- |
| Commits and PR delivery | [pulso-delivery](../../.agents/skills/pulso-delivery/SKILL.md) |
| Frontend design and accessibility | [pulso-design-system](../../.agents/skills/pulso-design-system/SKILL.md) |
| Tests and validation evidence | [pulso-testing](../../.agents/skills/pulso-testing/SKILL.md) |
| Database and persistence | [pulso-database](../../.agents/skills/pulso-database/SKILL.md) |

The design skill follows the existing CSS tokens and components; it does not
install a new component library. The testing skill selects existing checks and
documents their side effects. The delivery skill uses the
[GitHub PR template](../../.github/pull_request_template.md) and verifies the
published metadata. Follow the referenced policies when editing these workflows
instead of maintaining separate copies of the rules.

## Client entry points

| Client | Repository instructions | Skill discovery |
| --- | --- | --- |
| Codex | Root `AGENTS.md` | Four canonical skills in `.agents/skills/` |
| OpenCode | Root `AGENTS.md` | `.agents/skills/`; the optional Claude router may also appear |
| Claude Code | Root `CLAUDE.md` imports `AGENTS.md` | `.claude/skills/pulso-workflows/SKILL.md` routes to the four canonical files |

`CLAUDE.md` contains only `@AGENTS.md`, so policy changes have one home. The
Claude router has its own name to avoid duplicate skill names in OpenCode.
All files are regular Markdown files; this setup needs no symlinks, global
installation, client-specific permissions, or additional dependencies.

Codex can select an individual skill, such as `$pulso-delivery`. In Claude Code,
use `/pulso-workflows` or describe the task to trigger the router. OpenCode loads
the matching skill through its native skill tool. If automatic discovery is
unavailable, the required-skills table in `AGENTS.md` instructs the agent to open
the same canonical files directly. Start a new session after adding skills if
the client's current list has not refreshed.

Discovery/import conventions were checked against the official
[Codex skills documentation](https://learn.chatgpt.com/docs/build-skills),
[OpenCode skills](https://opencode.ai/docs/skills/) and
[rules](https://opencode.ai/docs/rules/), and Claude Code
[skills](https://code.claude.com/docs/en/skills) and
[instruction imports](https://code.claude.com/docs/en/memory#share-one-file-with-other-coding-tools).
Client configuration can disable project instructions or skills. File validation
alone does not prove discovery in a specific installed client/version.

## Examples

With branch `alejandrocastro0520/pul-72-project-lifecycle`, a valid subject is:

```text
feat(PUL-72): autorizar solicitudes de publicación y archivado
```

A request such as “crea un PR” loads the delivery skill, resolves the issue,
reviews the complete branch diff, validates the relevant behavior, and prepares
the three required sections. If the branch has no issue identifier and the task
does not provide one, the agent asks for it before committing/publishing while
continuing independent local preparation.

A request such as “ajusta el formulario de perfil en móvil” loads the design
and testing skills. A persistence change loads database and testing; publication
also loads delivery. A test suite that skipped JDBC tests because Docker was
unavailable must state that limitation in `Validación`.

These files guide agent behavior. They do not install Git hooks or CI gates and
therefore do not mechanically reject a human-authored nonconforming commit/PR.
