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

// One fixed height for every lane box so the five columns line up top and
// bottom instead of each sizing to its own content (the Written lane fills
// up once everything lands, which left the others short). Sized for that
// worst case: 3 rows x h-7 chips + 2 gaps + p-2 padding — the event count is
// 9 precisely so 3 chips per row fill exactly three rows. Also clears the
// enrich lane's 2 rows of h-10 slots.
const LANE_BOX_HEIGHT = 'h-[112px]';

const CHIP_STYLES = {
  queued: 'border-neutral-700 bg-neutral-800 text-neutral-400',
  buffered: 'border-amber-500 bg-amber-900 text-amber-300',
  written: 'border-green-500 bg-green-900 text-green-300',
};

// styleKey lets a lane colour its chips by the lane's meaning rather than the
// item's raw stage — items still tagged 'enriching' that have finished their
// work are shown in the waiting lane and should look like the rest of it.
function ItemChip({ item, styleKey }) {
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
      className={`relative flex h-7 w-11 items-center justify-center rounded-md border font-mono text-xs font-medium ${CHIP_STYLES[styleKey ?? item.stage]}`}
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
  const shown = tone;

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
          {item.durationMs}ms
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
  // An item whose enrich work has finished no longer holds a concurrency
  // permit — Effect releases it the moment the effect completes, and starts
  // the next item, while the finished one waits to be handed downstream.
  // Keeping those in the enrich slots overflowed the lane (5 items, 4 slots,
  // one silently not rendered) and misrepresented what they're doing: they
  // are waiting on the writer, so that's the lane they belong in.
  const enrichingAll = byStage('enriching');
  const isFinished = (i) => Date.now() - i.startedAt >= i.durationMs;
  const enriching = enrichingAll.filter((i) => !isFinished(i));
  const handingOff = enrichingAll.filter(isFinished);
  const buffered = byStage('buffered');
  const waiting = [...handingOff, ...buffered];
  const writing = byStage('writing');
  const written = byStage('written');

  useAnimationTick(enrichingAll.length > 0 || writing.length > 0);

  // Two tells, both meaning the bounded buffer is refusing more work and the
  // Stream is propagating the slow writer's pace upstream: an item has
  // finished enriching but can't hand off, or work is waiting upstream while
  // enrich slots sit free *and* the buffer is at capacity.
  //
  // Both are gated on the pipeline actually running — at rest every item is
  // queued and no slot is busy, which satisfied the second tell and left the
  // caption showing before the visitor had even pressed run.
  const inFlight = enrichingAll.length + waiting.length + writing.length > 0;
  const backpressured =
    inFlight &&
    (handingOff.length > 0 ||
      (queued.length > 0 &&
        enriching.length < pipeline.concurrency &&
        waiting.length >= pipeline.capacity));

  const slotsFor = (items, count) =>
    Array.from({ length: count }, (_, index) => {
      const item = items[index];
      return { key: item ? `item-${item.id}` : `empty-${index}`, item };
    });

  const enrichSlots = slotsFor(enriching, pipeline.concurrency);
  const writeSlots = slotsFor(writing, pipeline.writeConcurrency);

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
          <div
            className={`grid ${LANE_BOX_HEIGHT} grid-cols-2 content-start gap-1.5 rounded-lg border border-dashed border-blue-900/60 p-2`}
          >
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
          label={`Waiting for writer · ${waiting.length}`}
          items={waiting}
          styleKey="buffered"
          full={waiting.length >= pipeline.capacity}
        />

        <div className="flex flex-col gap-2">
          <LaneLabel>
            Write · {writing.length} of {pipeline.writeConcurrency}
          </LaneLabel>
          <div
            className={`flex ${LANE_BOX_HEIGHT} flex-col content-start gap-1.5 rounded-lg border border-dashed border-purple-900/60 p-2`}
          >
            {writeSlots.map(({ key, item }) => (
              <WorkSlot key={key} item={item} tone={WRITE_TONE} />
            ))}
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

function Lane({ label, items, full, styleKey }) {
  return (
    <div className="flex flex-col gap-2">
      <LaneLabel>{label}</LaneLabel>
      <div
        className={`flex ${LANE_BOX_HEIGHT} flex-wrap content-start justify-center gap-1.5 rounded-lg border border-dashed p-2 ${
          full ? 'border-amber-600/70 bg-amber-950/20' : 'border-neutral-800'
        }`}
      >
        <AnimatePresence mode="popLayout">
          {items.map((item) => (
            <ItemChip key={item.id} item={item} styleKey={styleKey} />
          ))}
        </AnimatePresence>
      </div>
    </div>
  );
}
