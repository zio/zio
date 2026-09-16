// website/src/components/visual-effects/LayerGraph.js
//
// Bespoke — the source visual-effect project has no DI/layer example to port
// (same situation as the Streaming tab). Same observer shape as VisualScope.js
// and StreamPipeline.js: the real `Layer` build drives the state, this just
// records it for the visual layer.
export class LayerGraph {
  constructor(layers) {
    this.layers = layers.map((layer) => ({ ...layer, state: 'pending' }));
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

  // Called when the layer's construction effect actually begins, with how
  // long it will take, so the card can show a progress bar driven by the real
  // build rather than a generic spinner — two bars filling at once is what
  // shows that independent layers are constructed in parallel.
  startBuild(id, durationMs) {
    const layer = this.layers.find((layer) => layer.id === id);
    if (!layer) return;

    layer.state = 'building';
    layer.startedAt = Date.now();
    layer.durationMs = durationMs;
    this.notify();
  }

  setReady(id) {
    const layer = this.layers.find((layer) => layer.id === id);
    if (!layer || layer.state === 'ready') return;

    layer.state = 'ready';
    this.notify();
  }

  reset() {
    this.layers = this.layers.map(({ id, label, dependsOn }) => ({
      id,
      label,
      dependsOn,
      state: 'pending',
    }));
    this.notify();
  }
}
