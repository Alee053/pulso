# Pulso UI/UX refactor research

Research date: 2026-10-06. Scope: presentation and usability of existing functionality.

## Existing application

Pulso uses Angular 22, Tailwind 4, feature-local UI components, and a signal-driven application shell. Current surfaces include authentication, onboarding, discovery, saved/interested projects, profile editing, draft creation, and project detail. Preserve their events, validation, permissions, and persistence boundaries.

Observed problems include very small low-opacity text, inconsistent surfaces, excessive glass effects, mobile discovery buttons hidden in favor of gestures, incomplete reduced-motion coverage, and mobile scroll constraints that omit project creation. A duplicate stale profile editor also prevents compilation.

## Design recommendations and sources

1. **Readable hierarchy.** Establish semantic page titles, section titles, body text, labels, and metadata. Use roughly 16px body text and 14px metadata as project design choices, and reduce oversized headings on working screens. [Atlassian typography](https://atlassian.design/foundations/typography/) supports consistent typography, relative units, and semantic hierarchy.

2. **Consistent surfaces and spacing.** Retain Pulso's dark identity with navy/slate surfaces and restrained sky-blue accents. Define semantic color and spacing tokens; prefer opaque backgrounds for important text. [Atlassian spacing](https://atlassian.design/foundations/spacing/) supports a consistent spacing scale. [W3C contrast guidance](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) specifies 4.5:1 contrast for ordinary text and 3:1 for qualifying large text. Verify final color pairs, including composited backgrounds.

3. **Scannable project information.** Prioritize project name and pitch, followed by stage, sector, and location; place actions consistently. Use compact rows for saved/interested collections while preserving their distinction. [Material card guidance](https://m1.material.io/components/cards.html) supports hierarchy, restrained content, and readable image overlays. This is legacy guidance for enduring design principles, not a framework recommendation.

4. **Discoverable mobile actions.** Show existing skip, save, and interested controls alongside gestures. Aim for 44–48px important touch controls as a usability choice. [W3C dragging guidance](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html) requires a single-pointer alternative without dragging. [W3C target-size guidance](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html) sets an AA minimum of 24 by 24 CSS pixels, subject to exceptions. Expose navigation labels and current destination consistently.

5. **Consistent forms.** Use persistent labels, associated hints/errors, visible focus, and consistent field dimensions across authentication, profile, onboarding, and project creation. Preserve validators and field limits. [GOV.UK text inputs](https://design-system.service.gov.uk/components/text-input/) and [error messages](https://design-system.service.gov.uk/components/error-message/) support explicit instructions and actionable error feedback.

6. **Deliberate states.** Unify loading, empty, error, and success presentation while distinguishing an empty catalog, completed feed, no saved projects, unavailable detail, and request failure. Keep existing retries and restarts. [Atlassian empty states](https://atlassian.design/components/empty-state/) supports explaining absent data and available next steps.

7. **Resilient responsive layouts.** Allow long forms and content to grow and scroll; reserve space for bottom navigation and safe areas. Check narrow and short screens, long Spanish labels, and enlarged text. [W3C reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html) targets an equivalent width of 320 CSS pixels without loss of information or functionality. [W3C focus visibility](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html) addresses focus hidden by authored content.

8. **Purposeful motion and dialogs.** Use brief selection and hover feedback, simplify decorative flips, and honor reduced motion throughout. Preserve focus placement, Tab containment, Escape dismissal, and focus restoration. Sources: [Atlassian motion](https://atlassian.design/foundations/motion/) and [W3C modal dialog pattern](https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/).

## Limits

Palette, radii, and visual treatment are project design recommendations; the sources do not prove their effectiveness for Pulso. This research is not a browser accessibility audit or a claim of WCAG conformance. It proposes no new product functionality or changes to backend, data contracts, or schema.

## Implementation verification

- Node 24 with the repository's pnpm toolchain: frozen dependency installation passed; `pnpm --filter @pulso/frontend test` passed 58 tests across 20 files; `pnpm build` passed with a 392.84 kB initial bundle, within the existing 500 kB warning budget.
- An isolated Chrome session with intercepted API/auth fixtures passed 28 UI checks across desktop, narrow mobile (320 px), and short mobile (480 px) layouts. Checks covered persistent labels, autocomplete, selection/current-navigation state, overflow, reachable touch controls, feedback clearance, native text scrolling, retained cover swipes, dialog scrolling/focus containment/restoration, validation associations, and reduced motion. Screenshots were inspected for the main forms and discovery surfaces.
- Standards and approved-scope reviews found no unresolved material issues. `git diff --check` passed.

These browser checks validate presentation and existing UI interactions with controlled data. They do not verify live backend requests, Supabase authentication, production images, or deployment.
