import { Effect } from 'effect';
import { useMemo } from 'react';
import { EffectExample } from '../EffectExample';
import { Emoji, loadEmoji } from '../examples/helpers';
import { useVisualEffects } from '../hooks/useVisualEffects';
import { visualEffect } from '../VisualEffect';

// Ported verbatim (TS types stripped, otherwise unchanged) from the source
// engine's src/examples/effect-race.tsx — this is the actual "concurrency"
// example from the visual-effect project, not a bespoke scenario. Metadata
// (name/description) matches its entry in the source's examples manifest
// exactly: id "effect-race", section "concurrency".
export default function RaceVisual() {
  // Create tasks with variable delays for realistic racing
  const { tortoise, achilles } = useVisualEffects({
    tortoise: () => loadEmoji(Emoji.Tortoise),
    achilles: () => loadEmoji(Emoji.Achilles),
  });

  // Create race task
  const raceResult = useMemo(() => {
    // Race Effects - returns the first one to complete
    const raceEffect = Effect.race(tortoise.effect, achilles.effect);
    return visualEffect('winner', raceEffect);
  }, [tortoise, achilles]);

  // Memoize tasks array
  const tasks = useMemo(() => [tortoise, achilles], [tortoise, achilles]);

  // Code snippet
  const codeSnippet = `val tortoise = runFast("tortoise")
val achilles = runFast("achilles")

val winner = tortoise.race(achilles)`;

  // Mapping between task name and the text to highlight
  const taskHighlightMap = useMemo(
    () => ({
      tortoise: {
        text: 'runFast("tortoise")',
      },
      achilles: {
        text: 'runFast("achilles")',
      },
      winner: {
        text: 'tortoise.race(achilles)',
      },
    }),
    [],
  );

  return (
    <EffectExample
      name="ZIO#race"
      description="Race two effects and return the result of the first successful one"
      code={codeSnippet}
      effects={tasks}
      resultEffect={raceResult}
      effectHighlightMap={taskHighlightMap}
      exampleId="effect-race"
    />
  );
}
