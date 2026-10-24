---
name: pulso-design-system
description: Implement or review Pulso Angular UI using its existing theme, components, copy, responsive behavior, and accessibility conventions. Use for frontend components, styles, forms, visual refactors, design consistency, or interaction changes.
---

# Pulso design system

## Inspect before designing

1. Read `AGENTS.md`, `docs/architecture/frontend.md`, and
   `docs/engineering/configurable-values-inventory.md`.
2. Inspect `frontend/src/index.css` for the actual semantic tokens and shared
   primitives, `frontend/src/app/shared/icon/icon.ts` and
   `frontend/src/app/shared/brand-mark/brand-mark.ts` for existing visual assets,
   and the affected feature's `ui/`, `strings/`, and tests.
3. Establish the issue's intended behavior and preserve it during visual work.
   Read `docs/research/ui-ux-refactor.md` when changing accessibility, responsive
   layout, motion, or the overall visual direction. Its historical verification
   is context, not evidence for the current diff.

Proceed when the owning component, existing patterns, and required interaction
states are identified. There is no separate component-library dependency to add.

## Apply the existing visual language

- Reuse the navy/slate surfaces, sky-blue accent, typography, spacing, radii,
  focus styles, and shared classes defined in `frontend/src/index.css`.
  Reference semantic variables instead of duplicating theme values in templates.
  Add a token only when the existing vocabulary cannot represent the need.
- Reuse the local icon and brand components. Extend their existing interfaces
  for a demonstrated need; preserve accessible names on icon-only controls.
- Keep feature presentation local. Promote a primitive to `shared/` only with
  demonstrated reuse. Do not move domain policy, HTTP requests, or session
  ownership into a template to simplify styling.
- Keep copy in the established `strings/` modules and use the product
  vocabulary in `CONTEXT.md`. Spanish is the reference language; a module that
  already uses `defineStrings` needs its English text too, and visible text must
  not be copied into a field or constant at load time (see "Language support" in
  `docs/architecture/frontend.md`). Preserve compatibility identifiers
  intentionally.
- Define loading, empty, error, retry, disabled, selected, and success states
  where relevant. Use semantic controls and persistent associated form labels,
  hints, validation errors, and visible focus.
- Preserve labeled alternatives to gestures, normal vertical scrolling and
  pinch zoom, clearance for fixed navigation, and device safe areas. Check narrow
  and short viewports, long Spanish content, and enlarged text.
- For dialogs, preserve reachable dismissal, initial focus, Tab containment,
  Escape handling, and return focus. Honor `prefers-reduced-motion`.
- Keep saved and interested decisions distinct. Do not turn a visual task into
  new persistence, external integrations, or changes to authorization.

## Validate the result

Use `pulso-testing` for affected component/domain/adapter tests and the frontend
build. Inspect the changed UI in a browser when available, including a narrow
320px viewport, a short mobile screen, desktop, keyboard navigation, and relevant
error/loading states. Check final text/background contrast when colors change.
Report which scenarios were observed and whether they used fixtures or real
services. If browser access is unavailable, report the manual checks still
pending instead of claiming visual verification from a successful build.
