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

- New tree: `website/src/components/visual-effects/`, adapted (not
  npm-linked, not a literal byte-for-byte copy) from the source repo. The
  source's engine is spread across ~15 entangled files (`EffectNode` alone
  pulls in `EffectContainer`, `EffectContent`, `EffectOverlay`, `EffectLabel`,
  `nodeVariants.ts`, `taskUtils.ts`, `useEffectMotion.ts`, a `Timer`
  component, `theme.ts`, `dimensions.ts`, plus failure/death/notification
  "bubble" components and a `useStateTransition` hook). Porting all of that
  for one tab violates YAGNI. Round 1 instead consolidates the same
  *behavior* (a box whose color/motion reflects live effect state) into two
  small files:
  - `VisualEffect.ts` — trimmed state-machine wrapper around an `effect`
    Effect. Same observable-subscribe pattern as the source, but drops what
    round 1's scenario doesn't use: no parent/child notification service, no
    sound hooks, no `showTimer`, no `death` state (nothing in round 1's
    scenario calls `Effect.die`). States: `idle | running | completed
    | failed | interrupted`.
  - `hooks/useVisualEffects.ts` — same shape as the source: builds a map of
    `VisualEffect`s via `useMemo`.
  - `effect-node/EffectNode.tsx` — one file: a `motion.div` box driven by a
    Motion `variants` object per state (color + scale, a looping pulse while
    running, a brief shake on failure via keyframes in the `failed` variant)
    plus a text label underneath. No separate container/content/overlay
    layering, no physics-based jitter/glitch system, no bubbles, no timer.
  - `colors.ts` — state colors (idle/running/success/error/interrupted).
    These are semantic (blue=running, green=success, red=error,
    orange=interrupted), not brand colors, so they're carried over as-is; no
    purple/indigo Effect branding exists in this subset to remap.
  - `animations.ts` — just the one shared Motion spring config
    (`defaultSpring`) that round 1 needs; the source's `springs`/`shake`/
    `timing`/`effects` tables are not carried over since nothing in round 1
    reads them.
- Left out entirely (add only if/when a later round's scenario actually
  needs one): `TaskSounds` (audio cues + mute toggle), `Notification` /
  floating-snooze-pill UI, `FloatingHighlight` code-hover-sync, `ScopeStack`,
  `ScheduleTimeline`, `QuickOpen`, `NavigationSidebar`, the glitch/jitter
  physics system, `death` state. YAGNI — a later round may need to widen
  `EffectNode`/`VisualEffect` back out (e.g. add `death` for a Streaming
  scenario that dies), but that's this architecture evolving under real
  requirements, not speculative upfront work.
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
  shorter delay. On failure, explicitly interrupts the other two fibers
  (mirrors what `ZIO.foreachPar` does automatically) so their `EffectNode`s
  animate to the `interrupted` state.
- A minimal header: a single Play/Reset button — a small button that swaps
  icon by state (Play when idle/done, Stop while running), not a port of the
  source's `HeaderView.tsx` (which is entangled with the option-key /
  link-copy / mute features round 1 doesn't have). No sound, no
  notifications, per the "left out" list above.
- Renders the 3 `EffectNode`s in a row with a label under each (task name).
  No nested code block/border chrome and no hover-highlight wiring — the
  Scala snippet is already shown by `CodeShowcase` itself via the Code/Visual
  toggle, so this component does not duplicate it.
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

## Post-round-1 amendment

Round 1 shipped with more fidelity than this document originally scoped, in
response to direct user feedback after seeing the consolidated version
render: the "consolidated single-file EffectNode" and "bespoke
ConcurrencyVisual scenario" described above were replaced with a verbatim
port of the source engine's actual node-rendering chain (`EffectContainer`/
`EffectContent`/`EffectOverlay`/`EffectLabel`/`nodeVariants`/`taskUtils`/
`useEffectMotion`, `Timer`, `feedback/` bubbles, `renderers/`) and the actual
`src/examples/effect-race.tsx` example (via `EffectExample`/`HeaderView`/
`CodeBlock`/`FloatingHighlight`), not an invented scenario. `VisualEffect.js`
was restored to its full source behavior (notify/children/`death` state),
with sound routed through a real ported `TaskSoundSystem` (Tone.js), not a
silent stub. The standing rule going forward (see also
`docs/superpowers/plans/2026-09-10-tabbed-visual-effects-round1.md`'s
ledger): **port the actual source file and its real dependency chain**,
adapting only what's structurally necessary (TS types stripped, dead
sound/theme mismatches fixed for this being a light/dark-toggling host page
instead of the source's permanently-dark one) — not a simplified rewrite.

One more standing rule earned the same way: **a tab's outer "Code" snippet
in `data.js` and its Visual view's embedded example must show the same
example** — when a round ports a new example, update `data.js`'s `code`
field (and the compile-checked `specs/snippet-check/showcase.scala`) to
match, don't leave the old snippet in place.

## Round 2: Error handling tab

**Example**: `src/examples/effect-retry-exponential.tsx` — "ZIO.retry",
variant "exponential" (section "schedule" in the source's own manifest, not
"error handling" — chosen for this tab anyway because its content,
`park.retry(Schedule.exponential(700.millis))`, directly matches what's
already on this tab today: `fetchConfig.retry(Schedule.exponential(100.millis)
&& Schedule.recurs(5))`). Per the parity rule above and the user's explicit
ask to relate the ported example to the current one, content fit won out
over the source's own section tag.

**New capability needed**: this example passes `showScheduleTimeline={true}`
to `EffectExample`, which round 1 dropped as unused. Restore that prop and
port `src/components/ScheduleTimeline.tsx` (a ~580-line scrolling
attempt-timeline showing running/gap segments with duration labels) verbatim
— the first real test of "later rounds may need to widen `EffectNode`/
`EffectExample` back out" from round 1's YAGNI notes.

**Other additions**: `useVisualEffect` (singular — round 1 only ported the
plural `useVisualEffects`) and `createCounter` (from the source's
`examples/helpers.ts`, round 1 skipped it as unused).

**Wiring**: `data.js`'s `errors` entry gets `visual: 'error-handling'` (or
similar key) and its `code` field updated to match the ported example's own
snippet; `specs/snippet-check/showcase.scala`'s Snippet2 updated and
recompiled to match, same as round 1 did for Snippet1.

## Round 3: Resource safety tab

**Example**: `src/examples/effect-acquire-release.tsx` — "ZIO.acquireRelease",
section "scope" in the source's own manifest. Three resources (database,
cache, logger) acquired then released in reverse order via a `VisualScope`
finalizer stack; the main task cycles through success/failure/defect each
run, demonstrating that cleanup runs regardless of how the effect exits.
Matches this tab's existing copy almost exactly already ("Many resources
compose and close in reverse order" / "Guaranteed on success, failure, or
interruption alike") — no copy rewrite expected, unlike round 2.

**New capability needed**: the `scope` prop on `EffectExample` (dropped in
round 1 as unused, same pattern as round 2's `showScheduleTimeline`).
Restoring it requires porting `VisualScope.ts` (a small state-machine class:
`idle → acquiring → active → releasing → released`, holding a LIFO
finalizer stack), `hooks/useVisualScope.ts` (a one-line force-update hook),
and `components/scope/{ScopeStack,FinalizerCard}.tsx` (the stack
visualization and its individual finalizer cards).

**Known deviation, ruled on before implementation**: the source example
sets `isDarkMode={mainTaskState.type === "death"}` on `EffectExample` — a
dark-red "something died" visual accent, achieved by swapping the card's
whole background/border. Round 1 already removed `isDarkMode` entirely and
replaced it with fixed `var(--ifm-*)` theme tokens (see the "Post-round-1
amendment" above) specifically because two permanently-dark variants don't
work on a light/dark-toggling host page. Reintroducing a death-triggered
background swap would reintroduce exactly that bug a third time. Ruling:
**drop the death-triggered visual accent entirely** for this round — the
functional demonstration (finalizers run LIFO regardless of success,
failure, or defect) is fully preserved without it; only the cosmetic "flash
dark red" flourish is cut. `FinalizerCard.tsx`'s own colors (small,
saturated per-state chips — gray/blue/green) are left as-is, same reasoning
as round 1 leaving `TASK_COLORS` alone: they're semantic state colors that
read fine on either theme, not a dark-host assumption.

## Later rounds

Each subsequent round repeats the same pattern for one more tab: add a
`scenarios/<Tab>Visual.tsx`, wire it into that tab's `data.js` entry, verify
locally. No architectural changes expected unless a tab's scenario needs one
of the left-out features above (e.g. Streaming might want a timeline-style
visualization rather than a static node row — evaluate when that round
starts, not now).
