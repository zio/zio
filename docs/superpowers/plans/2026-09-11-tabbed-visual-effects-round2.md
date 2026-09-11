# Tabbed Visual Effects — Round 2 (Error handling tab) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, animated visualization — retry with exponential
backoff, ported verbatim from the source `visual-effect` project's
`effect-retry-exponential` example — to the "Error handling" tab of
zio.dev's landing-page `CodeShowcase` section, same Code/Visual toggle
pattern as round 1's Concurrency tab.

**Architecture:** Reuses round 1's engine (`VisualEffect.js`,
`EffectContainer`/`EffectContent`/`EffectOverlay`/`nodeVariants`/
`useEffectMotion`/`EffectNode`, `EffectExample`/`HeaderView`/`CodeBlock`)
unchanged. Adds the two capabilities round 1 deliberately left unported
because nothing needed them yet: `useVisualEffect` (singular; round 1 only
ported the plural `useVisualEffects`) and `showScheduleTimeline` support in
`EffectExample` (backed by a new verbatim port of
`src/components/ScheduleTimeline.tsx`).

**Tech Stack:** Same as round 1 — plain `.js`/`.jsx`, `effect`, `motion`,
`@phosphor-icons/react`, Tailwind CSS v4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-10-tabbed-visual-effects-design.md`
(see "Round 2: Error handling tab" section, added 2026-09-11)

## Global Constraints

- Port the actual source file (`/home/milad/sources/typescript/visual-effect/main/src/examples/effect-retry-exponential.tsx`)
  and its real dependency chain, TS types stripped, otherwise unchanged —
  do not invent a simplified scenario. This is a standing rule from round 1
  (see the spec's "Post-round-1 amendment").
- The "Error handling" tab's outer Code-view snippet in `data.js` and its
  Visual view's embedded example must show the same example — update
  `data.js`'s `code` field AND `specs/snippet-check/showcase.scala`'s
  Snippet2 to match the ported example, verified via
  `scala-cli compile specs/snippet-check/showcase.scala`.
- Only the `errors` tab in `data.js` gets touched. `concurrency`,
  `resources`, `streaming`, `di` stay exactly as they are.
- Files are plain `.js`/`.jsx`, matching every existing file under
  `website/src/`.
- No automated test harness exists under `website/` — verified via the dev
  server (already running at `http://localhost:4123/`, survived a prior
  session restart) plus Playwright + the system Chromium at
  `/nix/var/nix/profiles/default/bin/chromium` (the bundled Playwright
  Chromium cannot launch in this sandbox — missing `libglib-2.0.so.0`).

---

### Task 1: Extend the shared engine — `useVisualEffect` (singular), `createCounter`, restore `showScheduleTimeline`

**Files:**
- Modify: `website/src/components/visual-effects/hooks/useVisualEffects.js`
- Modify: `website/src/components/visual-effects/examples/helpers.js`
- Modify: `website/src/components/visual-effects/EffectExample.jsx`

**Interfaces:**
- Consumes: `visualEffect` from `../VisualEffect` (already imported in
  `useVisualEffects.js`); `Effect` from `effect` (already imported in
  `helpers.js`).
- Produces: `useVisualEffect(name, create, options = {})` — `options` is
  `{ showTimer？= false, deps = [] }`; returns one `VisualEffect`, memoized
  over `deps`. `createCounter(initialValue = 0)` — returns
  `{ current (getter), increment(), reset (an Effect) }`. `EffectExample`
  gains a `showScheduleTimeline` boolean prop (default falsy/undefined,
  same as every other optional prop here) that, when true and both
  `effects[0]` and `resultEffect` are present, renders `<ScheduleTimeline
  baseEffect={effects[0]} repeatEffect={resultEffect} />` (Task 2's
  component) in a bordered row between the node-visualization row and the
  code block.

- [ ] **Step 1: Add `useVisualEffect` to `hooks/useVisualEffects.js`**

Current file:

```js
import { useMemo } from 'react';
import { visualEffect } from '../VisualEffect';

// Builds a map of VisualEffects from `{ name: () => Effect }` definitions,
// memoized once (over `deps`) so the same instances persist across renders.
export function useVisualEffects(definitions, deps = []) {
  return useMemo(() => {
    const effects = {};
    for (const [name, create] of Object.entries(definitions)) {
      effects[name] = visualEffect(name, create());
    }
    return effects;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
```

Add this function to the end of the file (ported from the source engine's
`src/hooks/useVisualEffects.ts`, TS types stripped):

```js

// Builds a single VisualEffect, memoized over `deps`. Sibling of
// useVisualEffects above for the common case of one task, not a map.
export function useVisualEffect(name, create, options = {}) {
  const { showTimer = false, deps = [] } = options;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => visualEffect(name, create(), showTimer), deps);
}
```

- [ ] **Step 2: Add `createCounter` to `examples/helpers.js`**

Current file starts with:

```js
import { Effect } from 'effect';
import { EmojiResult } from '../renderers';

// Ported from the source engine's src/examples/helpers.ts — only the
// pieces effect-race.jsx actually uses (getDelay, Emoji, loadEmoji).
// getWeather/createCounter are omitted, unused by the ported example.
```

Change the comment (it's now wrong — `createCounter` is used) and add the
function, ported verbatim from the source's `src/examples/helpers.ts`:

```js
import { Effect } from 'effect';
import { EmojiResult } from '../renderers';

// Ported from the source engine's src/examples/helpers.ts — the pieces
// effect-race.jsx and effect-retry-exponential.jsx actually use (getDelay,
// Emoji, loadEmoji, createCounter). getWeather is still omitted, unused by
// either ported example.
```

Add at the end of the file:

```js

/**
 * Creates a stateful counter with reset functionality.
 * @param initialValue Initial counter value (default 0)
 * @returns Object with `current` (getter), `increment()`, and a `reset` Effect
 */
export function createCounter(initialValue = 0) {
  let value = initialValue;

  return {
    get current() {
      return value;
    },
    increment() {
      value++;
    },
    reset: Effect.sync(() => {
      value = initialValue;
    }),
  };
}
```

- [ ] **Step 3: Restore `showScheduleTimeline` in `EffectExample.jsx`**

Add the import (alongside the existing `CodeBlock`/`EffectNode`/
`FloatingHighlight`/`HeaderView` imports at the top of the file):

```js
import { ScheduleTimeline } from './ScheduleTimeline';
```

In the destructured props of `EffectExampleComponent`, add
`showScheduleTimeline` (current props list, alphabetical-ish order the file
already uses):

```js
function EffectExampleComponent({
  code,
  configurationPanel,
  description,
  exampleId,
  name,
  refs = EMPTY_REFS_ARRAY,
  resultEffect,
  effectHighlightMap,
  effects,
  showScheduleTimeline,
  variant,
}) {
```

Immediately after the closing `</motion.div>` of the "Main visualization"
block and before the "Code block" comment/div, insert:

```jsx
      {/* Schedule timeline (if provided) */}
      {showScheduleTimeline && effects[0] && resultEffect && (
        <motion.div
          initial={{ borderColor: borderColorValue }}
          animate={{ borderColor: borderColorValue }}
          transition={standardTransition}
          className="border-b"
        >
          <ScheduleTimeline baseEffect={effects[0]} repeatEffect={resultEffect} />
        </motion.div>
      )}

```

(Leave the memo comparator at the bottom of the file untouched —
`showScheduleTimeline` doesn't need to be in it; the source's own comparator
never included it either, since it's fixed per mount.)

- [ ] **Step 4: Verify headlessly**

Run (from `website/`):

```bash
node --input-type=module -e "
import { createCounter } from './src/components/visual-effects/examples/helpers.js';
const c = createCounter(0);
console.log('start:', c.current);
c.increment();
c.increment();
console.log('after 2 increments:', c.current);
"
```

Expected: prints `start: 0`, then `after 2 increments: 2`.

Then confirm the dev server (already running at `http://localhost:4123/`)
picks up the change without errors: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:4123/`
should still print `200`, and there should be no new compile errors in
whatever log/terminal is tracking `yarn start` (the implementer should
locate it — `ps aux | grep docusaurus` or check for an existing log file
before assuming one needs to start a fresh server; if none is trackable,
starting a fresh one on a free port, e.g. `PORT=4124 yarn start`, is fine).

- [ ] **Step 5: Commit**

```bash
cd website && git add src/components/visual-effects/hooks/useVisualEffects.js src/components/visual-effects/examples/helpers.js src/components/visual-effects/EffectExample.jsx && git commit -m "feat(website): add useVisualEffect, createCounter, restore showScheduleTimeline"
```

---

### Task 2: Port `ScheduleTimeline.jsx`

**Files:**
- Create: `website/src/components/visual-effects/ScheduleTimeline.jsx`

**Interfaces:**
- Consumes: `useVisualEffectState` from `./VisualEffect` (already exported,
  unchanged since round 1).
- Produces: `ScheduleTimeline` (named export), props
  `{ baseEffect, repeatEffect, className = '', pixelsPerSecond = 100, scrollThreshold = 0.8 }`
  — a scrolling horizontal timeline showing running/gap segments with
  duration labels, driven entirely by `baseEffect`'s and `repeatEffect`'s
  live state (no other props needed). This is what Task 1's
  `showScheduleTimeline` branch renders.

- [ ] **Step 1: Create the file**

This is a verbatim port (TS types/interfaces stripped, otherwise
byte-identical) of the source engine's
`/home/milad/sources/typescript/visual-effect/main/src/components/ScheduleTimeline.tsx`.
Read that file and transcribe it to
`website/src/components/visual-effects/ScheduleTimeline.jsx` with these
mechanical changes only:
- Drop `import type { VisualEffect } from "@/VisualEffect"` (type-only, not
  needed in JS).
- Change `import { useVisualEffectState } from "@/VisualEffect"` to
  `import { useVisualEffectState } from './VisualEffect';` (relative path —
  this file lives at `website/src/components/visual-effects/ScheduleTimeline.jsx`,
  a sibling of `VisualEffect.js`, same as `Timer.jsx`/`HeaderView.jsx`/
  `EffectExample.jsx` already are).
- Drop the `export interface ScheduleTimelineProps { ... }` and
  `interface TrailSegment { ... }` blocks entirely (TS-only).
- Drop every `: Type` annotation on function parameters, destructured
  props, and `useState<T>`/`useRef<T>` generic type arguments (e.g.
  `useState<Array<TrailSegment>>([])` becomes `useState([])`,
  `useRef<HTMLDivElement>(null)` becomes `useRef(null)`).
- Drop the `as const` on `TIMELINE_CONFIG`.
- Everything else — every class name, every style value, every animation
  timing, every color token (including the `var(--color-*)` references,
  which resolve against this site's Tailwind v4 theme the same way
  round 1's `colors.js` already relies on) — stays exactly as written in
  the source file.

- [ ] **Step 2: Sanity-check imports resolve**

Run (from `website/`):

```bash
node -e "
const fs = require('fs');
const content = fs.readFileSync('src/components/visual-effects/ScheduleTimeline.jsx', 'utf8');
if (content.includes('@/') || content.includes('interface ') || content.includes(': React') || /useState<|useRef</.test(content)) {
  console.error('FOUND LEFTOVER TS/ALIAS SYNTAX');
  process.exit(1);
}
console.log('clean');
"
```

Expected: prints `clean`. If it prints `FOUND LEFTOVER TS/ALIAS SYNTAX`,
find and fix the leftover syntax before proceeding — this file is not
imported by anything yet (Task 3 wires it in), so a syntax error here
won't surface until then; catch it now.

- [ ] **Step 3: Commit**

```bash
cd website && git add src/components/visual-effects/ScheduleTimeline.jsx && git commit -m "feat(website): port ScheduleTimeline component"
```

---

### Task 3: Port the `effect-retry-exponential` scenario

**Files:**
- Create: `website/src/components/visual-effects/scenarios/RetryExponentialVisual.jsx`

**Interfaces:**
- Consumes: `useVisualEffect` from `../hooks/useVisualEffects` (Task 1);
  `visualEffect` from `../VisualEffect`; `createCounter`, `getDelay` from
  `../examples/helpers` (Task 1 / already present); `EffectExample` from
  `../EffectExample` (Task 1's `showScheduleTimeline` prop); `Effect`,
  `Schedule` from `effect`.
- Produces: `RetryExponentialVisual` (default export), a zero-prop
  component — this is what Task 4 wires into `CodeShowcase`.

- [ ] **Step 1: Create the file**

```jsx
// website/src/components/visual-effects/scenarios/RetryExponentialVisual.jsx
import { Effect, Schedule } from 'effect';
import { useMemo } from 'react';
import { EffectExample } from '../EffectExample';
import { createCounter, getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { visualEffect } from '../VisualEffect';

// Ported verbatim (TS types stripped, otherwise unchanged) from the source
// engine's src/examples/effect-retry-exponential.tsx — the actual
// "ZIO.retry" example from the visual-effect project, chosen for the Error
// handling tab because its retry+exponential-backoff shape matches what
// was already on this tab (see the design spec's "Round 2" section).
const parkingAttempt = createCounter(0);

const attempts = ['😤 Too Close!', '😡 Too Far!', '🤬 Neutral!', '😑 Focus.'];

function attemptParallelPark() {
  return Effect.gen(function* () {
    const delay = getDelay(400, 800);
    yield* Effect.sleep(delay);

    const attemptIndex = Math.min(parkingAttempt.current, attempts.length - 1);
    const message = attempts[attemptIndex] ?? '😬 Try Again!';

    parkingAttempt.increment();

    // Reset counter after giving up
    if (parkingAttempt.current > attempts.length) {
      return '🚗 Parked!';
    }

    return yield* Effect.fail(message);
  });
}

export default function RetryExponentialVisual() {
  const baseTask = useVisualEffect('park', attemptParallelPark);

  const repeatedTask = useMemo(
    () =>
      visualEffect(
        'result',
        Effect.retry(baseTask.effect, Schedule.exponential('700 millis')).pipe(
          Effect.ensuring(parkingAttempt.reset),
        ),
      ),
    [baseTask],
  );

  const codeSnippet = `val park = attemptParallelPark()
val result = park.retry(Schedule.exponential(700.millis))`;

  const taskHighlightMap = useMemo(
    () => ({
      park: { text: 'attemptParallelPark()' },
      result: { text: 'park.retry(Schedule.exponential(700.millis))' },
    }),
    [],
  );

  return (
    <EffectExample
      name="ZIO.retry"
      variant="exponential"
      description="Retry with exponential backoff"
      code={codeSnippet}
      effects={useMemo(() => [baseTask], [baseTask])}
      resultEffect={repeatedTask}
      effectHighlightMap={taskHighlightMap}
      showScheduleTimeline={true}
      exampleId="effect-retry-exponential"
    />
  );
}
```

- [ ] **Step 2: Verify imports resolve to real exports**

Read `../hooks/useVisualEffects.js`, `../examples/helpers.js`, `../EffectExample.jsx`, and `../VisualEffect.js` and confirm `useVisualEffect`, `createCounter`, `getDelay`, `EffectExample`, and `visualEffect` are all actually exported with those names (Task 1 added the first three; the rest already existed after round 1).

- [ ] **Step 3: Commit**

```bash
cd website && git add src/components/visual-effects/scenarios/RetryExponentialVisual.jsx && git commit -m "feat(website): add RetryExponentialVisual scenario component"
```

(No standalone runtime verification here — same as round 1's scenario
task, this component has no meaningful render target until Task 4 mounts
it on the page.)

---

### Task 4: Wire into `CodeShowcase` and keep the Code snippet in parity

**Files:**
- Modify: `website/src/components/sections/CodeShowcase/data.js`
- Modify: `website/src/components/sections/CodeShowcase/index.jsx`
- Modify: `specs/snippet-check/showcase.scala`

**Interfaces:**
- Consumes: `RetryExponentialVisual` (Task 3, loaded via `React.lazy`
  inside `VISUAL_COMPONENTS`, same pattern as `concurrency`'s `RaceVisual`
  entry).
- Produces: the landing page's Error handling tab shows a "Code / Visual"
  toggle, identical in behavior to the Concurrency tab's.

- [ ] **Step 1: Add the `visual` field and update the Code snippet in `data.js`**

The `errors` entry currently reads (in full):

```js
  {
    value: 'errors',
    label: 'Error handling',
    takeaway:
      'Errors are typed — the compiler knows what can fail, and when you have handled it all.',
    points: [
      'Every possible failure is visible in the type, not hidden in exceptions.',
      'Built-in retry policies recover from transient failures with backoff.',
      'The compiler proves when every error has been handled.',
    ],
    code: `enum AppError:
  case NetworkError(msg: String)
  case ParseError(line: Int)

def fetchConfig: ZIO[Any, AppError, Config] = ???

val program: ZIO[Any, Nothing, Config] =
  fetchConfig
    .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
    .catchAll:
      case AppError.NetworkError(_) => cachedConfig
      case AppError.ParseError(_)   => ZIO.succeed(Config.fallback)`,
  },
```

Change it to:

```js
  {
    value: 'errors',
    label: 'Error handling',
    visual: 'errors',
    takeaway:
      'Errors are typed — the compiler knows what can fail, and when you have handled it all.',
    points: [
      'Every possible failure is visible in the type, not hidden in exceptions.',
      'Built-in retry policies recover from transient failures with backoff.',
      'The compiler proves when every error has been handled.',
    ],
    code: `val park = attemptParallelPark()
val result = park.retry(Schedule.exponential(700.millis))`,
  },
```

(The `takeaway`/`points` bullets stay — they still describe the ported
example accurately: retry policies with backoff, typed failure.)

- [ ] **Step 2: Register the lazy-loaded component in `index.jsx`**

`VISUAL_COMPONENTS` currently reads:

```js
const VISUAL_COMPONENTS = {
  concurrency: React.lazy(() => import('../../visual-effects/scenarios/RaceVisual')),
};
```

Change to:

```js
const VISUAL_COMPONENTS = {
  concurrency: React.lazy(() => import('../../visual-effects/scenarios/RaceVisual')),
  errors: React.lazy(() => import('../../visual-effects/scenarios/RetryExponentialVisual')),
};
```

- [ ] **Step 3: Update the compile-checked spec**

Read `specs/snippet-check/showcase.scala` in full first (it has stubs at
the top and 5 `object SnippetN` blocks — you need to see the current stubs
to know what else, if anything, becomes unused). `Snippet2` currently
reads:

```scala
// ── Snippet 2: Error handling ───────────────────────────────────────────
object Snippet2 {
  enum AppError:
    case NetworkError(msg: String)
    case ParseError(line: Int)

  def fetchConfig: ZIO[Any, AppError, Config] = ???

  val program: ZIO[Any, Nothing, Config] =
    fetchConfig
      .retry(Schedule.exponential(100.millis) && Schedule.recurs(5))
      .catchAll:
        case AppError.NetworkError(_) => cachedConfig
        case AppError.ParseError(_)   => ZIO.succeed(Config.fallback)
}
```

Change it to:

```scala
// ── Snippet 2: Error handling ───────────────────────────────────────────
// Matches the "ZIO.retry" example mounted in the Error handling tab's
// Visual view (website/src/components/visual-effects/scenarios/RetryExponentialVisual.jsx)
// — Visual and Code must show the same example.
object Snippet2 {
  val park   = attemptParallelPark()
  val result = park.retry(Schedule.exponential(700.millis))
}
```

Add a stub for `attemptParallelPark` near the other stub `def`s (next to
`runFast`, which round 1 added for the same reason):

```scala
def attemptParallelPark(): IO[String, String] = ZIO.succeed("parked")
```

Then check whether `Config`, `object Config`, and `val cachedConfig` are
referenced anywhere else in the file (`grep -n "Config" specs/snippet-check/showcase.scala`
— from the repo root). If they're only used by the old `Snippet2` (which is
likely — `Snippet1`/`Snippet3`/`Snippet4`/`Snippet5` don't reference
`Config`), remove those now-unused stub lines too:

```scala
case class Config()
object Config { val fallback: Config = Config() }
...
val cachedConfig: UIO[Config] = ZIO.succeed(Config())
```

Leave `User`, `Stats`, `Event`, `File`, `Database`, `Logger`, and their
related `def`s alone — those back other snippets.

- [ ] **Step 4: Compile-check**

Run (from the repo root):

```bash
scala-cli compile specs/snippet-check/showcase.scala
```

Expected: compiles with no errors (may print dependency-resolution/caching
lines from `scala-cli` itself — that's normal, only a compile error is a
failure here).

- [ ] **Step 5: Manual verification**

The dev server should already be running (see Task 1 Step 4 for how to
find it) — if not, start it. Then:

1. Load the homepage, scroll to "The ZIO Way".
2. Click the "Error handling" tab. Confirm a "Visual / Code" toggle now
   appears (it shouldn't for Resource safety / Streaming / Dependency
   Injection).
3. In Visual mode: confirm the "ZIO.retry" header renders, a single node
   labeled "park" is shown, and clicking the header runs it — it should
   fail repeatedly with increasing delays between attempts (exponential
   backoff), each attempt appended to a scrolling timeline below the node,
   until eventually succeeding (after `attempts.length + 1` = 5 tries) and
   showing "🚗 Parked!".
4. Click "Code": confirm it shows the same `park`/`result`/
   `Schedule.exponential(700.millis)` snippet now on the page (not the old
   `enum AppError` text).
5. Switch theme to dark and back to light; confirm the card and timeline
   remain legible in both (same theme-aware tokens as round 1's card fix).
6. Confirm the Concurrency tab (and its Visual view) are unchanged.

Use the same Playwright + system-Chromium approach as round 1
(`executablePath: '/nix/var/nix/profiles/default/bin/chromium'` — note this
path changed from round 1's `/home/milad/.nix-profile/bin/chromium` after a
session restart; check both paths exist and use whichever does) if a
screenshot is useful for confirming visually; otherwise a careful read of
the rendered DOM / console errors is enough. Note in your report which
verification you actually performed.

- [ ] **Step 6: Commit**

```bash
cd /home/milad/sources/scala/zio-2.x-worktrees/zio-visual-effect && git add website/src/components/sections/CodeShowcase/data.js website/src/components/sections/CodeShowcase/index.jsx specs/snippet-check/showcase.scala && git commit -m "feat(website): wire RetryExponentialVisual into the Error handling tab"
```

---

## Plan Self-Review

**Spec coverage:** Every element of the spec's "Round 2: Error handling
tab" section is implemented: `showScheduleTimeline` restoration +
`ScheduleTimeline` port (Tasks 1–2), the actual `effect-retry-exponential`
example (Task 3), `useVisualEffect`/`createCounter` additions (Task 1), and
the Code/Visual parity rule applied to `data.js` + `specs/snippet-check/showcase.scala`
(Task 4).

**Placeholder scan:** No TBD/TODO markers; every step has runnable code or
an exact shell command.

**Type consistency:** `useVisualEffect(name, create, options)`'s signature
in Task 1 matches its call site in Task 3 (`useVisualEffect('park', attemptParallelPark)`,
no options — defaults apply). `ScheduleTimeline`'s prop names (`baseEffect`,
`repeatEffect`) in Task 2 match `EffectExample.jsx`'s Task 1 usage
(`baseEffect={effects[0]} repeatEffect={resultEffect}`). `RetryExponentialVisual`'s
default export (Task 3) matches `index.jsx`'s `import('../../visual-effects/scenarios/RetryExponentialVisual')`
(Task 4) — no named-export mismatch. `VISUAL_COMPONENTS`'s new key
(`errors`) matches `data.js`'s `visual: 'errors'` value (Task 4).
