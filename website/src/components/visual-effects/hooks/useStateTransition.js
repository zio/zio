import { useEffect, useRef } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/hooks/useStateTransition.ts.
export function useStateTransition(state) {
  const prevStateRef = useRef(state.type);
  const currentState = state.type;
  const previousState = prevStateRef.current;

  useEffect(() => {
    prevStateRef.current = currentState;
  }, [currentState]);

  return {
    justStarted: previousState !== 'running' && currentState === 'running',
    justCompleted:
      previousState !== 'completed' && currentState === 'completed',
    justFailed: previousState !== 'failed' && currentState === 'failed',
    justInterrupted:
      previousState !== 'interrupted' && currentState === 'interrupted',
    previousState,
    currentState,
  };
}
