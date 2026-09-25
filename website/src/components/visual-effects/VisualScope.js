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
