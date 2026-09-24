import { Effect, Schedule } from 'effect';
import { useMemo } from 'react';
import { EffectExample } from '../EffectExample';
import { createCounter, getDelay } from '../examples/helpers';
import { useVisualEffect } from '../hooks/useVisualEffects';
import { visualEffect } from '../VisualEffect';

// Ported verbatim (TS types stripped, otherwise unchanged) from the source
// engine's src/examples/effect-retry-exponential.tsx — the actual
// "ZIO.retry" example from the visual-effect project, chosen for the Error
// handling tab because its retry+exponential-backoff shape matches what
// was already on this tab (see the design spec's "Round 2" section).
const parkingAttempt = createCounter(0);

const attempts = ['😤 Too Close!', '😡 Too Far!', '🤬 Neutral!', '😑 Focus.'];

function attemptParallelPark() {
  return Effect.gen(function* () {
    const delay = getDelay(400, 800);
    yield* Effect.sleep(delay);

    const attemptIndex = Math.min(parkingAttempt.current, attempts.length - 1);
    const message = attempts[attemptIndex] ?? '😬 Try Again!';

    parkingAttempt.increment();

    // Reset counter after giving up
    if (parkingAttempt.current > attempts.length) {
      return '🚗 Parked!';
    }

    return yield* Effect.fail(message);
  });
}

export default function RetryExponentialVisual() {
  const baseTask = useVisualEffect('park', attemptParallelPark);

  const repeatedTask = useMemo(
    () =>
      visualEffect(
        'result',
        Effect.retry(baseTask.effect, Schedule.exponential('700 millis')).pipe(
          Effect.ensuring(parkingAttempt.reset),
        ),
      ),
    [baseTask],
  );

  const codeSnippet = `val park = attemptParallelPark()
val result = park.retry(Schedule.exponential(700.millis))`;

  const taskHighlightMap = useMemo(
    () => ({
      park: { text: 'attemptParallelPark()' },
      result: { text: 'park.retry(Schedule.exponential(700.millis))' },
    }),
    [],
  );

  return (
    <EffectExample
      name="ZIO#retry"
      variant="exponential"
      description="Retry with exponential backoff"
      code={codeSnippet}
      effects={useMemo(() => [baseTask], [baseTask])}
      resultEffect={repeatedTask}
      effectHighlightMap={taskHighlightMap}
      showScheduleTimeline={true}
      exampleId="effect-retry-exponential"
    />
  );
}
