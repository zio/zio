// website/src/components/visual-effects/pipeline/PipelineStages.jsx
//
// Bespoke — no source equivalent (see ../StreamPipeline.js). Renders each
// event as a small chip in whichever lane matches its current pipeline
// stage. Unlike ScopeStack's manual absolute-positioning + ResizeObserver
// (built for its pending/running/completed layout), items here just move
// between ordinary flex lanes and motion's `layoutId` handles the
// cross-lane transition — the simpler tool for this shape of layout.
import { AnimatePresence, motion } from 'motion/react';
import { useStreamPipeline } from '../hooks/useStreamPipeline';

// 'valid' is a real intermediate stage (an item that passed .filter but
// hasn't been swept into a full grouped(3) batch yet) — it shares the
// Group lane with 'batching' rather than getting its own column, since
// without this an item sits in neither lane (invisible) for however long
// it takes the remaining items in its group to finish enriching.
const LANES = [
  { stages: ['queued'], label: 'Source' },
  { stages: ['enriching'], label: 'Enrich (mapZIOPar)' },
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
  const isEnriching = item.stage === 'enriching';

  return (
    <motion.div
      layoutId={`pipeline-item-${item.id}`}
      layout
      initial={{ opacity: 0, scale: 0.6 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.6 }}
      transition={{ type: 'spring', visualDuration: 0.35, bounce: 0.2 }}
      className={`relative flex h-7 w-11 items-center justify-center rounded-md border font-mono text-xs font-medium ${CHIP_STYLES[item.stage]}`}
      style={{ willChange: 'transform, opacity' }}
    >
      {isEnriching && (
        <motion.div
          className="absolute inset-0 rounded-md"
          animate={{ opacity: [0.3, 0, 0.3] }}
          transition={{ duration: 1.2, repeat: Infinity, ease: 'easeInOut' }}
          style={{
            background:
              'radial-gradient(circle, rgba(59, 130, 246, 0.4) 0%, transparent 70%)',
          }}
        />
      )}
      <span className="relative z-10">#{item.id}</span>
    </motion.div>
  );
}

export function PipelineStages({ pipeline }) {
  useStreamPipeline(pipeline);

  const filteredCount = pipeline.items.filter(
    (item) => item.stage === 'filteredOut',
  ).length;

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="grid grid-cols-4 gap-3">
        {LANES.map(({ stages, label }) => {
          const items = pipeline.items.filter((item) =>
            stages.includes(item.stage),
          );

          return (
            <div key={label} className="flex flex-col gap-2">
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
        })}
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
