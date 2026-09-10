import { useMemo } from 'react';
import { visualEffect } from '../VisualEffect';

// Builds a map of VisualEffects from `{ name: () => Effect }` definitions,
// memoized once (over `deps`) so the same instances persist across renders.
export function useVisualEffects(definitions, deps = []) {
  return useMemo(() => {
    const effects = {};
    for (const [name, create] of Object.entries(definitions)) {
      effects[name] = visualEffect(name, create());
    }
    return effects;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
}
