import { Effect, Stream } from 'effect';
import { useEffect, useMemo } from 'react';
import { EffectExample } from '../EffectExample';
import { getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { PipelineStages } from '../pipeline/PipelineStages';
import { StringResult } from '../renderers';
import { StreamPipeline } from '../StreamPipeline';

// Bespoke — the source visual-effect project has no ZStream/streaming
// example to port, so this scenario is built by hand rather than ported
// verbatim (an explicit exception the user approved for this tab only).
// It still runs a REAL `effect` Stream pipeline (fromIterable -> mapEffect
// with concurrency -> buffer -> runDrain), mirroring the ZStream snippet
// shown in the Code toggle line for line, exactly like every other tab
// drives its visual off a real Effect/fiber rather than a canned animation.

const EVENTS = Array.from({ length: 10 }, (_, i) => ({ id: i + 1 }));

const CONCURRENCY = 4;
const BUFFER_CAPACITY = 2;

// Paced for watching, not for realism: the whole point of this tab is that
// a viewer can follow an item from source to done and see four of them
// overlapping in enrich on the way.
const ENRICH_MIN_MS = 1200;
const ENRICH_MAX_MS = 1800;

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
            // matching mapZIOPar(20)'s actual concurrent window. The
            // duration is handed to the pipeline so the chip's progress bar
            // tracks this item's real work, not an approximation.
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
      Stream.tap((item) =>
        Effect.sync(() => pipeline.setStage(item.id, 'done')),
      ),
      Stream.runDrain,
    );

    return new StringResult(`${EVENTS.length} processed`);
  });
}

export default function StreamingVisual() {
  const pipeline = useMemo(
    () => new StreamPipeline('events', EVENTS, CONCURRENCY, BUFFER_CAPACITY),
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

  const codeSnippet = `val pipeline: ZIO[Any, Throwable, Unit] =
  ZStream
    .fromIterable(events)          // or Kafka, files, sockets…
    .mapZIOPar(20)(enrich)         // 20 concurrent enrichments
    .buffer(16)                    // bounded — fills when the sink lags
    .runDrain`;

  return (
    <EffectExample
      name="ZStream.mapZIOPar"
      description="Process a stream concurrently, with backpressure built in"
      code={codeSnippet}
      effects={[pipelineTask]}
      effectHighlightMap={{ pipeline: { text: 'runDrain' } }}
      streamPipeline={pipeline}
      exampleId="stream-pipeline"
    />
  );
}
