// website/src/components/visual-effects/layers/LayerGraphView.jsx
//
// Bespoke — no source equivalent (see ../LayerGraph.js). Laid out by
// dependency depth rather than hardcoded positions: layers requiring nothing
// sit in the first column, whatever depends on them in the next, so the
// columns read left-to-right in construction order — and a column is also the
// set the runtime builds concurrently.
//
// The two DI-specific things it surfaces, which a plain dependency graph does
// not: a shared layer's "built 1x / used by 2" count plus the instance each
// consumer received, and the environment filling up underneath.
import { ArrowRightIcon } from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { useLayerGraph } from '../hooks/useLayerGraph';

const CARD_STYLES = {
  // Dashed and unfilled, matching the "idle" slots in the Streaming lanes —
  // a filled grey card rendered its label at near-zero contrast.
  pending: 'border-dashed border-neutral-500 bg-transparent',
  building: 'border-blue-500 bg-blue-900',
  ready: 'border-green-500 bg-green-900',
};

const LABEL_STYLES = {
  pending: 'text-neutral-500',
  building: 'text-blue-300',
  ready: 'text-green-300',
};

const STATE_TEXT = {
  pending: 'waiting',
  building: 'building',
  ready: 'ready',
};

// Ticks while any layer is under construction so each bar advances against
// its own real start time and duration.
function useAnimationTick(active) {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!active) return;

    let frame;
    const loop = () => {
      setTick((tick) => tick + 1);
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);

    return () => cancelAnimationFrame(frame);
  }, [active]);
}

function LayerCard({ layer, receivedInstance }) {
  const progress =
    layer.state === 'building'
      ? Math.min(1, (Date.now() - layer.startedAt) / layer.durationMs)
      : layer.state === 'ready'
        ? 1
        : 0;

  // Only worth calling out where it is surprising: a layer more than one
  // service asked for, which was still constructed exactly once.
  const shared = layer.usedBy.length > 1;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', visualDuration: 0.3, bounce: 0.2 }}
      className={`flex w-[200px] flex-col justify-center gap-1 rounded-md border px-2 py-1.5 ${CARD_STYLES[layer.state]}`}
    >
      <div
        className={`flex items-baseline justify-between gap-2 font-mono text-[11px] leading-none ${LABEL_STYLES[layer.state]}`}
      >
        <span className="truncate">{layer.label}</span>
        <span className="shrink-0 text-[9px] opacity-80">
          {receivedInstance ?? STATE_TEXT[layer.state]}
        </span>
      </div>

      <div className="h-1 overflow-hidden rounded-full bg-black/30">
        <div
          className={`h-full rounded-full ${
            layer.state === 'ready' ? 'bg-green-400' : 'bg-blue-400'
          }`}
          style={{ width: `${progress * 100}%` }}
        />
      </div>

      {shared && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="font-mono text-[9px] leading-none text-amber-400"
        >
          built {layer.buildCount}× · used by {layer.usedBy.length}
        </motion.div>
      )}
    </motion.div>
  );
}

export function LayerGraphView({ graph }) {
  useLayerGraph(graph);
  useAnimationTick(graph.layers.some((layer) => layer.state === 'building'));

  const depthOf = (layer) =>
    layer.dependsOn.length === 0
      ? 0
      : 1 + Math.max(...layer.dependsOn.map((id) => depthOf(graph.find(id))));

  const columns = [];
  for (const layer of graph.layers) {
    (columns[depthOf(layer)] ??= []).push(layer);
  }

  // What each consumer was handed, so the shared instance shows on the
  // consumer side too — the same id appearing twice is the whole point.
  const receivedBy = {};
  for (const layer of graph.layers) {
    for (const use of layer.usedBy) {
      receivedBy[use.consumerId] = use.instance;
    }
  }

  return (
    <div className="flex flex-col gap-2 px-4 pt-3 pb-2">
      <div className="flex items-center justify-center gap-3">
        {columns.map((column, index) => (
          <div key={index} className="flex items-center gap-3">
            {index > 0 && (
              <div className="text-neutral-500">
                <ArrowRightIcon size={18} weight="fill" />
              </div>
            )}
            <div className="flex flex-col gap-2">
              {column.map((layer) => (
                <LayerCard
                  key={layer.id}
                  layer={layer}
                  receivedInstance={receivedBy[layer.id]}
                />
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* The R of ZIO[R, E, A] being satisfied one layer at a time. */}
      <div className="flex flex-wrap items-center justify-center gap-1.5 font-mono text-[10px] text-neutral-500">
        <span className="shrink-0">environment:</span>
        <span className="text-neutral-600">{'{'}</span>
        {graph.environment.length === 0 && (
          <span className="text-neutral-600">nothing provided yet</span>
        )}
        <AnimatePresence mode="popLayout">
          {graph.environment.map((id) => (
            <motion.span
              key={id}
              layout
              initial={{ opacity: 0, scale: 0.7 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.7 }}
              transition={{ type: 'spring', visualDuration: 0.3, bounce: 0.2 }}
              className="rounded border border-green-600/60 bg-green-900/40 px-1.5 py-0.5 text-green-400"
            >
              {id}
            </motion.span>
          ))}
        </AnimatePresence>
        <span className="text-neutral-600">{'}'}</span>
      </div>
    </div>
  );
}
