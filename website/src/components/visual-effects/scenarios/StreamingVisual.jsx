import { Chunk, Effect, Stream } from 'effect';
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
// with concurrency -> filter -> grouped -> mapEffect -> runDrain), mirroring
// the ZStream snippet shown in the Code toggle line for line, exactly like
// every other tab drives its visual off a real Effect/fiber rather than a
// canned animation.

const EVENTS = Array.from({ length: 9 }, (_, i) => {
  const id = i + 1;
  return { id, isValid: id % 3 !== 0 };
});

const BATCH_SIZE = 3;
const CONCURRENCY = 4;

// Paced for watching, not for realism: the whole point of this tab is that
// a viewer can follow an item from source to written and see four of them
// overlapping on the way. At 400-700ms the enrich phase was over in ~1.8s;
// even at 900-1500ms it was still brisk enough to be hard to track.
const ENRICH_MIN_MS = 3000;
const ENRICH_MAX_MS = 4200;
const WRITE_MIN_MS = 1800;
const WRITE_MAX_MS = 2400;

function enrichItem(item, durationMs) {
  return Effect.gen(function* () {
    yield* Effect.sleep(durationMs);
    return item;
  });
}

function writeBatch(batch) {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(WRITE_MIN_MS, WRITE_MAX_MS));
    return batch;
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
            pipeline.startEnrich(item.id, durationMs);
            const enriched = yield* enrichItem(item, durationMs);
            pipeline.setStage(
              enriched.id,
              enriched.isValid ? 'valid' : 'filteredOut',
            );
            return enriched;
          }),
        { concurrency: CONCURRENCY },
      ),
      Stream.filter((item) => item.isValid),
      Stream.grouped(BATCH_SIZE),
      Stream.tap((batch) =>
        Effect.sync(() =>
          pipeline.setStageForIds(
            Chunk.toReadonlyArray(batch).map((item) => item.id),
            'batching',
          ),
        ),
      ),
      Stream.mapEffect((batch) => writeBatch(batch)),
      Stream.tap((batch) =>
        Effect.sync(() =>
          pipeline.setStageForIds(
            Chunk.toReadonlyArray(batch).map((item) => item.id),
            'written',
          ),
        ),
      ),
      Stream.runDrain,
    );

    const filteredCount = EVENTS.filter((event) => !event.isValid).length;
    const writtenCount = EVENTS.length - filteredCount;
    return new StringResult(
      `${writtenCount} written, ${filteredCount} filtered`,
    );
  });
}

export default function StreamingVisual() {
  const pipeline = useMemo(
    () => new StreamPipeline('events', EVENTS, CONCURRENCY),
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
    .filter(_.isValid)
    .grouped(100)                  // batch for the database
    .mapZIO(writeBatch)
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
