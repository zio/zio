import { useEffect, useRef, useState } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/Timer.tsx.
function useTimer(task) {
  const [elapsedTime, setElapsedTime] = useState(0);
  const intervalRef = useRef(null);

  useEffect(() => {
    if (task.showTimer && task.state.type === 'running' && task.startTime) {
      const start = task.startTime;
      const updateTimer = () => {
        const now = Date.now();
        setElapsedTime(now - (start ?? now));
      };

      updateTimer();

      intervalRef.current = window.setInterval(updateTimer, 10);
    } else if (task.showTimer && task.endTime && task.startTime) {
      setElapsedTime(task.endTime - task.startTime);
    } else if (task.state.type === 'idle') {
      setElapsedTime(0);
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [task.showTimer, task.state.type, task.startTime, task.endTime]);

  return elapsedTime;
}

export function Timer({ effect: task }) {
  const elapsedTime = useTimer(task);

  const shouldShowTimer =
    task.showTimer &&
    (task.state.type === 'running' ||
      task.state.type === 'completed' ||
      task.state.type === 'failed' ||
      task.state.type === 'interrupted' ||
      task.state.type === 'death');

  const formatTime = (ms) => {
    if (ms < 1000) {
      return `${ms}ms`;
    } else {
      return `${(ms / 1000).toFixed(1)}s`;
    }
  };

  if (shouldShowTimer) {
    return <span className="font-mono">{formatTime(elapsedTime)}</span>;
  }

  return <span>{task.name}</span>;
}
