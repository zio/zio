# Tabbed Visual Effects — Round 3 (Resource safety tab) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, animated visualization — three resources (database,
cache, logger) acquired and released in guaranteed LIFO order, ported
verbatim from the source `visual-effect` project's
`effect-acquire-release` example — to the "Resource safety" tab of
zio.dev's landing-page `CodeShowcase` section, same Code/Visual toggle
pattern as rounds 1–2.

**Architecture:** Reuses the engine built by rounds 1–2 unchanged. Restores
the `scope` prop on `EffectExample` (dropped in round 1 as unused, same
pattern as round 2's `showScheduleTimeline` restoration), backed by a new
port of `VisualScope`/`useVisualScope`/`ScopeStack`/`FinalizerCard` — a
small finalizer-stack state machine and its visualization. Per the design
spec's round-3 section, the source's death-triggered `isDarkMode` accent
is deliberately dropped (see Global Constraints) — everything else is a
verbatim port.

**Tech Stack:** Same as rounds 1–2 — plain `.js`/`.jsx`, `effect`,
`motion`, `@phosphor-icons/react`, Tailwind CSS v4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-10-tabbed-visual-effects-design.md`
(see "Round 3: Resource safety tab" section, added 2026-09-11)

## Global Constraints

- Port the actual source file
  (`/home/milad/sources/typescript/visual-effect/main/src/examples/effect-acquire-release.tsx`)
  and its real dependency chain (`VisualScope.ts`, `hooks/useVisualScope.ts`,
  `components/scope/ScopeStack.tsx`, `components/scope/FinalizerCard.tsx`),
  TS types stripped, otherwise unchanged — standing rule from round 1.
- **Ruled exception:** drop the source's `isDarkMode={mainTaskState.type === "death"}`
  line and the `isDarkMode` prop it would pass to `EffectExample` entirely.
  Round 1 removed `isDarkMode` from `EffectExample` because two
  permanently-dark variants don't work on a light/dark-toggling host; this
  is a deliberate, spec-ruled omission, not a missed requirement — do not
  restore `isDarkMode` support to `EffectExample.jsx` as part of this
  round. The functional behavior (finalizers run LIFO on every exit path)
  is unaffected; only the cosmetic "flash dark red on death" accent is cut.
- The "Resource safety" tab's outer Code-view snippet in `data.js` and its
  Visual view's embedded example must show the same example — update
  `data.js`'s `code` field AND `specs/snippet-check/showcase.scala`'s
  Snippet3 to match, verified via
  `scala-cli compile specs/snippet-check/showcase.scala`. Unlike round 2,
  this tab's existing `takeaway`/`points` copy already describes this
  example well (acquire/release pairing, reverse-order cleanup, guaranteed
  on success/failure/interruption) — leave that copy as-is unless the
  final review finds it's drifted, same as round 1's Concurrency tab.
- Only the `resources` tab in `data.js` gets touched. `concurrency`,
  `errors`, `streaming`, `di` stay exactly as they are.
- Files are plain `.js`/`.jsx`, matching every existing file under
  `website/src/`.
- No automated test harness exists under `website/` — verified via the dev
  server (check for one already running — `ps aux | grep -i docusaurus` or
  `ss -ltnp | grep -E "3000|4123|4124"` — before starting a fresh one) plus
  Playwright + a system Chromium (`/nix/var/nix/profiles/default/bin/chromium`
  or `/home/milad/.nix-profile/bin/chromium` — check which exists; the
  bundled Playwright Chromium cannot launch in this sandbox).

---

### Task 1: Port `VisualScope`, `useVisualScope`, `ScopeStack`, `FinalizerCard`

**Files:**
- Create: `website/src/components/visual-effects/VisualScope.js`
- Create: `website/src/components/visual-effects/hooks/useVisualScope.js`
- Create: `website/src/components/visual-effects/scope/ScopeStack.jsx`
- Create: `website/src/components/visual-effects/scope/FinalizerCard.jsx`

**Interfaces:**
- Consumes: `taskSounds` from `../sounds/taskSounds` (already exists,
  round 1) inside `VisualScope.js`; `springs` from `../../animations`
  (already exists, round 1) inside `FinalizerCard.jsx`.
- Produces:
  - `VisualScope` (class) — `new VisualScope(id)`, `.state` (one of
    `"idle" | "acquiring" | "active" | "releasing" | "released"`),
    `.finalizers` (array of `{ id, name, timestamp, state }`, `state` one
    of `"pending" | "running" | "completed"`), `.subscribe(cb)`,
    `.setState(newState)`, `.addFinalizer(name)` (returns the new
    finalizer's id), `.runFinalizers()` (async, runs LIFO), `.reset()`.
  - `useVisualScope(scope)` — subscribes a component to a `VisualScope`'s
    changes (force-update), returns the same `scope` back.
  - `ScopeStack` (named export) — props `{ scope }`, renders the finalizer
    stack visualization.
  - `FinalizerCard` (named export) — props `{ finalizer }`, renders one
    finalizer's pending/running/completed card.

- [ ] **Step 1: Create `VisualScope.js`**

Verbatim port (TS types/interfaces stripped) of
`/home/milad/sources/typescript/visual-effect/main/src/VisualScope.ts`:

```js
// website/src/components/visual-effects/VisualScope.js
import { taskSounds } from './sounds/taskSounds';

// Ported verbatim (TS types stripped) from the source engine's
// src/VisualScope.ts.
export class VisualScope {
  constructor(id) {
    this.id = id;
    this.state = 'idle';
    this.finalizers = [];
    this.subscribers = new Set();
  }

  subscribe(callback) {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  notify() {
    this.subscribers.forEach((callback) => {
      callback();
    });
  }

  setState(newState) {
    if (this.state === newState) return;

    this.state = newState;
    this.notify();
  }

  addFinalizer(name) {
    const id = `finalizer-${name}`;
    const finalizer = {
      id,
      name,
      timestamp: Date.now(),
      state: 'pending',
    };

    this.finalizers.push(finalizer);
    taskSounds.playFinalizerCreated();

    this.notify();
    return id;
  }

  async runFinalizers() {
    this.setState('releasing');

    // Run finalizers in reverse order (LIFO)
    const finalizersToRun = [...this.finalizers].reverse();

    for (const finalizer of finalizersToRun) {
      // Abort if scope was reset while releasing
      if (this.state !== 'releasing') {
        return;
      }

      finalizer.state = 'running';
      taskSounds.playFinalizerRunning();
      this.notify();

      // Simulate finalizer execution
      await new Promise((resolve) => setTimeout(resolve, 800));

      // If scope was reset during the simulated execution, abort further processing
      if (this.state !== 'releasing') {
        return;
      }

      finalizer.state = 'completed';
      taskSounds.playFinalizerCompleted();
      this.notify();
    }

    // Only mark as released if we weren't reset in the meantime
    if (this.state === 'releasing') {
      this.setState('released');
    }
  }

  reset() {
    this.state = 'idle';
    this.finalizers = [];
    this.notify();
  }
}
```

- [ ] **Step 2: Create `hooks/useVisualScope.js`**

```js
// website/src/components/visual-effects/hooks/useVisualScope.js
import { useEffect, useReducer } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/hooks/useVisualScope.ts.
export function useVisualScope(scope) {
  const [, forceUpdate] = useReducer((x) => x + 1, 0);

  useEffect(() => {
    return scope.subscribe(forceUpdate);
  }, [scope]);

  return scope;
}
```

- [ ] **Step 3: Create `scope/FinalizerCard.jsx`**

Verbatim port of
`/home/milad/sources/typescript/visual-effect/main/src/components/scope/FinalizerCard.tsx`:

```jsx
// website/src/components/visual-effects/scope/FinalizerCard.jsx
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { springs } from '../animations';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/scope/FinalizerCard.tsx.
export function FinalizerCard({ finalizer }) {
  const isRunning = finalizer.state === 'running';
  const isCompleted = finalizer.state === 'completed';

  // Track state transitions
  const prevStateRef = useRef(finalizer.state);
  const [justCompleted, setJustCompleted] = useState(false);

  useEffect(() => {
    if (prevStateRef.current !== 'completed' && finalizer.state === 'completed') {
      setJustCompleted(true);
      const timeout = setTimeout(() => setJustCompleted(false), 600); // Match animation duration
      return () => clearTimeout(timeout);
    }
    prevStateRef.current = finalizer.state;
  }, [finalizer.state]);

  return (
    <motion.div
      initial={{
        opacity: 0,
        scale: 1.2,
        filter: 'blur(4px)',
      }}
      animate={{
        opacity: 1,
        scale: 1,
        filter: 'blur(0px)',
      }}
      exit={{
        opacity: 0,
        scale: 0.8,
        filter: 'blur(4px)',
      }}
      transition={{
        type: 'spring',
        visualDuration: 0.3,
        bounce: 0.3,
      }}
      className={`relative flex h-[52px] items-center gap-3 rounded-lg px-4 py-3 shadow-lg shadow-neutral-900 transition-colors duration-200 ${
        finalizer.state === 'pending'
          ? 'bg-neutral-800 border border-neutral-700'
          : isRunning
            ? 'bg-blue-900 border border-blue-500 '
            : 'bg-green-900 border border-green-500'
      }`}
      style={{
        minWidth: '200px',
        willChange: 'transform, opacity, filter',
        translateZ: 0,
      }}
    >
      {/* Checkbox container */}
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={springs.default}
        className={`relative flex h-6 w-6 items-center justify-center rounded border transition-colors duration-200 ${
          isCompleted
            ? 'bg-green-500 border-green-500'
            : isRunning
              ? 'bg-blue-800 border-blue-500'
              : 'bg-neutral-700 border-neutral-500'
        }`}
      >
        <AnimatePresence mode="popLayout">
          {isCompleted && (
            <motion.div
              key="check"
              initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
              animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
              exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              className="absolute inset-0 flex items-center justify-center"
            >
              <svg width="14" height="10" viewBox="0 0 14 10" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M1.5 5L5 8.5L12.5 1"
                  stroke="white"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Running animation */}
        {isRunning && (
          <motion.div
            className="absolute inset-0 rounded"
            animate={{
              scale: [1, 1.3, 1],
              opacity: [0.3, 0, 0.3],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
            style={{
              background: 'radial-gradient(circle, rgba(59, 130, 246, 0.4) 0%, transparent 70%)',
            }}
          />
        )}
      </motion.div>
      {/* Running pulse effect */}
      {isRunning && (
        <motion.div
          className="absolute inset-0 rounded-lg"
          initial={{ opacity: 0 }}
          animate={{
            opacity: [0, 0.3, 0],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
          style={{
            background: 'radial-gradient(ellipse at center, rgba(59, 130, 246, 0.2) 0%, transparent 70%)',
          }}
        />
      )}

      {/* Completion flash */}
      {justCompleted && (
        <motion.div
          className="absolute inset-0 rounded-lg"
          initial={{
            opacity: 0.8,
            scale: 1,
          }}
          animate={{
            opacity: 0,
            scale: 1.05,
          }}
          transition={{
            duration: 0.6,
            ease: 'easeOut',
          }}
          style={{
            background: 'radial-gradient(ellipse at center, rgba(34, 197, 94, 0.4) 0%, transparent 70%)',
          }}
        />
      )}

      {/* Content */}
      <div className="relative z-10">
        <motion.span
          className={`font-mono text-base font-medium ${
            finalizer.state === 'pending'
              ? 'text-white'
              : isRunning
                ? 'text-blue-300'
                : 'text-green-300/90'
          }`}
          animate={{
            opacity: 1,
          }}
          transition={{ duration: 0.2 }}
        >
          {finalizer.name}
        </motion.span>
      </div>
    </motion.div>
  );
}
```

- [ ] **Step 4: Create `scope/ScopeStack.jsx`**

Verbatim port of
`/home/milad/sources/typescript/visual-effect/main/src/components/scope/ScopeStack.tsx`:

```jsx
// website/src/components/visual-effects/scope/ScopeStack.jsx
import { CaretRightIcon } from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'motion/react';
import { useLayoutEffect, useRef, useState } from 'react';
import { useVisualScope } from '../hooks/useVisualScope';
import { FinalizerCard } from './FinalizerCard';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/scope/ScopeStack.tsx.
export function ScopeStack({ scope }) {
  useVisualScope(scope);
  const containerRef = useRef(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const cardWidth = 200; // Based on minWidth in FinalizerCard

  useLayoutEffect(() => {
    const updateWidth = () => {
      if (containerRef.current) {
        setContainerWidth(containerRef.current.offsetWidth);
      }
    };

    updateWidth();

    const resizeObserver = new ResizeObserver(updateWidth);
    if (containerRef.current) {
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, []);

  const pendingFinalizers = scope.finalizers.filter((f) => f.state === 'pending');
  const completedFinalizers = scope.finalizers.filter((f) => f.state === 'completed');

  // Don't render until we have container width
  if (containerWidth === 0) {
    return (
      <div
        ref={containerRef}
        className="relative flex h-[88px] items-center justify-between rounded-xl border border-dashed border-neutral-700 m-4"
      >
        <div className="absolute inset-0 flex items-center justify-center text-neutral-700">
          <div className="flex items-center gap-2">
            {/* Left chevrons */}
            <div className="flex gap-1">
              {[0, 1, 2].map((i) => (
                <motion.span
                  key={`left-${i}`}
                  className="text-neutral-600"
                  animate={{
                    opacity: [0.2, 1, 0.2],
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                    delay: i * 0.15,
                    ease: 'easeInOut',
                  }}
                >
                  ›
                </motion.span>
              ))}
            </div>
            <span className="tracking-widest">FINALIZERS!</span>

            {/* Right chevrons */}
            <div className="flex gap-1">
              {[3, 4, 5].map((i) => (
                <motion.span
                  key={`right-${i}`}
                  className="text-neutral-600"
                  animate={{
                    opacity: [0.2, 1, 0.2],
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                    delay: i * 0.15,
                    ease: 'easeInOut',
                  }}
                >
                  ›
                </motion.span>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative flex h-[88px] items-center justify-between">
      <div className="absolute inset-0 flex items-center justify-center text-neutral-700">
        <div className="flex items-center gap-2">
          {/* Left chevrons */}
          <div className="flex gap-1">
            {[0, 1, 2].map((i) => (
              <motion.span
                key={`left-${i}`}
                className="text-neutral-600"
                animate={{
                  opacity: [0.2, 1, 0.2],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  delay: i * 0.1,
                  ease: 'easeInOut',
                }}
              >
                <CaretRightIcon size={16} weight="fill" />
              </motion.span>
            ))}
          </div>

          <span className="tracking-wider">FINALIZERS</span>

          {/* Right chevrons */}
          <div className="flex gap-1">
            {[4, 5, 6].map((i) => (
              <motion.span
                key={`right-${i}`}
                className="text-neutral-600"
                animate={{
                  opacity: [0.2, 1, 0.2],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  delay: i * 0.15,
                  ease: 'easeInOut',
                }}
              >
                <CaretRightIcon size={16} weight="fill" />
              </motion.span>
            ))}
          </div>
        </div>
      </div>
      <AnimatePresence mode="popLayout">
        {scope.finalizers.map((finalizer) => {
          const isRunning = finalizer.state === 'running';
          const isPending = finalizer.state === 'pending';
          const isCompleted = finalizer.state === 'completed';

          // Find indices
          const pendingIndex = pendingFinalizers.indexOf(finalizer);
          const completedIndex = completedFinalizers.indexOf(finalizer);

          // Calculate x position from left edge
          let xPosition = 0;
          let zIndex = 10;

          if (isRunning) {
            // Center the running finalizer
            xPosition = (containerWidth - cardWidth) / 2;
            zIndex = 20;
          } else if (isPending) {
            // Stack pending on the left
            xPosition = pendingIndex * 35 + 16;
            zIndex = 10 + pendingIndex;
          } else if (isCompleted) {
            // Stack completed on the right (calculate from left)
            const rightOffset = (completedFinalizers.length - 1 - completedIndex) * 35 + 16;
            xPosition = containerWidth - rightOffset - cardWidth;
            zIndex = 10 - completedIndex;
          }

          const scale = isRunning ? 1.05 : 1;

          return (
            <motion.div
              key={finalizer.id}
              layoutId={finalizer.id}
              className="absolute"
              style={{
                zIndex,
                willChange: 'transform',
                translateZ: 0,
              }}
              animate={{
                x: xPosition,
                scale,
              }}
              transition={{
                type: 'spring',
                visualDuration: 0.5,
                bounce: 0.0,
              }}
            >
              <FinalizerCard finalizer={finalizer} />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
```

- [ ] **Step 5: Verify headlessly**

Run (from `website/`):

```bash
node --input-type=module -e "
import { VisualScope } from './src/components/visual-effects/VisualScope.js';
const s = new VisualScope('test');
s.subscribe(() => console.log('state ->', s.state, 'finalizers:', s.finalizers.map(f => f.state)));
s.setState('acquiring');
s.addFinalizer('Close database');
s.addFinalizer('Flush cache');
await s.runFinalizers();
console.log('final state:', s.state);
console.log('final finalizer order (LIFO — cache should complete before database):', s.finalizers.map(f => f.name));
"
```

Expected: prints state transitions ending in `final state: released`, and
the finalizer completion order shows `Flush cache` running/completing
before `Close database` (LIFO — last added, first run). This will take
~1.6s (two 800ms simulated finalizer runs) — that's expected, not a hang.

- [ ] **Step 6: Commit**

```bash
cd website && git add src/components/visual-effects/VisualScope.js src/components/visual-effects/hooks/useVisualScope.js src/components/visual-effects/scope/FinalizerCard.jsx src/components/visual-effects/scope/ScopeStack.jsx && git commit -m "feat(website): port VisualScope, useVisualScope, ScopeStack, FinalizerCard"
```

---

### Task 2: Restore the `scope` prop on `EffectExample`

**Files:**
- Modify: `website/src/components/visual-effects/EffectExample.jsx`

**Interfaces:**
- Consumes: `ScopeStack` (named export) from `./scope/ScopeStack` (Task 1).
- Produces: `EffectExample` gains a `scope` prop (a `VisualScope` instance
  or undefined) that, when present, renders `<ScopeStack scope={scope} />`
  in a bordered row, positioned after the Schedule timeline block (round
  2) and before the Code block.

- [ ] **Step 1: Add the import**

Current top-of-file imports end with:

```js
import { HeaderView } from './HeaderView';
import { ScheduleTimeline } from './ScheduleTimeline';
```

Add:

```js
import { HeaderView } from './HeaderView';
import { ScheduleTimeline } from './ScheduleTimeline';
import { ScopeStack } from './scope/ScopeStack';
```

- [ ] **Step 2: Add `scope` to the destructured props**

Current:

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

Change to:

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
  scope,
  showScheduleTimeline,
  variant,
}) {
```

- [ ] **Step 3: Render `ScopeStack` when `scope` is provided**

The file currently has this block (the Schedule timeline block from round
2), immediately followed by the "Code block" comment/div:

```jsx
      {/* Schedule timeline (if provided) */}
      {showScheduleTimeline && effects[0] && resultEffect && (
        <motion.div
          initial={{ borderColor: borderColorValue }}
          animate={{ borderColor: borderColorValue }}
          transition={standardTransition}
          className="border-b"
        >
          <ScheduleTimeline
            baseEffect={effects[0]}
            repeatEffect={resultEffect}
            pixelsPerSecond={50}
          />
        </motion.div>
      )}

      {/* Code block. Fixed dark background, independent of the card's
```

Insert a new block between them, so it reads:

```jsx
      {/* Schedule timeline (if provided) */}
      {showScheduleTimeline && effects[0] && resultEffect && (
        <motion.div
          initial={{ borderColor: borderColorValue }}
          animate={{ borderColor: borderColorValue }}
          transition={standardTransition}
          className="border-b"
        >
          <ScheduleTimeline
            baseEffect={effects[0]}
            repeatEffect={resultEffect}
            pixelsPerSecond={50}
          />
        </motion.div>
      )}

      {/* Scope visualization (if provided) */}
      {scope && (
        <motion.div
          initial={{ borderColor: borderColorValue }}
          animate={{ borderColor: borderColorValue }}
          transition={standardTransition}
          className="border-b"
        >
          <ScopeStack scope={scope} />
        </motion.div>
      )}

      {/* Code block. Fixed dark background, independent of the card's
```

(Leave the memo comparator at the bottom of the file untouched — same
reasoning as round 2's `showScheduleTimeline`, `scope` is fixed per mount.)

- [ ] **Step 4: Verify headlessly**

Confirm the dev server (find it per Global Constraints' note, or start a
fresh one on a free port) compiles cleanly after this edit:
`curl -s -o /dev/null -w "%{http_code}\n" http://localhost:<port>/` should
print `200`, with no new compile errors in the server's output.

- [ ] **Step 5: Commit**

```bash
cd website && git add src/components/visual-effects/EffectExample.jsx && git commit -m "feat(website): restore scope prop on EffectExample"
```

---

### Task 3: Port the `effect-acquire-release` scenario

**Files:**
- Create: `website/src/components/visual-effects/scenarios/AcquireReleaseVisual.jsx`

**Interfaces:**
- Consumes: `useVisualEffect` from `../hooks/useVisualEffects`; `getDelay`
  from `../examples/helpers`; `StringResult` from `../renderers`;
  `useVisualScope` from `../hooks/useVisualScope` (Task 1); `VisualScope`
  from `../VisualScope` (Task 1); `EffectExample` from `../EffectExample`
  (Task 2's `scope` prop); `useVisualEffectState` from `../VisualEffect`;
  `Effect` from `effect`.
- Produces: `AcquireReleaseVisual` (default export), a zero-prop
  component — this is what Task 4 wires into `CodeShowcase`.

- [ ] **Step 1: Create the file**

This is a verbatim port (TS types stripped) of
`/home/milad/sources/typescript/visual-effect/main/src/examples/effect-acquire-release.tsx`,
**except**: the source's `isDarkMode` computation and its use as an
`EffectExample` prop are dropped entirely, per this plan's Global
Constraints (round 1 removed `isDarkMode` support from `EffectExample`,
and this is a deliberate, spec-ruled omission, not something to restore).

```jsx
// website/src/components/visual-effects/scenarios/AcquireReleaseVisual.jsx
import { Effect } from 'effect';
import { useEffect, useMemo, useRef } from 'react';
import { EffectExample } from '../EffectExample';
import { getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { useVisualScope } from '../hooks/useVisualScope';
import { StringResult } from '../renderers';
import { VisualScope } from '../VisualScope';

// Ported verbatim (TS types stripped, otherwise unchanged) from the source
// engine's src/examples/effect-acquire-release.tsx — the actual
// "ZIO.acquireRelease" example from the visual-effect project. The
// source's isDarkMode={mainTaskState.type === "death"} accent is
// deliberately dropped here (see the design spec's "Round 3" section) —
// EffectExample no longer supports isDarkMode (round 1 removed it because
// two permanently-dark variants don't work on a light/dark-toggling host).

// Simulate resource acquisition with cleanup
function acquireDatabase() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      connection: 'DATABASE',
      close: () => console.log('Database connection closed'),
    };
  });
}

function acquireCache() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      connection: 'CACHE',
      close: () => console.log('Cache connection closed'),
    };
  });
}

function acquireLogger() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      file: 'LOGGER',
      close: () => console.log('Logger file closed'),
    };
  });
}

export default function AcquireReleaseVisual() {
  const scope = useMemo(() => new VisualScope('resourceScope'), []);
  const runCountRef = useRef(0);
  useVisualScope(scope);

  // Individual resource tasks
  const dbTask = useVisualEffect(
    'database',
    () =>
      acquireDatabase().pipe(
        Effect.map((db) => new StringResult(db.connection)),
        Effect.tap(() => scope.addFinalizer('Close database')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  const cacheTask = useVisualEffect(
    'cache',
    () =>
      acquireCache().pipe(
        Effect.map((cache) => new StringResult(cache.connection)),
        Effect.tap(() => scope.addFinalizer('Flush cache')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  const loggerTask = useVisualEffect(
    'logger',
    () =>
      acquireLogger().pipe(
        Effect.map((logger) => new StringResult(logger.file)),
        Effect.tap(() => scope.addFinalizer('Close log file')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  // Main effect that uses scoped resources
  const mainTask = useVisualEffect(
    'result',
    () =>
      Effect.gen(function* () {
        runCountRef.current += 1;
        const currentRun = runCountRef.current;

        scope.setState('acquiring');

        yield* dbTask.effect;
        yield* cacheTask.effect;
        yield* loggerTask.effect;

        scope.setState('active');

        yield* Effect.sleep(getDelay(1000, 1500));

        const cyclePosition = (currentRun - 1) % 3;

        if (cyclePosition === 0) {
          return new StringResult('Work completed!');
        } else if (cyclePosition === 1) {
          return yield* Effect.fail('Oops.');
        } else {
          return yield* Effect.die('BANG!');
        }
      }),
    { deps: [dbTask, cacheTask, loggerTask, scope] },
  );

  // Handle scope cleanup when main task completes
  useEffect(() => {
    const unsubscribe = mainTask.subscribe(() => {
      if (
        (mainTask.state.type === 'completed' ||
          mainTask.state.type === 'interrupted' ||
          mainTask.state.type === 'failed' ||
          mainTask.state.type === 'death') &&
        scope.state !== 'released'
      ) {
        // Run finalizers (guaranteed cleanup!)
        scope.runFinalizers();
      } else if (mainTask.state.type === 'idle') {
        // Reset scope when task resets
        scope.reset();
      }
    });

    return unsubscribe;
  }, [mainTask, scope]);

  const codeSnippet = `val makeDatabase = ZIO.acquireRelease(connectDatabase())(db => ZIO.succeed(db.close()))
val makeCache = ZIO.acquireRelease(connectCache())(cache => ZIO.succeed(cache.flush()))
val makeLogger = ZIO.acquireRelease(openLogFile())(file => ZIO.succeed(file.close()))

val result = ZIO.scoped {
  for {
    db     <- makeDatabase
    cache  <- makeCache
    logger <- makeLogger
    r      <- doWork(db, cache, logger)
  } yield r
}`;

  const taskHighlightMap = useMemo(
    () => ({
      database: { text: 'makeDatabase' },
      cache: { text: 'makeCache' },
      logger: { text: 'makeLogger' },
      result: { text: 'result' },
    }),
    [],
  );

  return (
    <EffectExample
      name="ZIO.acquireRelease"
      description="Acquire resources with guaranteed cleanup"
      code={codeSnippet}
      effects={useMemo(() => [dbTask, cacheTask, loggerTask], [dbTask, cacheTask, loggerTask])}
      resultEffect={mainTask}
      effectHighlightMap={taskHighlightMap}
      scope={scope}
      exampleId="effect-acquire-release"
    />
  );
}
```

Note: the `useVisualEffectState`/`isDarkMode` line from the source is
simply absent above — there is nothing to import or compute since the
feature is dropped, not stubbed.

- [ ] **Step 2: Verify imports resolve to real exports**

Read `../hooks/useVisualEffects.js`, `../examples/helpers.js`,
`../renderers/index.js` (or wherever `StringResult` is re-exported from),
`../hooks/useVisualScope.js`, `../VisualScope.js`, and `../EffectExample.jsx`
and confirm `useVisualEffect`, `getDelay`, `StringResult`, `useVisualScope`,
`VisualScope`, and `EffectExample` are all actually exported with those
names.

- [ ] **Step 3: Commit**

```bash
cd website && git add src/components/visual-effects/scenarios/AcquireReleaseVisual.jsx && git commit -m "feat(website): add AcquireReleaseVisual scenario component"
```

(No standalone runtime verification here — same as prior rounds, this
component has no meaningful render target until Task 4 mounts it.)

---

### Task 4: Wire into `CodeShowcase` and keep the Code snippet in parity

**Files:**
- Modify: `website/src/components/sections/CodeShowcase/data.js`
- Modify: `website/src/components/sections/CodeShowcase/index.jsx`
- Modify: `specs/snippet-check/showcase.scala`

**Interfaces:**
- Consumes: `AcquireReleaseVisual` (Task 3, loaded via `React.lazy` inside
  `VISUAL_COMPONENTS`, same pattern as the existing `concurrency`/`errors`
  entries).
- Produces: the landing page's Resource safety tab shows a "Code / Visual"
  toggle, identical in behavior to the other two tabs'.

- [ ] **Step 1: Add the `visual` field and update the Code snippet in `data.js`**

The `resources` entry currently reads (in full):

```js
  {
    value: 'resources',
    label: 'Resource safety',
    takeaway:
      'Acquire and release are paired at the type level — leaks are impossible, even under interruption.',
    points: [
      'Acquire and release are paired, so cleanup always runs.',
      'Many resources compose and close in reverse order.',
      'Guaranteed on success, failure, or interruption alike.',
    ],
    code: `def analyze(path: String): ZIO[Any, IOException, Stats] =
  ZIO.acquireReleaseWith(openFile(path))(closeFile): file =>
    computeStats(file)

// Or compose many resources with Scope
val app: ZIO[Any, Throwable, Unit] =
  ZIO.scoped:
    for
      db   <- Database.connect
      file <- logFile("app.log")
      _    <- runMigrations(db, file)
    yield () // released in reverse order — even on failure or interruption`,
  },
```

Change it to (only `visual` added and `code` replaced — `takeaway`/`points`
already describe this example well, per the spec's round-3 section, so
they stay):

```js
  {
    value: 'resources',
    label: 'Resource safety',
    visual: 'resources',
    takeaway:
      'Acquire and release are paired at the type level — leaks are impossible, even under interruption.',
    points: [
      'Acquire and release are paired, so cleanup always runs.',
      'Many resources compose and close in reverse order.',
      'Guaranteed on success, failure, or interruption alike.',
    ],
    code: `val makeDatabase = ZIO.acquireRelease(connectDatabase())(db => ZIO.succeed(db.close()))
val makeCache = ZIO.acquireRelease(connectCache())(cache => ZIO.succeed(cache.flush()))
val makeLogger = ZIO.acquireRelease(openLogFile())(file => ZIO.succeed(file.close()))

val result = ZIO.scoped {
  for {
    db     <- makeDatabase
    cache  <- makeCache
    logger <- makeLogger
    r      <- doWork(db, cache, logger)
  } yield r
}`,
  },
```

- [ ] **Step 2: Register the lazy-loaded component in `index.jsx`**

`VISUAL_COMPONENTS` currently reads:

```js
const VISUAL_COMPONENTS = {
  concurrency: React.lazy(() => import('../../visual-effects/scenarios/RaceVisual')),
  errors: React.lazy(() => import('../../visual-effects/scenarios/RetryExponentialVisual')),
};
```

Change to:

```js
const VISUAL_COMPONENTS = {
  concurrency: React.lazy(() => import('../../visual-effects/scenarios/RaceVisual')),
  errors: React.lazy(() => import('../../visual-effects/scenarios/RetryExponentialVisual')),
  resources: React.lazy(() => import('../../visual-effects/scenarios/AcquireReleaseVisual')),
};
```

- [ ] **Step 3: Update the compile-checked spec**

Read `specs/snippet-check/showcase.scala` in full first. `Snippet3`
currently reads:

```scala
// ── Snippet 3: Resource safety ──────────────────────────────────────────
object Snippet3 {
  def analyze(path: String): ZIO[Any, IOException, Stats] =
    ZIO.acquireReleaseWith(openFile(path))(closeFile): file =>
      computeStats(file)

  // Or compose many resources with Scope
  val app: ZIO[Any, Throwable, Unit] =
    ZIO.scoped:
      for
        db   <- Database.connect
        file <- logFile("app.log")
        _    <- runMigrations(db, file)
      yield () // released in reverse order — even on failure or interruption
}
```

Change it to:

```scala
// ── Snippet 3: Resource safety ──────────────────────────────────────────
// Matches the "ZIO.acquireRelease" example mounted in the Resource safety
// tab's Visual view (website/src/components/visual-effects/scenarios/AcquireReleaseVisual.jsx)
// — Visual and Code must show the same example.
object Snippet3 {
  val makeDatabase = ZIO.acquireRelease(connectDatabase())(db => ZIO.succeed(db.close()))
  val makeCache     = ZIO.acquireRelease(connectCache())(cache => ZIO.succeed(cache.flush()))
  val makeLogger    = ZIO.acquireRelease(openLogFile())(file => ZIO.succeed(file.close()))

  val result: ZIO[Any, Throwable, Stats] =
    ZIO.scoped:
      for
        db     <- makeDatabase
        cache  <- makeCache
        logger <- makeLogger
        r      <- doWork(db, cache, logger)
      yield r
}
```

Add stubs near the other stub `def`s for the new names this snippet
introduces (`connectDatabase`, `connectCache`, `openLogFile`, `doWork`,
plus small result types for the `db`/`cache` objects' `.close()`/`.flush()`
calls):

```scala
class DbConn { def close(): Unit = () }
class CacheConn { def flush(): Unit = () }
def connectDatabase(): Task[DbConn] = ZIO.succeed(new DbConn)
def connectCache(): Task[CacheConn] = ZIO.succeed(new CacheConn)
def openLogFile(): IO[IOException, File] = ZIO.succeed(new File)
def doWork(db: DbConn, cache: CacheConn, logger: File): Task[Stats] = ZIO.succeed(Stats())
```

Then check whether `openFile`, `closeFile`, `computeStats`, `analyze`'s
stub dependencies (`openFile`/`closeFile`/`computeStats` — check if any of
these three are referenced anywhere else in the file first) are now
unused. `grep -n "openFile\|closeFile\|computeStats" specs/snippet-check/showcase.scala`
from the repo root — if they're only used by the old `Snippet3` (likely,
same pattern as round 1/2's stub cleanups), remove those three `def`
lines. Leave `Stats`, `File`, `IOException` import, and every other
existing stub (`User`, `Event`, `Database`, `Logger`, `runFast`,
`attemptParallelPark`, etc.) alone.

- [ ] **Step 4: Compile-check**

Run (from the repo root):

```bash
scala-cli compile specs/snippet-check/showcase.scala
```

Expected: compiles with no errors.

- [ ] **Step 5: Manual verification**

The dev server should already be running (see Global Constraints for how
to find it) — if not, start it. Then:

1. Load the homepage, scroll to "The ZIO Way".
2. Click the "Resource safety" tab. Confirm a "Visual / Code" toggle now
   appears (it shouldn't for Streaming / Dependency Injection).
3. In Visual mode: confirm the "ZIO.acquireRelease" header renders, three
   nodes (database/cache/logger) plus a result node, and a "FINALIZERS"
   stack row below them. Click the header to run it — the three resource
   nodes acquire in sequence, the finalizer stack fills with 3 pending
   cards as each resource is acquired, then after the main work completes
   the finalizers run in **reverse order** (logger card first, then cache,
   then database — LIFO) with each card animating from pending → running →
   completed. Run it 2-3 times: the outcome should cycle
   success → failure → defect → success (visible on the result node),
   while cleanup runs identically every time regardless of outcome.
4. Click "Code": confirm it shows the same `makeDatabase`/`makeCache`/
   `makeLogger`/`ZIO.scoped` snippet now on the page (not the old
   `analyze`/`ZIO.acquireReleaseWith` text).
5. Switch theme to dark and back to light; confirm the card, node colors,
   and the finalizer stack/cards remain legible in both. Pay particular
   attention to the `ScopeStack` empty-state placeholder (the dashed-border
   "FINALIZERS!" ghost box shown before any run) and the chevron
   decorations — these use fixed `neutral-6/7/800` colors ported verbatim
   and were not part of any prior round's theme fix, so check they're
   actually legible on the light card rather than assuming they are.
6. Confirm the Concurrency and Error handling tabs are unchanged.

Use Playwright + system Chromium if a screenshot is useful for confirming
visually (see Global Constraints for the chromium path); otherwise a
careful DOM/console-error check is enough. Note in your report which
verification you actually performed.

- [ ] **Step 6: Commit**

```bash
cd /home/milad/sources/scala/zio-2.x-worktrees/zio-visual-effect && git add website/src/components/sections/CodeShowcase/data.js website/src/components/sections/CodeShowcase/index.jsx specs/snippet-check/showcase.scala && git commit -m "feat(website): wire AcquireReleaseVisual into the Resource safety tab"
```

---

## Plan Self-Review

**Spec coverage:** Every element of the spec's "Round 3: Resource safety
tab" section is implemented: `VisualScope`/`useVisualScope`/`ScopeStack`/
`FinalizerCard` ports (Task 1), the `scope` prop restoration (Task 2), the
actual `effect-acquire-release` example with `isDarkMode` deliberately
omitted per the ruling (Task 3), and the Code/Visual parity rule applied to
`data.js` + `specs/snippet-check/showcase.scala` (Task 4).

**Placeholder scan:** No TBD/TODO markers; every step has runnable code or
an exact shell command.

**Type consistency:** `VisualScope`'s method names/shapes (Task 1) match
`AcquireReleaseVisual.jsx`'s usage (Task 3) exactly: `.addFinalizer(name)`,
`.setState(...)`, `.runFinalizers()`, `.reset()`, `.subscribe(cb)`.
`ScopeStack`'s prop name (`scope`) matches `EffectExample.jsx`'s Task 2
usage (`<ScopeStack scope={scope} />`). `AcquireReleaseVisual`'s default
export (Task 3) matches `index.jsx`'s `import('../../visual-effects/scenarios/AcquireReleaseVisual')`
(Task 4). `VISUAL_COMPONENTS`'s new key (`resources`) matches `data.js`'s
`visual: 'resources'` value (Task 4). The dropped `isDarkMode` is
consistently absent from both `EffectExample.jsx` (never re-added) and
`AcquireReleaseVisual.jsx` (never computed or passed) — no dangling
reference either direction.
