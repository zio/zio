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
import { taskSounds } from './sounds/taskSounds';

// Fire-and-forget: every play* is async and a rejected audio promise (an
// autoplay-blocked context, say) must never break the visual.
const play = (sound) => sound?.call(taskSounds).catch(() => {});

export class StreamPipeline {
  // `concurrency` is the same number the Stream's mapEffect is gated on —
  // the visual layer renders exactly this many enrich slots so a viewer can
  // see the parallelism directly (4 slots filled at once) instead of having
  // to infer it from chips appearing in a box. `capacity` is the Stream's
  // buffer size, shown next to the buffer lane so "bounded" is explicit.
  constructor(id, events, concurrency, capacity, writeConcurrency) {
    this.id = id;
    this.concurrency = concurrency;
    this.capacity = capacity;
    this.writeConcurrency = writeConcurrency;
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

    // Stages are voiced in ascending pitch, so an item is audibly climbing
    // the pipeline: a soft tick when it lands in the buffer, a brighter one
    // an octave up when it is finally written.
    if (stage === 'buffered') play(taskSounds.playFinalizerCreated);
    if (stage === 'written') play(taskSounds.playFinalizerCompleted);

    this.announceBackpressure();
    this.notify();
  }

  // Records when a timed stage actually began for this item and how long its
  // work will take, so the chip can render a progress bar driven by that
  // item's real duration rather than a generic spinner — several enrich bars
  // filling at once, each at its own rate, is what makes the parallelism
  // legible.
  startTimed(id, stage, durationMs) {
    const item = this.items.find((item) => item.id === id);
    if (!item) return;

    item.stage = stage;
    item.startedAt = Date.now();
    item.durationMs = durationMs;

    if (stage === 'enriching') play(taskSounds.playRunning);
    if (stage === 'writing') play(taskSounds.playFinalizerRunning);

    this.notify();
  }

  // One chime per stall, not one per frame: the buffer sits full for seconds
  // at a time, so this fires on the edge into backpressure and re-arms only
  // once the writer has drained it below capacity again.
  announceBackpressure() {
    const waiting = this.items.filter(
      (item) => item.stage === 'buffered',
    ).length;

    if (waiting >= this.capacity && !this.backpressureAnnounced) {
      this.backpressureAnnounced = true;
      play(taskSounds.playNotificationChime);
    } else if (waiting < this.capacity) {
      this.backpressureAnnounced = false;
    }
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
    this.backpressureAnnounced = false;
    this.notify();
  }
}
