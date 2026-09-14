// website/src/components/visual-effects/pipeline/PipelineStages.jsx
//
// Bespoke — no source equivalent (see ../StreamPipeline.js). Renders each
// event as a small chip in whichever lane matches its current pipeline
// stage. Unlike ScopeStack's manual absolute-positioning + ResizeObserver
// (built for its pending/running/completed layout), items here just move
// between ordinary flex lanes and motion's `layoutId` handles the
// cross-lane transition — the simpler tool for this shape of layout.
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useState } from 'react';
import { useStreamPipeline } from '../hooks/useStreamPipeline';

// 'valid' is a real intermediate stage (an item that passed .filter but
// hasn't been swept into a full grouped(3) batch yet) — it shares the
// Group lane with 'batching' rather than getting its own column, since
// without this an item sits in neither lane (invisible) for however long
// it takes the remaining items in its group to finish enriching.
const LANES = [
  { stages: ['queued'], label: 'Source' },
  { stages: ['valid', 'batching'], label: 'Group' },
  { stages: ['written'], label: 'Written' },
];

const CHIP_STYLES = {
  queued: 'border-neutral-700 bg-neutral-800 text-neutral-400',
  valid: 'border-amber-700 bg-amber-950 text-amber-400',
  enriching: 'border-blue-500 bg-blue-900 text-blue-300',
  batching: 'border-amber-500 bg-amber-900 text-amber-300',
  written: 'border-green-500 bg-green-900 text-green-300',
};

function ItemChip({ item }) {
  return (
    <motion.div
      layoutId={`pipeline-item-${item.id}`}
      layout
      initial={{ opacity: 0, scale: 0.6 }}
      animate={{ opacity: 1, scale: 1 }}
      // Quick exit on purpose: popLayout absolutely-positions a leaving chip
      // at its old coordinates while its siblings reflow, so a slow fade
      // reads as chips overlapping each other.
      exit={{ opacity: 0, scale: 0.6, transition: { duration: 0.12 } }}
      transition={{ type: 'spring', visualDuration: 0.35, bounce: 0.2 }}
      className={`relative flex h-7 w-11 items-center justify-center rounded-md border font-mono text-xs font-medium ${CHIP_STYLES[item.stage]}`}
      style={{ willChange: 'transform, opacity' }}
    >
      <span className="relative z-10">#{item.id}</span>
    </motion.div>
  );
}

// Ticks while anything is enriching so each in-flight bar advances against
// its own real start time / duration — several bars filling at once, at
// visibly different rates, is the whole point of this lane.
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

// Deliberately not sharing layoutId with ItemChip: a slot and a chip are
// very different shapes, and morphing between them produced overlapping
// artifacts mid-flight. A plain fade in/out reads more clearly here.
function EnrichSlot({ item }) {
  if (!item) {
    return (
      <div className="flex h-10 items-center justify-center rounded-md border border-dashed border-neutral-700/70">
        <span className="font-mono text-[10px] text-neutral-500">idle</span>
      </div>
    );
  }

  const elapsed = Date.now() - item.startedAt;
  const progress = Math.min(1, elapsed / item.durationMs);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', visualDuration: 0.25, bounce: 0.2 }}
      className="flex h-10 flex-col justify-center gap-1 rounded-md border border-blue-500 bg-blue-900 px-1.5"
    >
      <div className="flex items-baseline justify-between font-mono text-[11px] leading-none text-blue-300">
        <span>#{item.id}</span>
        <span className="text-[9px] text-blue-400/80">{item.durationMs}ms</span>
      </div>
      <div className="h-1 overflow-hidden rounded-full bg-blue-950">
        <div
          className="h-full rounded-full bg-blue-400"
          style={{ width: `${progress * 100}%` }}
        />
      </div>
    </motion.div>
  );
}

export function PipelineStages({ pipeline }) {
  useStreamPipeline(pipeline);

  const enriching = pipeline.items.filter((item) => item.stage === 'enriching');
  useAnimationTick(enriching.length > 0);

  const filteredCount = pipeline.items.filter(
    (item) => item.stage === 'filteredOut',
  ).length;

  // One slot per unit of the Stream's actual concurrency limit. Empty slots
  // stay visible so the lane reads as "N workers, this many busy right now"
  // rather than just a box that happens to contain some chips.
  const slots = Array.from({ length: pipeline.concurrency }, (_, index) => {
    const item = enriching[index];
    return { key: item ? `item-${item.id}` : `empty-${index}`, item };
  });

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="grid grid-cols-4 gap-3">
        <Lane label={LANES[0].label} pipeline={pipeline} lane={LANES[0]} />

        <div className="flex flex-col gap-2">
          <span className="text-center text-xs tracking-wide text-neutral-500 uppercase">
            Enrich · {enriching.length} of {pipeline.concurrency} in parallel
          </span>
          {/* 2x2 rather than a 4-row column: four stacked slots made the
              card taller than the panel's fixed height and pushed the code
              block out of view entirely. */}
          <div className="grid min-h-[88px] grid-cols-2 content-start gap-1.5 rounded-lg border border-dashed border-blue-900/60 p-2">
            {slots.map(({ key, item }) => (
              <EnrichSlot key={key} item={item} />
            ))}
          </div>
        </div>

        <Lane label={LANES[1].label} pipeline={pipeline} lane={LANES[1]} />
        <Lane label={LANES[2].label} pipeline={pipeline} lane={LANES[2]} />
      </div>

      {filteredCount > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="text-center text-xs text-neutral-500"
        >
          {filteredCount} filtered out by <code>_.isValid</code>
        </motion.div>
      )}
    </div>
  );
}

function Lane({ label, lane, pipeline }) {
  const items = pipeline.items.filter((item) =>
    lane.stages.includes(item.stage),
  );

  return (
    <div className="flex flex-col gap-2">
      <span className="text-center text-xs tracking-wide text-neutral-500 uppercase">
        {label}
      </span>
      <div className="flex min-h-[88px] flex-wrap content-start justify-center gap-1.5 rounded-lg border border-dashed border-neutral-800 p-2">
        <AnimatePresence mode="popLayout">
          {items.map((item) => (
            <ItemChip key={item.id} item={item} />
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}
