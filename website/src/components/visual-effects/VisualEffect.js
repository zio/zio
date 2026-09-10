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
    if (this.state.type === 'completed')
      return Effect.succeed(this.state.result);
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
