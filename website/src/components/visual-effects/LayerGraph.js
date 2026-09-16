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
    this.environment.push(id);
    this.notify();
  }

  // Recorded when a consumer pulls a dependency out of the environment — the
  // moment that would have constructed a second copy if layers weren't shared.
  recordUse(depId, consumerId, instance) {
    const layer = this.find(depId);
    if (!layer) return;

    layer.usedBy.push({ consumerId, instance });
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
