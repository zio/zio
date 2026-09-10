// website/src/components/visual-effects/scenarios/ConcurrencyVisual.jsx
import React, { useCallback, useMemo } from 'react';
import { Effect } from 'effect';
import { ArrowCounterClockwiseIcon, PlayIcon, StopIcon } from '@phosphor-icons/react';
import { useVisualEffectState, visualEffect } from '../VisualEffect';
import { useVisualEffects } from '../hooks/useVisualEffects';
import EffectNode from '../effect-node/EffectNode';

// Three parallel tasks, one of which fails; the tab's copy already promises
// "if one fails, the rest are interrupted" — Effect.all with unbounded
// concurrency gives us that for free (validated headlessly in Task 1).
export default function ConcurrencyVisual() {
  const tasks = useVisualEffects({
    fetchUsers: () => Effect.sleep(900),
    fetchOrders: () => Effect.sleep(1300),
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

  const usersState = useVisualEffectState(tasks.fetchUsers);
  const ordersState = useVisualEffectState(tasks.fetchOrders);
  const profileState = useVisualEffectState(tasks.fetchProfile);
  const groupState = useVisualEffectState(group);

  const nodes = [
    { name: tasks.fetchUsers.name, state: usersState },
    { name: tasks.fetchOrders.name, state: ordersState },
    { name: tasks.fetchProfile.name, state: profileState },
  ];

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
    <div className="flex h-full flex-col items-center justify-center gap-8 p-8">
      <div className="flex flex-wrap items-center justify-center gap-8">
        {nodes.map((node) => (
          <EffectNode key={node.name} name={node.name} state={node.state} />
        ))}
      </div>
      <button
        type="button"
        onClick={handleClick}
        className="flex items-center gap-2 rounded-full bg-[var(--ifm-color-primary)] px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-[var(--ifm-color-primary-light)]"
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
