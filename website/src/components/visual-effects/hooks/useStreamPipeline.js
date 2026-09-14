// website/src/components/visual-effects/hooks/useStreamPipeline.js
//
// Bespoke — no source equivalent (see StreamPipeline.js). Same
// subscribe-and-force-update shape as useVisualScope.js.
import { useEffect, useReducer } from 'react';

export function useStreamPipeline(pipeline) {
  const [, forceUpdate] = useReducer((x) => x + 1, 0);

  useEffect(() => {
    return pipeline.subscribe(forceUpdate);
  }, [pipeline]);

  return pipeline;
}
