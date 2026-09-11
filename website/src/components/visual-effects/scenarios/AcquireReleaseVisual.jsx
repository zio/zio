import { Effect } from 'effect';
import { useEffect, useMemo, useRef } from 'react';
import { EffectExample } from '../EffectExample';
import { getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { useVisualScope } from '../hooks/useVisualScope';
import { StringResult } from '../renderers';
import { VisualScope } from '../VisualScope';

// Ported verbatim (TS types stripped, otherwise unchanged) from the source
// engine's src/examples/effect-acquire-release.tsx — the actual
// "ZIO.acquireRelease" example from the visual-effect project. The
// source's isDarkMode={mainTaskState.type === "death"} accent is
// deliberately dropped here (see the design spec's "Round 3" section) —
// EffectExample no longer supports isDarkMode (round 1 removed it because
// two permanently-dark variants don't work on a light/dark-toggling host).

// Simulate resource acquisition with cleanup
function acquireDatabase() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      connection: 'DATABASE',
      close: () => console.log('Database connection closed'),
    };
  });
}

function acquireCache() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      connection: 'CACHE',
      close: () => console.log('Cache connection closed'),
    };
  });
}

function acquireLogger() {
  return Effect.gen(function* () {
    yield* Effect.sleep(getDelay(600, 900));
    return {
      file: 'LOGGER',
      close: () => console.log('Logger file closed'),
    };
  });
}

export default function AcquireReleaseVisual() {
  const scope = useMemo(() => new VisualScope('resourceScope'), []);
  const runCountRef = useRef(0);
  useVisualScope(scope);

  // Individual resource tasks
  const dbTask = useVisualEffect(
    'database',
    () =>
      acquireDatabase().pipe(
        Effect.map((db) => new StringResult(db.connection)),
        Effect.tap(() => scope.addFinalizer('Close database')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  const cacheTask = useVisualEffect(
    'cache',
    () =>
      acquireCache().pipe(
        Effect.map((cache) => new StringResult(cache.connection)),
        Effect.tap(() => scope.addFinalizer('Flush cache')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  const loggerTask = useVisualEffect(
    'logger',
    () =>
      acquireLogger().pipe(
        Effect.map((logger) => new StringResult(logger.file)),
        Effect.tap(() => scope.addFinalizer('Close log file')),
        Effect.tap(() => Effect.sleep(200)),
      ),
    { deps: [scope] },
  );

  // Main effect that uses scoped resources
  const mainTask = useVisualEffect(
    'result',
    () =>
      Effect.gen(function* () {
        runCountRef.current += 1;
        const currentRun = runCountRef.current;

        scope.setState('acquiring');

        yield* dbTask.effect;
        yield* cacheTask.effect;
        yield* loggerTask.effect;

        scope.setState('active');

        yield* Effect.sleep(getDelay(1000, 1500));

        const cyclePosition = (currentRun - 1) % 3;

        if (cyclePosition === 0) {
          return new StringResult('Work completed!');
        } else if (cyclePosition === 1) {
          return yield* Effect.fail('Oops.');
        } else {
          return yield* Effect.die('BANG!');
        }
      }),
    { deps: [dbTask, cacheTask, loggerTask, scope] },
  );

  // Handle scope cleanup when main task completes
  useEffect(() => {
    const unsubscribe = mainTask.subscribe(() => {
      if (
        (mainTask.state.type === 'completed' ||
          mainTask.state.type === 'interrupted' ||
          mainTask.state.type === 'failed' ||
          mainTask.state.type === 'death') &&
        scope.state !== 'releasing' &&
        scope.state !== 'released'
      ) {
        // Run finalizers (guaranteed cleanup!)
        scope.runFinalizers();
      } else if (mainTask.state.type === 'idle') {
        // Reset scope when task resets
        scope.reset();
      }
    });

    return () => {
      unsubscribe();
      scope.reset();
    };
  }, [mainTask, scope]);

  const codeSnippet = `val result: ZIO[Any, Throwable, Report] =
  ZIO.scoped:
    for
      db     <- ZIO.acquireRelease(connectDatabase())(db => ZIO.succeed(db.close()))
      cache  <- ZIO.acquireRelease(connectCache())(cache => ZIO.succeed(cache.flush()))
      logger <- ZIO.acquireRelease(openLogFile())(file => ZIO.succeed(file.close()))
      r      <- doWork(db, cache, logger)
    yield r`;

  const taskHighlightMap = useMemo(
    () => ({
      database: { text: 'connectDatabase' },
      cache: { text: 'connectCache' },
      logger: { text: 'openLogFile' },
      result: { text: 'result' },
    }),
    [],
  );

  return (
    <EffectExample
      name="ZIO.acquireRelease"
      description="Acquire resources with guaranteed cleanup"
      code={codeSnippet}
      effects={useMemo(
        () => [dbTask, cacheTask, loggerTask],
        [dbTask, cacheTask, loggerTask],
      )}
      resultEffect={mainTask}
      effectHighlightMap={taskHighlightMap}
      scope={scope}
      exampleId="effect-acquire-release"
    />
  );
}
