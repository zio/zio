// website/src/components/visual-effects/LayerGraph.js
//
// Bespoke — the source visual-effect project has no DI/layer example to port
// (same situation as the Streaming tab). Same observer shape as VisualScope.js
// and StreamPipeline.js: the real `Layer` build drives all of this, nothing
// here simulates it.
//
// Beyond build state it records the two things that actually make DI what it
// is, rather than a generic task graph:
//   - how many times a layer was *constructed* vs how many services *asked*
//     for it (Database: built once, used twice — the bit people expect to go
//     the other way)
//   - what is in the environment so far, i.e. the R of ZIO[R, E, A] being
//     satisfied one layer at a time
import { taskSounds } from './sounds/taskSounds';
// Fire-and-forget: play* is async, and a rejected or unavailable audio
// context must never break the visual.
const play = (event) => taskSounds.playLayerEvent(event).catch(() => {});

export class LayerGraph {
  constructor(layers) {
    this.initial = layers;
    this.reset();
  }

  subscribe(callback) {
    this.subscribers ??= new Set();
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  notify() {
    this.subscribers?.forEach((callback) => {
      callback();
    });
  }

  // Called when the layer's construction effect actually begins, with how long
  // it will take, so the card's progress bar tracks the real build.
  startBuild(id, durationMs) {
    const layer = this.find(id);
    if (!layer) return;

    layer.state = 'building';
    layer.startedAt = Date.now();
    layer.durationMs = durationMs;
    layer.buildCount += 1;
    this.notify();
  }

  // `instance` is the identity the constructed service actually carries, so
  // consumers can show they received that exact one rather than a copy.
  setReady(id, instance) {
    const layer = this.find(id);
    if (!layer || layer.state === 'ready') return;

    layer.state = 'ready';
    layer.instance = instance;
    // `app` is not a service anyone can require, so it never joins the
    // environment — it is the thing the environment exists to satisfy.
    if (!layer.isApp) this.environment.push(id);

    // A layer turning green gets a quiet tick; the app finishing gets the
    // one brighter note in the run.
    play(layer.isApp ? 'done' : 'ready');

    this.notify();
  }

  // Recorded when a consumer pulls a dependency out of the environment — the
  // moment that would have constructed a second copy if layers weren't shared.
  recordUse(depId, consumerId, instance) {
    const layer = this.find(depId);
    if (!layer) return;

    layer.usedBy.push({ consumerId, instance });

    // Only the second consumer onward is worth hearing: that is the instance
    // being shared rather than rebuilt, which is what the graph is arguing.
    if (layer.usedBy.length > 1) play('shared');

    this.notify();
  }

  find(id) {
    return this.layers.find((layer) => layer.id === id);
  }

  reset() {
    this.layers = this.initial.map((layer) => ({
      ...layer,
      state: 'pending',
      buildCount: 0,
      usedBy: [],
      instance: null,
    }));
    this.environment = [];
    this.notify();
  }
}
