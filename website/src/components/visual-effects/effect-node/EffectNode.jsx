import React from 'react';
import { motion } from 'motion/react';
import { TASK_COLORS } from '../colors';

// Static per-state properties (color/scale/opacity), matching state.type
// exactly so no remapping is needed. Complex per-property timing (the
// running pulse, the failure shake) is overridden per-variant, same
// "hybrid" approach as the source engine's nodeVariants.
const nodeVariants = {
  idle: { backgroundColor: TASK_COLORS.idle, scale: 1, opacity: 0.6, x: 0 },
  running: {
    backgroundColor: TASK_COLORS.running,
    scale: [0.95, 1.03, 0.95],
    opacity: 1,
    x: 0,
    transition: { scale: { duration: 0.9, repeat: Infinity, ease: 'easeInOut' } },
  },
  completed: { backgroundColor: TASK_COLORS.completed, scale: [1.2, 1], opacity: 1, x: 0 },
  failed: {
    backgroundColor: TASK_COLORS.failed,
    scale: 1,
    opacity: 1,
    x: [0, -6, 6, -4, 4, 0],
    transition: { x: { duration: 0.4, ease: 'easeInOut' } },
  },
  interrupted: { backgroundColor: TASK_COLORS.interrupted, scale: 1, opacity: 1, x: 0 },
};

const STATE_LABEL = {
  idle: 'idle',
  running: 'running…',
  completed: 'done',
  failed: 'failed',
  interrupted: 'interrupted',
};

export default function EffectNode({ name, state }) {
  return (
    <div className="flex flex-col items-center gap-2">
      <motion.div
        className="h-14 w-14 rounded-2xl"
        variants={nodeVariants}
        animate={state.type}
        initial="idle"
        transition={{ type: 'spring', stiffness: 200, damping: 28 }}
      />
      <div className="text-center text-xs">
        <div className="font-semibold text-[var(--ifm-font-color-base)]">{name}</div>
        <div className="text-[var(--ifm-color-emphasis-600)]">{STATE_LABEL[state.type]}</div>
      </div>
    </div>
  );
}
