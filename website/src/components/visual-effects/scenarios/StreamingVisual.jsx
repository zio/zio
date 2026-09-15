import { Effect, Stream } from 'effect';
import { useEffect, useMemo } from 'react';
import { EffectExample } from '../EffectExample';
import { getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { PipelineStages } from '../pipeline/PipelineStages';
import { StringResult } from '../renderers';
import { StreamPipeline } from '../StreamPipeline';
import {
  STREAM_BUFFER_CAPACITY,
  STREAM_CONCURRENCY,
  STREAM_WRITE_CONCURRENCY,
  STREAMING_SNIPPET,
} from '../streamingParams';

// Bespoke — the source visual-effect project has no ZStream/streaming
// example to port, so this scenario is built by hand rather than ported
// verbatim (an explicit exception the user approved for this tab only).
// It still runs a REAL `effect` Stream pipeline (fromIterable -> mapEffect
// with concurrency -> buffer -> mapEffect -> runDrain), mirroring the
// ZStream snippet shown in the Code toggle line for line, exactly like
// every other tab drives its visual off a real Effect/fiber rather than a
// canned animation.
//
// The writer is deliberately the bottleneck: it handles one item at a time,
// slower than four enrichments complete, so the bounded buffer fills and
// upstream enrichment visibly stalls waiting on it. That stall is real
// backpressure from the Stream itself — verified in the engine, where an
// item's enrich start lands milliseconds after a write completes and frees
// buffer space — not something this visual fakes.

// 9, not 10: the Written lane fits 3 chips per row, so 9 fills exactly three
// rows and lets every lane box be one row shorter (see LANE_BOX_HEIGHT in
// pipeline/PipelineStages.jsx), giving the code block back the space.
const EVENTS = Array.from({ length: 9 }, (_, i) => ({ id: i + 1 }));

// From streamingParams.js, the same module the displayed snippet is built
// from — so what the code says and what the pipeline runs cannot drift.
const CONCURRENCY = STREAM_CONCURRENCY;
const BUFFER_CAPACITY = STREAM_BUFFER_CAPACITY;
const WRITE_CONCURRENCY = STREAM_WRITE_CONCURRENCY;

// Paced for watching, not for realism: the whole point of this tab is that
// a viewer can follow an item from source to written, see four of them
// overlapping in enrich, and watch the writer set the pace for everything
// upstream of it.
const ENRICH_MIN_MS = 2200;
const ENRICH_MAX_MS = 3000;
const WRITE_MIN_MS = 2100;
const WRITE_MAX_MS = 2800;

function enrichItem(item, durationMs) {
  return Effect.gen(function* () {
    yield* Effect.sleep(durationMs);
    return item;
  });
}

function buildPipelineEffect(pipeline) {
  return Effect.gen(function* () {
    yield* Stream.fromIterable(EVENTS).pipe(
      Stream.mapEffect(
        (item) =>
          Effect.gen(function* () {
            // Marked here, not before mapEffect, so "enriching" lights up
            // exactly when this item's concurrency-gated slot starts —
            // matching mapZIOPar's actual concurrent window. The duration is
            // handed to the pipeline so the chip's progress bar tracks this
            // item's real work, not an approximation.
            const durationMs = getDelay(ENRICH_MIN_MS, ENRICH_MAX_MS);
            pipeline.startTimed(item.id, 'enriching', durationMs);
            return yield* enrichItem(item, durationMs);
          }),
        { concurrency: CONCURRENCY },
      ),
      // Runs when the buffer actually accepts the element, so the buffer
      // lane's occupancy tracks the real queue rather than an estimate.
      Stream.tap((item) =>
        Effect.sync(() => pipeline.setStage(item.id, 'buffered')),
      ),
      Stream.buffer({ capacity: BUFFER_CAPACITY }),
      Stream.mapEffect(
        (item) =>
          Effect.gen(function* () {
            const durationMs = getDelay(WRITE_MIN_MS, WRITE_MAX_MS);
            pipeline.startTimed(item.id, 'writing', durationMs);
            yield* Effect.sleep(durationMs);
            pipeline.setStage(item.id, 'written');
            return item;
          }),
        { concurrency: WRITE_CONCURRENCY },
      ),
      Stream.runDrain,
    );

    return new StringResult(`${EVENTS.length} written`);
  });
}

export default function StreamingVisual() {
  const pipeline = useMemo(
    () =>
      new StreamPipeline(
        'events',
        EVENTS,
        CONCURRENCY,
        BUFFER_CAPACITY,
        WRITE_CONCURRENCY,
      ),
    [],
  );

  const pipelineTask = useVisualEffect(
    'pipeline',
    () => buildPipelineEffect(pipeline),
    { deps: [pipeline] },
  );

  // Same pattern as AcquireReleaseVisual.jsx's scope cleanup: reset the
  // visual side-channel when the task resets to idle, and on unmount (the
  // EffectExample-level effects.forEach(effect => effect.reset()) above
  // this component already interrupts the fiber; this resets our own
  // pipeline state alongside it).
  useEffect(() => {
    const unsubscribe = pipelineTask.subscribe(() => {
      if (pipelineTask.state.type === 'idle') {
        pipeline.reset();
      }
    });

    return () => {
      unsubscribe();
      pipeline.reset();
    };
  }, [pipelineTask, pipeline]);

  return (
    <EffectExample
      name="ZStream.mapZIOPar"
      description="Process a stream concurrently, with backpressure built in"
      code={STREAMING_SNIPPET}
      effects={[pipelineTask]}
      effectHighlightMap={{ pipeline: { text: 'runDrain' } }}
      streamPipeline={pipeline}
      exampleId="stream-pipeline"
    />
  );
}
