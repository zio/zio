// website/src/components/visual-effects/scenarios/ConcurrencyVisual.jsx
import React, { useCallback, useMemo } from 'react';
import { Effect } from 'effect';
import {
  ArrowCounterClockwiseIcon,
  PlayIcon,
  StopIcon,
} from '@phosphor-icons/react';
import { useVisualEffectState, visualEffect } from '../VisualEffect';
import { useVisualEffects } from '../hooks/useVisualEffects';
import EffectNode from '../effect-node/EffectNode';

// Three parallel tasks, one of which fails; the tab's copy already promises
// "if one fails, the rest are interrupted" — Effect.all with unbounded
// concurrency gives us that for free (validated headlessly in Task 1). The
// two successful tasks resolve to a short result string ("OK") rather than
// void so the node's "completed" state has something to render — matching
// the source engine's examples, which always resolve to a meaningful value.
export default function ConcurrencyVisual() {
  const tasks = useVisualEffects({
    fetchUsers: () => Effect.sleep(900).pipe(Effect.as('OK')),
    fetchOrders: () => Effect.sleep(1300).pipe(Effect.as('OK')),
    fetchProfile: () =>
      Effect.gen(function* () {
        yield* Effect.sleep(600);
        return yield* Effect.fail(new Error('Profile fetch failed'));
      }),
  });

  const taskList = useMemo(
    () => [tasks.fetchUsers, tasks.fetchOrders, tasks.fetchProfile],
    [tasks],
  );

  const group = useMemo(
    () =>
      visualEffect(
        'all',
        Effect.all(
          taskList.map((task) => task.effect),
          { concurrency: 'unbounded' },
        ),
      ),
    [taskList],
  );

  const groupState = useVisualEffectState(group);

  const isRunning = groupState.type === 'running';
  const isDone =
    groupState.type === 'completed' ||
    groupState.type === 'failed' ||
    groupState.type === 'interrupted';

  const handleClick = useCallback(() => {
    if (isRunning) {
      group.interrupt();
      taskList.forEach((task) => task.interrupt());
    } else if (isDone) {
      group.reset();
      taskList.forEach((task) => task.reset());
    } else {
      group.run();
    }
  }, [group, taskList, isRunning, isDone]);

  return (
    <div className="flex h-full w-full flex-1 flex-col items-center justify-center gap-8 p-8">
      <div className="flex flex-wrap items-center justify-center gap-8">
        {taskList.map((task) => (
          <EffectNode key={task.name} effect={task} />
        ))}
      </div>
      <button
        type="button"
        onClick={handleClick}
        className="flex items-center gap-2 rounded-full border-0 bg-[var(--ifm-color-primary)] px-4 py-2 font-[inherit] text-sm font-semibold text-white transition-colors hover:bg-[var(--ifm-color-primary-light)]"
      >
        {isRunning ? (
          <StopIcon size={16} weight="bold" />
        ) : isDone ? (
          <ArrowCounterClockwiseIcon size={16} weight="bold" />
        ) : (
          <PlayIcon size={16} weight="fill" />
        )}
        {isRunning ? 'Interrupt' : isDone ? 'Reset' : 'Run'}
      </button>
    </div>
  );
}
