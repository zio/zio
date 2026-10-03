// website/src/components/visual-effects/hooks/useLayerGraph.js
//
// Bespoke — no source equivalent (see ../LayerGraph.js). Same
// subscribe-and-force-update shape as useVisualScope.js.
import { useEffect, useReducer } from 'react';

export function useLayerGraph(graph) {
  const [, forceUpdate] = useReducer((x) => x + 1, 0);

  useEffect(() => {
    return graph.subscribe(forceUpdate);
  }, [graph]);

  return graph;
}
