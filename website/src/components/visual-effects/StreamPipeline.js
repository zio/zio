// website/src/components/visual-effects/StreamPipeline.js
//
// No equivalent exists in the source visual-effect project (it has no
// ZStream/streaming example) — this is a bespoke visual state class for
// the Streaming tab, built by hand rather than ported. It follows the same
// observer pattern as VisualScope.js (subscribe/notify) so it plugs into
// EffectExample the same way scope/VisualScope does.
//
// Tracks each event's current pipeline stage so the visual layer
// (pipeline/PipelineStages.jsx) can render where every item currently is,
// while a real `effect` Stream (see scenarios/StreamingVisual.jsx) is what
// actually drives the stage transitions via Effect.sync calls.
export class StreamPipeline {
  constructor(id, events) {
    this.id = id;
    this.items = events.map((event) => ({ ...event, stage: 'queued' }));
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

  setStage(id, stage) {
    const item = this.items.find((item) => item.id === id);
    if (!item || item.stage === stage) return;

    item.stage = stage;
    this.notify();
  }

  setStageForIds(ids, stage) {
    const idSet = new Set(ids);
    let changed = false;

    for (const item of this.items) {
      if (idSet.has(item.id) && item.stage !== stage) {
        item.stage = stage;
        changed = true;
      }
    }

    if (changed) this.notify();
  }

  reset() {
    this.items = this.items.map((item) => ({ ...item, stage: 'queued' }));
    this.notify();
  }
}
