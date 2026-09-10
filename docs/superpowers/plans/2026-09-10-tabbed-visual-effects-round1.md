# Tabbed Visual Effects — Round 1 (Concurrency tab) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, animated visualization (3 parallel tasks, one fails, the
others visibly interrupt) to the "Concurrency" tab of zio.dev's landing-page
`CodeShowcase` section, toggleable against the existing static code view.

**Architecture:** A small, self-contained engine
(`website/src/components/visual-effects/`) wraps real `effect`-npm-package
Effects in an observable `VisualEffect` class; a Motion-animated `EffectNode`
renders each task's live state. `CodeShowcase` gains a "Code / Visual" toggle
that, only for tabs with a registered visual, lazy-loads the scenario
component through Docusaurus's `<BrowserOnly>` (the animation can't run
during static-site prerender).

**Tech Stack:** React (Docusaurus 3, plain `.js`/`.jsx` — the codebase has no
`.ts`/`.tsx` files anywhere, so round 1 follows that convention rather than
introducing TypeScript), `effect` (real fiber-based Effect runtime, npm
package `effect`), `motion` (npm package `motion`, imported as
`motion/react`), `@phosphor-icons/react` (icons), Tailwind CSS v4 (already
active site-wide via `@tailwindcss/postcss`, confirmed by existing
`className="... dark:text-zinc-400"` usage in `Features/index.jsx`).

**Spec:** `docs/superpowers/specs/2026-09-10-tabbed-visual-effects-design.md`

## Global Constraints

- New tree lives at `website/src/components/visual-effects/`. Only the
  `concurrency` tab in `CodeShowcase/data.js` gets a `visual` in round 1; the
  other four tabs (Error handling, Resource safety, Streaming, Dependency
  Injection) are untouched.
- No sound, no notifications, no `death` state, no parent/child effect
  nesting — round 1's scenario doesn't need them (spec's "left out" list).
- Files are plain `.js`/`.jsx`, matching every existing file under
  `website/src/` (no `.ts`/`.tsx` precedent to follow).
- Colors are semantic state colors (`idle`/`running`/`completed`/`failed`/
  `interrupted`), not brand colors — carried over as plain hex values, not
  Tailwind CSS variables (avoids depending on whether Tailwind v4's palette
  custom properties are actually emitted by this site's build).
- The visual component must never execute at Docusaurus's Node.js
  server-render time (`docusaurus build` prerenders pages in Node) — it is
  loaded only inside `<BrowserOnly>`'s children function, via `require()`
  there (not a top-level `import`), which is the documented Docusaurus
  pattern for browser-only libraries.
- `website/yarn.lock` and `website/node_modules` are gitignored (repo-root
  `.gitignore` lines `website/node_modules`, `website/yarn.lock`,
  `website/package-lock.json`) — no lockfile to commit after `yarn install`.
- No automated test harness exists under `website/` (Docusaurus site,
  verified via `yarn build`/manual review — confirmed by absence of any
  `*.test.*` file or test runner dependency in `website/package.json`).
  Round 1 is verified by: (a) one throwaway Node script that exercises the
  core interrupt-on-failure logic headlessly (no React/DOM needed — the
  riskiest assumption, checked before any UI is built on top of it), and
  (b) manual browser checks (`yarn start`) once the UI is wired up. This
  matches `CodeShowcase`'s own existing convention (it has no tests either).

---

### Task 1: Add engine dependencies, validate the interrupt-on-failure assumption

**Files:**
- Modify: `website/package.json`

**Interfaces:**
- Produces: `effect`, `motion`, `@phosphor-icons/react` available as
  resolvable node modules for every later task in this plan.

- [ ] **Step 1: Add the three dependencies to `website/package.json`**

In the `"dependencies"` block, insert `"@phosphor-icons/react": "^2.1.10"`
alphabetically after `"@mdx-js/react": "^3.0.1"` and before
`"@tailwindcss/postcss": "4.3.3"`:

```json
    "@mdx-js/react": "^3.0.1",
    "@phosphor-icons/react": "^2.1.10",
    "@tailwindcss/postcss": "4.3.3",
```

Insert `"effect": "^3.19.14"` alphabetically after
`"docusaurus-plugin-llms": "0.6.0"` and before `"highlight.js": "11.12.0"`:

```json
    "docusaurus-plugin-llms": "0.6.0",
    "effect": "^3.19.14",
    "highlight.js": "11.12.0",
```

Insert `"motion": "^12.27.1"` alphabetically after
`"highlight.js": "11.12.0"` and before `"node-fetch": "^3.3.2"`:

```json
    "highlight.js": "11.12.0",
    "motion": "^12.27.1",
    "node-fetch": "^3.3.2",
```

- [ ] **Step 2: Install**

Run (from `website/`): `yarn install`
Expected: completes without error; `website/node_modules/effect`,
`website/node_modules/motion`, and
`website/node_modules/@phosphor-icons/react` exist.

- [ ] **Step 3: Validate that `Effect.all` with unbounded concurrency actually interrupts siblings on failure**

This is round 1's key design assumption (spec: "if one fails, the rest are
interrupted") — check it headlessly, before building any UI on top of it.

Create a throwaway file `website/.scratch-verify-effect-all.mjs`:

```js
import { Effect } from "effect";

const log = (label) => (value) => Effect.sync(() => console.log(label, value));

const fetchUsers = Effect.sleep(900).pipe(
  Effect.tap(() => Effect.sync(() => console.log("fetchUsers: completed"))),
  Effect.onInterrupt(() => Effect.sync(() => console.log("fetchUsers: interrupted"))),
);

const fetchOrders = Effect.sleep(1300).pipe(
  Effect.tap(() => Effect.sync(() => console.log("fetchOrders: completed"))),
  Effect.onInterrupt(() => Effect.sync(() => console.log("fetchOrders: interrupted"))),
);

const fetchProfile = Effect.sleep(600).pipe(
  Effect.flatMap(() => Effect.fail(new Error("Profile fetch failed"))),
  Effect.tapError(() => Effect.sync(() => console.log("fetchProfile: failed"))),
);

const program = Effect.all([fetchUsers, fetchOrders, fetchProfile], {
  concurrency: "unbounded",
});

Effect.runPromiseExit(program).then((exit) => {
  console.log("exit:", exit._tag);
});
```

- [ ] **Step 4: Run it**

Run (from `website/`): `node .scratch-verify-effect-all.mjs`
Expected output (order of the two interrupted lines may vary, but both must
appear): `fetchProfile: failed`, `fetchUsers: interrupted`,
`fetchOrders: interrupted`, `exit: Failure`.

If instead `fetchUsers: completed` / `fetchOrders: completed` print, the
concurrency option is wrong — stop and re-check the `effect` version's
`Effect.all` options API before proceeding to Task 2.

- [ ] **Step 5: Delete the throwaway script**

Run: `rm website/.scratch-verify-effect-all.mjs`

- [ ] **Step 6: Commit**

```bash
cd website && git add package.json && git commit -m "feat(website): add effect/motion/phosphor-icons deps for landing-page visuals"
```

---

### Task 2: Port the trimmed VisualEffect engine

**Files:**
- Create: `website/src/components/visual-effects/colors.js`
- Create: `website/src/components/visual-effects/VisualEffect.js`

**Interfaces:**
- Consumes: `effect` package (`Effect`, `Fiber`), `react` (`useSyncExternalStore`) — both from Task 1.
- Produces:
  - `colors.js`: `TASK_COLORS` — object with keys `idle | running | completed | failed | interrupted`, each a hex color string.
  - `VisualEffect.js`: `VisualEffect` class (`.name`, `.state`, `.effect` getter, `.subscribe(listener)`, `.run()`, `.interrupt()`, `.reset()`), `visualEffect(name, effectValue)` factory, `useVisualEffectState(visualEffect)` hook returning the live `EffectState` (`{ type: "idle" | "running" | "completed", result } | { type: "failed", error } | { type: "interrupted" }`).

- [ ] **Step 1: Create `colors.js`**

```js
// website/src/components/visual-effects/colors.js

// State colors for VisualEffect nodes. Semantic (blue=running,
// green=completed, red=failed, orange=interrupted), not brand colors, so
// they don't need to match zio.dev's red/amber accent palette.
export const TASK_COLORS = {
  idle: '#64748b',
  running: '#3b82f6',
  completed: '#15803d',
  failed: '#ef4444',
  interrupted: '#f97316',
};
```

- [ ] **Step 2: Create `VisualEffect.js`**

```js
// website/src/components/visual-effects/VisualEffect.js
import { Effect, Fiber } from 'effect';
import { useSyncExternalStore } from 'react';

// Trimmed from the source visual-effect engine: no sound hooks, no
// parent/child notification service, no showTimer, no `death` state —
// round 1's scenario doesn't use any of them (see spec's "left out" list).
const VALID_TRANSITIONS = {
  idle: new Set(['running', 'idle']),
  running: new Set(['completed', 'failed', 'interrupted', 'idle', 'running']),
  completed: new Set(['idle', 'running', 'completed']),
  failed: new Set(['idle', 'running', 'failed']),
  interrupted: new Set(['idle', 'running', 'interrupted']),
};

export class VisualEffect {
  constructor(name, effectValue) {
    this.name = name;
    this._effect = effectValue;
    this.state = { type: 'idle' };
    this.listeners = new Set();
    this.fiber = null;
    this.isResetting = false;
  }

  // Returns an Effect that updates this instance's state as it runs.
  get effect() {
    if (this.state.type === 'completed') return Effect.succeed(this.state.result);
    if (this.state.type === 'failed') return Effect.fail(this.state.error);

    const self = this;
    return Effect.gen(function* () {
      self.setState({ type: 'running' });
      return yield* self._effect;
    }).pipe(
      Effect.tap((result) =>
        Effect.sync(() => {
          self.setState({ type: 'completed', result });
        }),
      ),
      Effect.tapError((error) =>
        Effect.sync(() => {
          self.setState({ type: 'failed', error });
        }),
      ),
      Effect.onInterrupt(() =>
        Effect.sync(() => {
          if (!self.isResetting) self.setState({ type: 'interrupted' });
        }),
      ),
    );
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  setState(newState) {
    if (this.isResetting) return;
    const validNext = VALID_TRANSITIONS[this.state.type];
    if (!validNext || !validNext.has(newState.type)) return;
    this.state = newState;
    this.listeners.forEach((listener) => listener());
  }

  async run() {
    try {
      this.fiber = Effect.runFork(this.effect);
      await Effect.runPromise(Fiber.await(this.fiber));
    } catch {
      // Failure/interruption is already recorded via the effect pipeline above.
    } finally {
      this.fiber = null;
    }
  }

  interrupt() {
    if (this.state.type !== 'running') return;
    const fiberToInterrupt = this.fiber;
    this.fiber = null;
    this.setState({ type: 'interrupted' });
    if (fiberToInterrupt) Effect.runFork(Fiber.interrupt(fiberToInterrupt));
  }

  reset() {
    this.isResetting = true;
    if (this.fiber) {
      Effect.runFork(Fiber.interrupt(this.fiber));
      this.fiber = null;
    }
    this.isResetting = false;
    this.setState({ type: 'idle' });
  }
}

export function visualEffect(name, effectValue) {
  return new VisualEffect(name, effectValue);
}

export function useVisualEffectState(effect) {
  return useSyncExternalStore(
    (listener) => effect.subscribe(listener),
    () => effect.state,
  );
}
```

- [ ] **Step 3: Verify headlessly (no DOM/React needed for the class itself)**

Run (from `website/`):

```bash
node --input-type=module -e "
import { visualEffect } from './src/components/visual-effects/VisualEffect.js';
import { Effect } from 'effect';

const t = visualEffect('demo', Effect.sleep(100));
t.subscribe(() => console.log('state:', t.state.type));
await t.run();
console.log('final:', t.state.type);
"
```

Expected: prints `state: running`, then `state: completed`, then
`final: completed`.

- [ ] **Step 4: Commit**

```bash
cd website && git add src/components/visual-effects/colors.js src/components/visual-effects/VisualEffect.js && git commit -m "feat(website): add trimmed VisualEffect engine for landing-page visuals"
```

---

### Task 3: `useVisualEffects` hook and `EffectNode` visual component

**Files:**
- Create: `website/src/components/visual-effects/hooks/useVisualEffects.js`
- Create: `website/src/components/visual-effects/effect-node/EffectNode.jsx`

**Interfaces:**
- Consumes: `visualEffect` from `../VisualEffect.js` (Task 2), `TASK_COLORS` from `../colors.js` (Task 2), `motion` from `motion/react` (Task 1).
- Produces:
  - `useVisualEffects(definitions, deps = [])` — `definitions` is `{ [name]: () => Effect }`; returns `{ [name]: VisualEffect }`, memoized over `deps`.
  - `EffectNode` (default export) — props `{ name: string, state: EffectState }`; renders the animated node + label.

- [ ] **Step 1: Create `hooks/useVisualEffects.js`**

```js
// website/src/components/visual-effects/hooks/useVisualEffects.js
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

- [ ] **Step 2: Create `effect-node/EffectNode.jsx`**

```jsx
// website/src/components/visual-effects/effect-node/EffectNode.jsx
import React from 'react';
import { motion } from 'motion/react';
import { TASK_COLORS } from '../colors';

// Static per-state properties (color/scale/opacity), matching state.type
// exactly so no remapping is needed. Complex per-property timing (the
// running pulse, the failure shake) is overridden per-variant, same
// "hybrid" approach as the source engine's nodeVariants.
const nodeVariants = {
  idle: { backgroundColor: TASK_COLORS.idle, scale: 1, opacity: 0.6, x: 0 },
  running: {
    backgroundColor: TASK_COLORS.running,
    scale: [0.95, 1.03, 0.95],
    opacity: 1,
    x: 0,
    transition: { scale: { duration: 0.9, repeat: Infinity, ease: 'easeInOut' } },
  },
  completed: { backgroundColor: TASK_COLORS.completed, scale: [1.2, 1], opacity: 1, x: 0 },
  failed: {
    backgroundColor: TASK_COLORS.failed,
    scale: 1,
    opacity: 1,
    x: [0, -6, 6, -4, 4, 0],
    transition: { x: { duration: 0.4, ease: 'easeInOut' } },
  },
  interrupted: { backgroundColor: TASK_COLORS.interrupted, scale: 1, opacity: 1, x: 0 },
};

const STATE_LABEL = {
  idle: 'idle',
  running: 'running…',
  completed: 'done',
  failed: 'failed',
  interrupted: 'interrupted',
};

export default function EffectNode({ name, state }) {
  return (
    <div className="flex flex-col items-center gap-2">
      <motion.div
        className="h-14 w-14 rounded-2xl"
        variants={nodeVariants}
        animate={state.type}
        initial="idle"
        transition={{ type: 'spring', stiffness: 200, damping: 28 }}
      />
      <div className="text-center text-xs">
        <div className="font-semibold text-[var(--ifm-font-color-base)]">{name}</div>
        <div className="text-[var(--ifm-color-emphasis-600)]">{STATE_LABEL[state.type]}</div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
cd website && git add src/components/visual-effects/hooks/useVisualEffects.js src/components/visual-effects/effect-node/EffectNode.jsx && git commit -m "feat(website): add useVisualEffects hook and animated EffectNode"
```

(No standalone runtime verification here — `EffectNode` needs a running
scenario to render against; it's exercised end-to-end in Task 5's manual
browser check.)

---

### Task 4: `ConcurrencyVisual` scenario component

**Files:**
- Create: `website/src/components/visual-effects/scenarios/ConcurrencyVisual.jsx`

**Interfaces:**
- Consumes: `visualEffect`, `useVisualEffectState` from `../VisualEffect` (Task 2); `useVisualEffects` from `../hooks/useVisualEffects` (Task 3); `EffectNode` (default export) from `../effect-node/EffectNode` (Task 3); `Effect` from `effect`; `PlayIcon`, `StopIcon`, `ArrowCounterClockwiseIcon` from `@phosphor-icons/react`.
- Produces: `ConcurrencyVisual` (default export), a zero-prop component — this is what Task 5 wires into `CodeShowcase`.

- [ ] **Step 1: Create `scenarios/ConcurrencyVisual.jsx`**

```jsx
// website/src/components/visual-effects/scenarios/ConcurrencyVisual.jsx
import React, { useCallback, useMemo } from 'react';
import { Effect } from 'effect';
import { ArrowCounterClockwiseIcon, PlayIcon, StopIcon } from '@phosphor-icons/react';
import { useVisualEffectState, visualEffect } from '../VisualEffect';
import { useVisualEffects } from '../hooks/useVisualEffects';
import EffectNode from '../effect-node/EffectNode';

// Three parallel tasks, one of which fails; the tab's copy already promises
// "if one fails, the rest are interrupted" — Effect.all with unbounded
// concurrency gives us that for free (validated headlessly in Task 1).
export default function ConcurrencyVisual() {
  const tasks = useVisualEffects({
    fetchUsers: () => Effect.sleep(900),
    fetchOrders: () => Effect.sleep(1300),
    fetchProfile: () =>
      Effect.gen(function* () {
        yield* Effect.sleep(600);
        return yield* Effect.fail(new Error('Profile fetch failed'));
      }),
  });

  const taskList = useMemo(
    () => [tasks.fetchUsers, tasks.fetchOrders, tasks.fetchProfile],
    [tasks],
  );

  const group = useMemo(
    () =>
      visualEffect(
        'all',
        Effect.all(
          taskList.map((task) => task.effect),
          { concurrency: 'unbounded' },
        ),
      ),
    [taskList],
  );

  const usersState = useVisualEffectState(tasks.fetchUsers);
  const ordersState = useVisualEffectState(tasks.fetchOrders);
  const profileState = useVisualEffectState(tasks.fetchProfile);
  const groupState = useVisualEffectState(group);

  const nodes = [
    { name: tasks.fetchUsers.name, state: usersState },
    { name: tasks.fetchOrders.name, state: ordersState },
    { name: tasks.fetchProfile.name, state: profileState },
  ];

  const isRunning = groupState.type === 'running';
  const isDone =
    groupState.type === 'completed' ||
    groupState.type === 'failed' ||
    groupState.type === 'interrupted';

  const handleClick = useCallback(() => {
    if (isRunning) {
      group.interrupt();
      taskList.forEach((task) => task.interrupt());
    } else if (isDone) {
      group.reset();
      taskList.forEach((task) => task.reset());
    } else {
      group.run();
    }
  }, [group, taskList, isRunning, isDone]);

  return (
    <div className="flex h-full flex-col items-center justify-center gap-8 p-8">
      <div className="flex flex-wrap items-center justify-center gap-8">
        {nodes.map((node) => (
          <EffectNode key={node.name} name={node.name} state={node.state} />
        ))}
      </div>
      <button
        type="button"
        onClick={handleClick}
        className="flex items-center gap-2 rounded-full bg-[var(--ifm-color-primary)] px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-[var(--ifm-color-primary-light)]"
      >
        {isRunning ? (
          <StopIcon size={16} weight="bold" />
        ) : isDone ? (
          <ArrowCounterClockwiseIcon size={16} weight="bold" />
        ) : (
          <PlayIcon size={16} weight="fill" />
        )}
        {isRunning ? 'Interrupt' : isDone ? 'Reset' : 'Run'}
      </button>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
cd website && git add src/components/visual-effects/scenarios/ConcurrencyVisual.jsx && git commit -m "feat(website): add ConcurrencyVisual scenario component"
```

(Verified end-to-end in Task 5's manual browser check — this component has
no meaningful standalone render target until it's mounted on a page.)

---

### Task 5: Wire into `CodeShowcase` with a Code/Visual toggle

**Files:**
- Modify: `website/src/components/sections/CodeShowcase/data.js`
- Modify: `website/src/components/sections/CodeShowcase/index.jsx`
- Modify: `website/src/components/sections/CodeShowcase/styles.module.css`

**Interfaces:**
- Consumes: `ConcurrencyVisual` (Task 4, loaded via `require()` inside `<BrowserOnly>`, never a top-level `import`).
- Produces: the landing page's Concurrency tab shows a "Code / Visual" toggle; other tabs are unchanged.

- [ ] **Step 1: Add the `visual` field to the concurrency example**

In `website/src/components/sections/CodeShowcase/data.js`, the first entry
currently reads:

```js
  {
    value: 'concurrency',
    label: 'Concurrency',
    takeaway:
```

Change it to:

```js
  {
    value: 'concurrency',
    label: 'Concurrency',
    visual: 'concurrency',
    takeaway:
```

(The other four entries — `errors`, `resources`, `streaming`, `di` — are
left exactly as they are; they get no `visual` field in round 1.)

- [ ] **Step 2: Add the visual-component lookup and view-mode state to `index.jsx`**

In `website/src/components/sections/CodeShowcase/index.jsx`, the top of the
file currently reads:

```jsx
import React, { useState, useRef, useEffect } from 'react';
import { Highlight, Prism } from 'prism-react-renderer';
import { usePrismTheme } from '@docusaurus/theme-common';
import useIsBrowser from '@docusaurus/useIsBrowser';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import { FaCopy, FaCheck, FaArrowRight } from 'react-icons/fa6';
import styles from './styles.module.css';

import { examples } from './data';
```

Change it to:

```jsx
import React, { useState, useRef, useEffect } from 'react';
import { Highlight, Prism } from 'prism-react-renderer';
import { usePrismTheme } from '@docusaurus/theme-common';
import useIsBrowser from '@docusaurus/useIsBrowser';
import BrowserOnly from '@docusaurus/BrowserOnly';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import { FaCopy, FaCheck, FaArrowRight } from 'react-icons/fa6';
import styles from './styles.module.css';

import { examples } from './data';

// Lazily require()'d only inside <BrowserOnly>'s render function below, so
// this (and everything it pulls in — `motion`, `effect`) never executes
// during Docusaurus's Node.js prerender of this page.
const VISUAL_COMPONENTS = {
  concurrency: () => require('../../visual-effects/scenarios/ConcurrencyVisual').default,
};
```

- [ ] **Step 3: Add view-mode state and reset it on tab switch**

The component's state currently reads:

```jsx
export default function CodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
  const [copied, setCopied] = useState(false);
```

Change to:

```jsx
export default function CodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
  const [viewMode, setViewMode] = useState('visual');
  const [copied, setCopied] = useState(false);
```

And `handleTabClick` currently reads:

```jsx
  const handleTabClick = (idx) => {
    setActiveTab(idx);
    setCopied(false);
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
  };
```

Change to:

```jsx
  const handleTabClick = (idx) => {
    setActiveTab(idx);
    setViewMode('visual');
    setCopied(false);
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
  };
```

- [ ] **Step 4: Render the toggle and swap the panel body**

The Tab Bar / Code Area block currently reads:

```jsx
            {/* Tab Bar */}
            <div className={styles.tabBar} role="tablist">
              {examples.map((example, idx) => (
                <button
                  key={example.value}
                  id={`tab-${idx}`}
                  data-label={example.label}
                  className={clsx(
                    styles.tab,
                    activeTab === idx && styles.tabActive,
                  )}
                  onClick={() => handleTabClick(idx)}
                  aria-selected={activeTab === idx}
                  aria-controls={`tabpanel-${idx}`}
                  type="button"
                  role="tab"
                >
                  {example.label}
                </button>
              ))}
            </div>

            {/* Code Area */}
            <div
              id={`tabpanel-${activeTab}`}
              className={styles.codeArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}
            >
              <Highlight
                key={activeTab}
                theme={prismTheme}
                code={active.code.trim()}
                language="scala"
              >
                {({
                  className,
                  style,
                  tokens,
                  getLineProps,
                  getTokenProps,
                }) => (
                  <pre className={`${className} ${styles.pre}`} style={style}>
                    <code>
                      {tokens.map((line, i) => (
                        <div
                          key={i}
                          {...getLineProps({ line, key: i })}
                          className={styles.codeLine}
                        >
                          <span className={styles.lineNumber}>{i + 1}</span>
                          <span className={styles.lineContent}>
                            {line.map((token, key) => (
                              <span
                                key={key}
                                {...getTokenProps({ token, key })}
                              />
                            ))}
                          </span>
                        </div>
                      ))}
                    </code>
                  </pre>
                )}
              </Highlight>
            </div>
```

Change to:

```jsx
            {/* Tab Bar */}
            <div className={styles.tabBar} role="tablist">
              {examples.map((example, idx) => (
                <button
                  key={example.value}
                  id={`tab-${idx}`}
                  data-label={example.label}
                  className={clsx(
                    styles.tab,
                    activeTab === idx && styles.tabActive,
                  )}
                  onClick={() => handleTabClick(idx)}
                  aria-selected={activeTab === idx}
                  aria-controls={`tabpanel-${idx}`}
                  type="button"
                  role="tab"
                >
                  {example.label}
                </button>
              ))}
            </div>

            {/* Code/Visual view toggle — only for tabs with a registered visual */}
            {active.visual && (
              <div className={styles.viewToggle}>
                <button
                  type="button"
                  className={clsx(
                    styles.viewToggleButton,
                    viewMode === 'visual' && styles.viewToggleButtonActive,
                  )}
                  onClick={() => setViewMode('visual')}
                >
                  Visual
                </button>
                <button
                  type="button"
                  className={clsx(
                    styles.viewToggleButton,
                    viewMode === 'code' && styles.viewToggleButtonActive,
                  )}
                  onClick={() => setViewMode('code')}
                >
                  Code
                </button>
              </div>
            )}

            {/* Code Area */}
            <div
              id={`tabpanel-${activeTab}`}
              className={active.visual && viewMode === 'visual' ? styles.visualArea : styles.codeArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}
            >
              {active.visual && viewMode === 'visual' ? (
                <BrowserOnly fallback={<div className={styles.visualArea} />}>
                  {() => {
                    const VisualComponent = VISUAL_COMPONENTS[active.visual]();
                    return <VisualComponent />;
                  }}
                </BrowserOnly>
              ) : (
                <Highlight
                  key={activeTab}
                  theme={prismTheme}
                  code={active.code.trim()}
                  language="scala"
                >
                  {({
                    className,
                    style,
                    tokens,
                    getLineProps,
                    getTokenProps,
                  }) => (
                    <pre className={`${className} ${styles.pre}`} style={style}>
                      <code>
                        {tokens.map((line, i) => (
                          <div
                            key={i}
                            {...getLineProps({ line, key: i })}
                            className={styles.codeLine}
                          >
                            <span className={styles.lineNumber}>{i + 1}</span>
                            <span className={styles.lineContent}>
                              {line.map((token, key) => (
                                <span
                                  key={key}
                                  {...getTokenProps({ token, key })}
                                />
                              ))}
                            </span>
                          </div>
                        ))}
                      </code>
                    </pre>
                  )}
                </Highlight>
              )}
            </div>
```

- [ ] **Step 5: Add toggle and visual-area styles**

In `website/src/components/sections/CodeShowcase/styles.module.css`, the
file currently has this block:

```css
.tabActive {
  color: var(--ifm-color-primary);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--ifm-color-primary);
}

.codeArea {
  flex: 1;
  display: flex;
  flex-direction: column;
}
```

Change to:

```css
.tabActive {
  color: var(--ifm-color-primary);
  font-weight: 600;
  box-shadow: inset 0 -2px 0 var(--ifm-color-primary);
}

.viewToggle {
  display: flex;
  gap: 0.25rem;
  padding: 0.5rem 1rem 0;
  background-color: var(--ifm-color-emphasis-100);
}

.viewToggleButton {
  padding: 0.35rem 0.9rem;
  border-radius: 999px;
  border: none;
  background: transparent;
  color: var(--ifm-color-emphasis-600);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.viewToggleButton:hover {
  color: var(--ifm-color-emphasis-900);
}

.viewToggleButtonActive {
  background-color: var(--ifm-color-emphasis-200);
  color: var(--ifm-color-primary);
}

.visualArea {
  flex: 1;
  display: flex;
  min-height: 24rem;
  max-height: 500px;
}

.codeArea {
  flex: 1;
  display: flex;
  flex-direction: column;
}
```

- [ ] **Step 6: Manual verification — dev server**

Run (from `website/`): `yarn start`

Open the printed local URL, scroll to "The ZIO Way" section. Verify:
1. The "Concurrency" tab (selected by default) shows the "Visual / Code"
   toggle; the other four tabs do not.
2. Default view is "Visual": three nodes (`fetchUsers`, `fetchOrders`,
   `fetchProfile`), idle/grey, with a "Run" button.
3. Click "Run": nodes turn blue and pulse; `fetchProfile` turns red
   ("failed") first, `fetchUsers`/`fetchOrders` turn orange
   ("interrupted") shortly after; button becomes "Reset".
4. Click "Reset": all three nodes return to idle/grey; button becomes "Run"
   again. Click "Run" a second time — it works again (no stale state).
5. Click "Code": the existing static Scala snippet for Concurrency appears,
   unchanged from before this plan. Click "Visual": the animation returns.
6. Switch to the "Error handling" tab: no toggle appears, the static code
   panel behaves exactly as before this plan (no regression on
   untouched tabs).
7. Toggle the site's dark/light theme switcher: node colors, toggle
   buttons, and the panel chrome all remain legible in both themes.
8. Resize the browser to ~400px width: the node row wraps, no horizontal
   overflow of the page.

Expected: all 8 checks pass. If any node fails to reach the expected
terminal color within ~2 seconds of clicking "Run", stop and re-check
Task 1 Step 4's headless validation output before debugging the UI layer.

- [ ] **Step 7: Commit**

```bash
cd website && git add src/components/sections/CodeShowcase/data.js src/components/sections/CodeShowcase/index.jsx src/components/sections/CodeShowcase/styles.module.css && git commit -m "feat(website): wire ConcurrencyVisual into the CodeShowcase Concurrency tab"
```

---

## Plan Self-Review

**Spec coverage:** Every element of the spec's "Round 1: Concurrency tab"
section is implemented: the 3-parallel-tasks-one-fails scenario (Task 4),
the minimal Play/Reset header control (Task 4), the `.codePanel`-chrome-only
integration with no duplicate code block (Task 5), the three added deps
(Task 1), the `<BrowserOnly>` load-deferral (Task 5), and manual-only
verification (Tasks 1, 2, 5). The spec's general architecture section
(consolidated engine file set, `data.js`/`index.jsx` wiring pattern reusable
by later rounds) is implemented across Tasks 2–5.

**Placeholder scan:** No TBD/TODO markers; every step has runnable code or
an exact shell command.

**Type consistency:** `EffectState.type` values (`idle | running | completed
| failed | interrupted`) are identical across `VisualEffect.js`,
`colors.js`'s `TASK_COLORS` keys, `EffectNode.jsx`'s `nodeVariants`/
`STATE_LABEL` keys, and `ConcurrencyVisual.jsx`'s `isDone` check. The
`useVisualEffects` hook's returned shape (`{ [name]: VisualEffect }`) matches
how `ConcurrencyVisual.jsx` destructures `tasks.fetchUsers` /
`tasks.fetchOrders` / `tasks.fetchProfile`. `EffectNode`'s prop contract
(`{ name, state }`) matches the `nodes` array built in `ConcurrencyVisual.jsx`.
`VISUAL_COMPONENTS`'s key (`concurrency`) matches `data.js`'s
`visual: 'concurrency'` value.
