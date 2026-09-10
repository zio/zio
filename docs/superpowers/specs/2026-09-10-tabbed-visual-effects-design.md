# Tabbed visual effects on the zio.dev landing page

Date: 2026-09-10
Status: approved for round 1

## Goal

zio.dev's landing page has a "CodeShowcase" section
(`website/src/components/sections/CodeShowcase/`) with a tab bar (Concurrency,
Error handling, Resource safety, Streaming, Dependency Injection) that shows a
static Scala code snippet per tab. effect.website's landing page does the
same thing but with a live, animated visualization instead of (or alongside)
static code — fibers actually racing, actually being interrupted, etc.

We want the same effect: each CodeShowcase tab eventually gets an animated
visual to go with its code snippet. This is done **iteratively, one tab per
round**, not as one big migration. This document specs the general
architecture (round 1's shape, reused unchanged in later rounds) and the
concrete plan for round 1.

## Prior art / what's reused

Source components: `/home/milad/sources/typescript/visual-effect/main`
(a TypeScript/Next.js/Motion port of Kit Langton's `visual-effect`, itself a
companion to effect.website, already partially rebranded to ZIO naming). Its
animations run the real `effect` npm package client-side (actual fibers,
actual interruption) — a `VisualEffect` wrapper subscribes to that live
execution and drives Motion animations off real state transitions, not a
canned timeline. This is a faithful stand-in for ZIO's runtime semantics for
visualization purposes, the same way the source repo already uses Scala-look
code labels over a JS `effect` engine.

Not reused: the separate `zio-animate` worktree (uncommitted Astro app +
new `zio-visual-model` Scala module). That's a different, heavier
architecture (its own Scala model driving the visuals) and is out of scope
here — this spec ports the lighter TS/`effect`-engine approach directly into
the existing Docusaurus site.

## Architecture (applies to every round)

- New tree: `website/src/components/visual-effects/`, containing ported
  (copied and adapted, not npm-linked) files from the source repo:
  - `VisualEffect.ts` — state-machine wrapper around an `effect` Effect
    (idle/running/completed/failed/interrupted/death), unchanged in
    behavior.
  - `hooks/useVisualEffects.ts` — creates a map of `VisualEffect`s via
    `useMemo`.
  - `effect/` — `EffectNode`, `EffectContainer`, `EffectLabel`,
    `nodeVariants.ts`, `taskUtils.ts`, `useEffectMotion.ts`: the animated
    node representing one running/completed/failed task.
  - `animations.ts` — shared Motion spring config (`defaultSpring`).
  - `colors.ts` — state colors (idle/running/success/error/interrupted).
    These are semantic (blue=running, green=success, red=error,
    orange=interrupted), not brand colors, so they're ported as-is; no
    purple/indigo Effect branding exists in this subset to remap.
- Left out of the port (all optional, add only if/when a later round needs
  them): `TaskSounds` (audio cues + mute toggle), `Notification` /
  floating-snooze-pill UI, `FloatingHighlight` code-hover-sync, `ScopeStack`,
  `ScheduleTimeline`, `QuickOpen`, `NavigationSidebar`. None of these are
  needed to demonstrate a parallel-fail-interrupt scenario, and skipping them
  keeps round 1 small. YAGNI — add in a later round only for a tab that
  actually needs one.
- `website/src/components/sections/CodeShowcase/data.js`: each example
  gains an optional `visual` field — a component reference. Only the
  `concurrency` entry sets one in round 1; the other four stay `visual:
  undefined` and render exactly as today.
- `CodeShowcase/index.jsx`: when `active.visual` is set, render a small
  "Code / Visual" segmented toggle in the panel header (next to the
  existing Scala language badge), defaulting to "Visual". The toggle swaps
  the panel body between the existing `<Highlight>` code block and
  `<active.visual />`; both live inside the same `.codePanel` chrome
  (dark editor-style container, theme-aware CSS vars) — no new panel style.
  Tabs without a `visual` show no toggle, unchanged from today.
- The visual component is loaded through Docusaurus's `<BrowserOnly>` (the
  site's equivalent of the source repo's `dynamic(..., { ssr: false })`):
  Motion/DOM animation cannot run during static prerender.

## Round 1: Concurrency tab

**Scenario**: 3 parallel tasks (`ZIO.foreachPar`-shaped), one of which fails;
the other two are visibly interrupted as a result. This directly illustrates
the tab's existing copy — "if one fails, the rest are interrupted" — more
strongly than a plain two-way race would.

**New file**: `website/src/components/visual-effects/scenarios/ConcurrencyVisual.tsx`
- Builds 3 `VisualEffect`s via `useVisualEffects` wrapping `effect`
  `Effect`s: two that `Effect.sleep` then succeed, one that fails after a
  shorter delay.
- A minimal header: a single Play/Reset button (reuse the icon-swap pattern
  from the source's `HeaderView.tsx`, trimmed to drop the option-key /
  link-copy / mute-aware branches — those depend on ported-out features).
  No sound, no notifications, per the "left out" list above.
- Renders the 3 `EffectNode`s in a row (matches `EffectExample`'s "Multiple
  effects" layout, but without its own nested code block/border chrome or
  hover-highlight wiring — the Scala snippet is already shown by
  `CodeShowcase` itself via the Code/Visual toggle, so this component does
  not duplicate it).
- Colors: reuse `colors.ts` as-is (state-based, not brand-based).

**Deps added to `website/package.json`**: `effect`, `motion`,
`@phosphor-icons/react` (icons for the run/reset control).

**Styling**: fits inside the existing `.codePanel`/`.codeArea` CSS
(`CodeShowcase/styles.module.css`), theme-aware via the site's existing
`[data-theme='dark']` / `--ifm-*` CSS variables. No new color tokens.

**Testing**: `website/` has no existing automated test harness (Docusaurus
site is verified via `yarn build`/manual review, not vitest). Round 1 is
verified by running `yarn start` locally, checking both themes and mobile
width, per the standard for this section.

## Later rounds

Each subsequent round repeats the same pattern for one more tab: add a
`scenarios/<Tab>Visual.tsx`, wire it into that tab's `data.js` entry, verify
locally. No architectural changes expected unless a tab's scenario needs one
of the left-out features above (e.g. Streaming might want a timeline-style
visualization rather than a static node row — evaluate when that round
starts, not now).
