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

const CHIP_STYLES = {
  queued: 'border-neutral-700 bg-neutral-800 text-neutral-400',
  buffered: 'border-amber-500 bg-amber-900 text-amber-300',
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
      exit={{ opacity: 0, scale: 0.6, transition: { duration: 0.15 } }}
      transition={{ type: 'spring', visualDuration: 1, bounce: 0.2 }}
      className={`relative flex h-7 w-11 items-center justify-center rounded-md border font-mono text-xs font-medium ${CHIP_STYLES[item.stage]}`}
      style={{ willChange: 'transform, opacity' }}
    >
      <span className="relative z-10">#{item.id}</span>
    </motion.div>
  );
}

// Ticks while any timed stage is in flight so each bar advances against its
// own real start time / duration — several enrich bars filling at once at
// visibly different rates is what makes the parallelism legible, and the
// single write bar is what makes the slow consumer's pacing legible.
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
function WorkSlot({ item, tone }) {
  if (!item) {
    return (
      <div className="flex h-10 items-center justify-center rounded-md border border-dashed border-neutral-700/70">
        <span className="font-mono text-[10px] text-neutral-500">idle</span>
      </div>
    );
  }

  const elapsed = Date.now() - item.startedAt;
  const progress = Math.min(1, elapsed / item.durationMs);

  // The item's own work is done but it is still holding this slot: the only
  // thing keeping it here is that the bounded buffer downstream has no room.
  // That is the backpressure moment, per item, so say so rather than leaving
  // a finished bar sitting under an "enriching" label.
  const blocked = progress >= 1;
  const shown = blocked ? BLOCKED_TONE : tone;

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ type: 'spring', visualDuration: 0.25, bounce: 0.2 }}
      className={`flex h-10 flex-col justify-center gap-1 rounded-md border px-1.5 ${shown.container}`}
    >
      <div
        className={`flex items-baseline justify-between font-mono text-[11px] leading-none ${shown.label}`}
      >
        <span>#{item.id}</span>
        <span className={`text-[9px] ${shown.duration}`}>
          {blocked ? 'blocked' : `${item.durationMs}ms`}
        </span>
      </div>
      <div className={`h-1 overflow-hidden rounded-full ${shown.track}`}>
        <div
          className={`h-full rounded-full ${shown.bar}`}
          style={{ width: `${progress * 100}%` }}
        />
      </div>
    </motion.div>
  );
}

const BLOCKED_TONE = {
  container: 'border-amber-600 bg-amber-950',
  label: 'text-amber-300',
  duration: 'text-amber-400',
  track: 'bg-amber-950',
  bar: 'bg-amber-500',
};

const ENRICH_TONE = {
  container: 'border-blue-500 bg-blue-900',
  label: 'text-blue-300',
  duration: 'text-blue-400/80',
  track: 'bg-blue-950',
  bar: 'bg-blue-400',
};

const WRITE_TONE = {
  container: 'border-purple-500 bg-purple-900',
  label: 'text-purple-300',
  duration: 'text-purple-400/80',
  track: 'bg-purple-950',
  bar: 'bg-purple-400',
};

export function PipelineStages({ pipeline }) {
  useStreamPipeline(pipeline);

  const byStage = (stage) => pipeline.items.filter((i) => i.stage === stage);
  const queued = byStage('queued');
  const enriching = byStage('enriching');
  const buffered = byStage('buffered');
  const writing = byStage('writing');
  const written = byStage('written');

  useAnimationTick(enriching.length > 0 || writing.length > 0);

  // Two tells, both meaning the bounded buffer is refusing more work and the
  // Stream is propagating the slow writer's pace upstream: an enrich slot has
  // finished but can't hand its item off, or work is waiting upstream while
  // enrich slots sit free.
  const backpressured =
    enriching.some((i) => Date.now() - i.startedAt >= i.durationMs) ||
    (queued.length > 0 && enriching.length < pipeline.concurrency);

  const enrichSlots = Array.from(
    { length: pipeline.concurrency },
    (_, index) => {
      const item = enriching[index];
      return { key: item ? `item-${item.id}` : `empty-${index}`, item };
    },
  );

  return (
    <div className="flex flex-col gap-2 p-4">
      <div className="grid grid-cols-5 gap-2">
        <Lane label="Source" items={queued} />

        <div className="flex flex-col gap-2">
          <LaneLabel>
            Enrich · {enriching.length} of {pipeline.concurrency}
          </LaneLabel>
          {/* 2x2 rather than a 4-row column: four stacked slots made the
              card taller than the panel's fixed height and pushed the code
              block out of view entirely. */}
          <div className="grid min-h-[88px] grid-cols-2 content-start gap-1.5 rounded-lg border border-dashed border-blue-900/60 p-2">
            {enrichSlots.map(({ key, item }) => (
              <WorkSlot key={key} item={item} tone={ENRICH_TONE} />
            ))}
          </div>
        </div>

        {/* Named for what it measures, not for the buffer: the marking tap
            has to sit before Stream.buffer (after it, the writer consumes
            each element immediately and the lane would always read empty),
            so an item is counted the moment enrich produces it — including
            the one blocked at a full queue's door. That makes the peak
            exactly capacity + 1, which looked like a bug when the lane was
            labelled "Buffer" next to a .buffer(2) in the snippet. Every item
            here is genuinely waiting on the writer, whichever side of the
            queue boundary it sits on. */}
        <Lane
          label={`Waiting for writer · ${buffered.length}`}
          items={buffered}
          full={buffered.length >= pipeline.capacity}
        />

        <div className="flex flex-col gap-2">
          <LaneLabel>Write · 1 at a time</LaneLabel>
          <div className="flex min-h-[88px] flex-col content-start gap-1.5 rounded-lg border border-dashed border-purple-900/60 p-2">
            <WorkSlot item={writing[0]} tone={WRITE_TONE} />
          </div>
        </div>

        <Lane label="Written" items={written} />
      </div>

      <div className="h-4 text-center text-xs">
        {backpressured && (
          <motion.span
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-amber-600"
          >
            backpressure — buffer full, enrichment paused until the writer
            catches up
          </motion.span>
        )}
      </div>
    </div>
  );
}

function LaneLabel({ children }) {
  return (
    <span className="text-center text-xs tracking-wide text-neutral-500 uppercase">
      {children}
    </span>
  );
}

function Lane({ label, items, full }) {
  return (
    <div className="flex flex-col gap-2">
      <LaneLabel>{label}</LaneLabel>
      <div
        className={`flex min-h-[88px] flex-wrap content-start justify-center gap-1.5 rounded-lg border border-dashed p-2 ${
          full ? 'border-amber-600/70 bg-amber-950/20' : 'border-neutral-800'
        }`}
      >
        <AnimatePresence mode="popLayout">
          {items.map((item) => (
            <ItemChip key={item.id} item={item} />
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}
