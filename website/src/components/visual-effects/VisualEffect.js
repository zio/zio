import { Context, Effect, Fiber, Option } from 'effect';
import { useSyncExternalStore } from 'react';
import { taskSounds } from './sounds/taskSounds';

// Ported verbatim (TS types stripped) from the source engine's
// src/VisualEffect.ts, including the parent/child notification service and
// `death` state — full fidelity, even though round 1's scenario never
// exercises notify()/death (nothing calls Effect.die or the notify
// helper). Sound calls go through the silent taskSounds stub (see
// sounds/taskSounds.js) since audio is out of scope for round 1.

// Pattern matching helper for EffectState (internal use only)
const matchEffectState = (state, cases) => {
  switch (state.type) {
    case 'idle':
      return cases.idle();
    case 'running':
      return cases.running();
    case 'completed':
      return cases.completed(state.result);
    case 'failed':
      return cases.failed(state.error);
    case 'interrupted':
      return cases.interrupted();
    case 'death':
      return cases.death(state.error);
    default:
      return undefined;
  }
};

// Valid state transitions for the state machine
const VALID_TRANSITIONS = {
  idle: new Set(['running', 'idle']),
  running: new Set([
    'completed',
    'failed',
    'interrupted',
    'death',
    'idle',
    'running',
  ]),
  completed: new Set(['idle', 'running', 'completed']),
  failed: new Set(['failed', 'idle', 'running']),
  interrupted: new Set(['interrupted', 'idle', 'running']),
  death: new Set(['death', 'idle', 'running']),
};

// Service tag for parent-child VisualEffect communication
const VisualEffectService = Context.GenericTag('VisualEffectService');

// Service implementation
class VisualEffectServiceImpl {
  constructor(parent) {
    this.parent = parent;
  }

  addChild(child) {
    return Effect.sync(() => {
      this.parent.addChildEffect(child);
    });
  }

  notify(message, options) {
    return Effect.sync(() => {
      this.parent.notify(message, options);
    });
  }
}

export class VisualEffect {
  constructor(name, effectValue, showTimer = false) {
    this.name = name;
    this._effect = effectValue;
    this.showTimer = showTimer;

    this.state = { type: 'idle' };
    this.listeners = new Set();
    this.notificationListeners = new Set();
    this.currentNotification = null;
    this.fiber = null;
    this.timeouts = new Set();
    this.isResetting = false;
    this.children = new Set();
    this.startTime = null;
    this.endTime = null;
  }

  addChildEffect(child) {
    this.children.add(child);
  }

  // The effect property returns an Effect that updates this effect's state when run
  get effect() {
    // Quick return for terminal states
    const quickReturn = matchEffectState(this.state, {
      idle: () => null,
      running: () => null,
      completed: (result) => Effect.succeed(result),
      failed: (error) => Effect.fail(error),
      interrupted: () => null,
      death: (error) => Effect.die(error),
    });

    if (quickReturn) return quickReturn;

    const self = this;

    // Create the effect
    return Effect.gen(function* () {
      // Register with parent service if available
      const maybeParentService =
        yield* Effect.serviceOption(VisualEffectService);
      yield* Option.match(maybeParentService, {
        onNone: () => Effect.void,
        onSome: (service) => service.addChild(self),
      });

      // Mark as running
      self.setState({ type: 'running' });

      // Execute the wrapped effect with appropriate service provided to all nested effects
      const effectWithRootService = self._effect.pipe(
        Effect.provideService(
          VisualEffectService,
          new VisualEffectServiceImpl(self),
        ),
      );

      const wrappedEffect = Option.isSome(maybeParentService)
        ? self._effect
        : effectWithRootService;

      return yield* wrappedEffect;
    }).pipe(
      // Clear notifications on any non-success exit
      Effect.tapErrorCause(() => Effect.sync(() => this.clearNotifications())),
      // Handle success
      Effect.tap((result) =>
        Effect.sync(() => {
          this.setState({ type: 'completed', result });
        }),
      ),
      // Handle errors
      Effect.tapError((error) =>
        Effect.sync(() => {
          this.setState({ type: 'failed', error });
        }),
      ),
      // Handle interruption
      Effect.onInterrupt(() =>
        Effect.sync(() => {
          if (!this.isResetting) {
            this.setState({ type: 'interrupted' });
          }
        }),
      ),
      // Handle defects
      Effect.tapDefect((defect) =>
        Effect.sync(() => {
          this.setState({ type: 'death', error: defect });
        }),
      ),
    );
  }

  // Observable pattern methods
  subscribe(listener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  subscribeToNotifications(listener) {
    this.notificationListeners.add(listener);
    return () => {
      this.notificationListeners.delete(listener);
    };
  }

  notify(message, options) {
    // Clear any existing notification and its timeout
    this.clearNotifications();

    const notification = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`,
      message,
      timestamp: Date.now(),
      duration: options?.duration ?? 2000, // default 2 seconds
      ...(options?.icon && { icon: options.icon }),
    };

    this.currentNotification = notification;
    this.notifyNotificationListeners();

    // Auto-remove after duration
    if (notification.duration) {
      const timeoutId = setTimeout(() => {
        this.clearNotifications();
      }, notification.duration);
      this.timeouts.add(timeoutId);
    }
  }

  getCurrentNotification() {
    return this.currentNotification;
  }

  clearNotifications() {
    this.currentNotification = null;
    this.clearTimeouts();
    this.notifyNotificationListeners();
  }

  notifyStateListeners() {
    this.listeners.forEach((listener) => {
      listener();
    });
  }

  notifyNotificationListeners() {
    this.notificationListeners.forEach((listener) => {
      listener();
    });
  }

  setState(newState) {
    if (this.isResetting) return;

    const previousState = this.state;
    const validTransitions = VALID_TRANSITIONS[previousState.type];

    if (!validTransitions?.has(newState.type)) {
      return;
    }

    this.state = newState;

    // Track timing
    if (this.showTimer) {
      if (newState.type === 'running' && previousState.type !== 'running') {
        this.startTime = Date.now();
        this.endTime = null;
      } else if (
        previousState.type === 'running' &&
        newState.type !== 'running'
      ) {
        this.endTime = Date.now();
      }
    }

    // Trigger sounds
    if (previousState.type !== newState.type) {
      matchEffectState(newState, {
        idle: () => {},
        running: () => taskSounds.playRunning().catch(() => {}),
        completed: () => taskSounds.playSuccess().catch(() => {}),
        failed: () => taskSounds.playFailure().catch(() => {}),
        interrupted: () => taskSounds.playInterrupted().catch(() => {}),
        death: () => taskSounds.playDeath().catch(() => {}),
      });
    }

    this.notifyStateListeners();
  }

  clearTimeouts() {
    this.timeouts.forEach(clearTimeout);
    this.timeouts.clear();
  }

  reset() {
    this.isResetting = true;
    try {
      // Reset all children first so their state transitions obey the reset flag
      this.children.forEach((child) => {
        child.reset();
      });

      // Clear the children collection since they're no longer relevant
      this.children.clear();

      // Interrupt our own fiber if it's still running
      if (this.fiber) {
        Effect.runFork(Fiber.interrupt(this.fiber));
        this.fiber = null;
      }

      // Clean up any scheduled work / caches
      this.clearTimeouts();
      this.clearNotifications(); // Clear notifications on reset
      this.startTime = null;
      this.endTime = null;
    } finally {
      // Allow subsequent state transitions
      this.isResetting = false;
    }

    // Now that the reset flag is cleared, transition ourselves to idle
    this.setState({ type: 'idle' });
  }

  async run() {
    try {
      this.fiber = Effect.runFork(this.effect);
      await Effect.runPromise(Fiber.await(this.fiber));
    } catch {
      // Error handling is done within the effect
    } finally {
      this.fiber = null;
    }
  }

  interrupt() {
    if (this.state.type === 'running') {
      const fiberToInterrupt = this.fiber;
      this.fiber = null;

      // Optimistically mark as interrupted so observers update immediately.
      // The onInterrupt handler inside the effect will confirm this later.
      this.setState({ type: 'interrupted' });

      if (fiberToInterrupt) {
        Effect.runFork(Fiber.interrupt(fiberToInterrupt));
      }
    }
  }
}

// Utility function for effects to notify their parent
export const notify = (message, options) =>
  Effect.serviceOption(VisualEffectService).pipe(
    Effect.flatMap((option) =>
      Option.isSome(option)
        ? option.value.notify(message, options)
        : Effect.void,
    ),
  );

// Granular React hooks for better performance

// Subscribe only to state changes
export function useVisualEffectState(effect) {
  return useSyncExternalStore(
    effect.subscribe.bind(effect),
    () => effect.state,
  );
}

// Subscribe only to notification changes
export function useVisualEffectNotification(effect) {
  return useSyncExternalStore(
    effect.subscribeToNotifications.bind(effect),
    () => effect.getCurrentNotification(),
  );
}

// Subscribe for re-renders only (no return value)
export function useVisualEffectSubscription(effect) {
  useSyncExternalStore(effect.subscribe.bind(effect), () => effect.state);
}

// Factory function
export function visualEffect(name, effectValue, showTimer = false) {
  return new VisualEffect(name, effectValue, showTimer);
}
