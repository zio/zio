// website/src/components/visual-effects/hooks/useVisualScope.js
import { useEffect, useReducer } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/hooks/useVisualScope.ts.
export function useVisualScope(scope) {
  const [, forceUpdate] = useReducer((x) => x + 1, 0);

  useEffect(() => {
    return scope.subscribe(forceUpdate);
  }, [scope]);

  return scope;
}
