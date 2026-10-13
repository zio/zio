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

// Builds a single VisualEffect, memoized over `deps`. Sibling of
// useVisualEffects above for the common case of one task, not a map.
export function useVisualEffect(name, create, options = {}) {
  const { showTimer = false, deps = [] } = options;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => visualEffect(name, create(), showTimer), deps);
}
