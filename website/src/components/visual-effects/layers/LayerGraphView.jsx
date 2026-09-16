// website/src/components/visual-effects/layers/LayerGraphView.jsx
//
// Bespoke — no source equivalent (see ../LayerGraph.js). Lays the graph out
// by dependency depth rather than by hardcoded positions: every layer with no
// dependencies sits in the first column, everything that depends on them in
// the next, so the columns read left-to-right in construction order.
import { motion } from 'motion/react';
import { ArrowRightIcon } from '@phosphor-icons/react';
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

function LayerCard({ layer }) {
  const progress =
    layer.state === 'building'
      ? Math.min(1, (Date.now() - layer.startedAt) / layer.durationMs)
      : layer.state === 'ready'
        ? 1
        : 0;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', visualDuration: 0.3, bounce: 0.2 }}
      className={`flex h-11 w-[184px] flex-col justify-center gap-1 rounded-md border px-2 ${CARD_STYLES[layer.state]}`}
    >
      <div
        className={`flex items-baseline justify-between gap-2 font-mono text-[11px] leading-none ${LABEL_STYLES[layer.state]}`}
      >
        <span className="truncate">{layer.label}</span>
        <span className="shrink-0 text-[9px] opacity-80">
          {STATE_TEXT[layer.state]}
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
    </motion.div>
  );
}

export function LayerGraphView({ graph }) {
  useLayerGraph(graph);
  useAnimationTick(graph.layers.some((layer) => layer.state === 'building'));

  // Depth = longest chain of dependencies behind this layer. Layers at the
  // same depth have nothing to wait on from each other, which is exactly the
  // set the runtime constructs in parallel — so a column is also a "these
  // build together" group.
  const depthOf = (layer) =>
    layer.dependsOn.length === 0
      ? 0
      : 1 +
        Math.max(
          ...layer.dependsOn.map((id) =>
            depthOf(graph.layers.find((l) => l.id === id)),
          ),
        );

  const columns = [];
  for (const layer of graph.layers) {
    const depth = depthOf(layer);
    (columns[depth] ??= []).push(layer);
  }

  return (
    <div className="flex items-center justify-center gap-3 p-4">
      {columns.map((column, index) => (
        <div key={index} className="flex items-center gap-3">
          {index > 0 && (
            <div className="text-neutral-500">
              <ArrowRightIcon size={18} weight="fill" />
            </div>
          )}
          <div className="flex flex-col gap-2">
            {column.map((layer) => (
              <LayerCard key={layer.id} layer={layer} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
