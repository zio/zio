import { springs } from '../animations';
import { TASK_COLORS } from '../colors';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/nodeVariants.ts.
//
// Hybrid approach: Only handle static state-based properties in variants.
// Keep complex animations (jitter, pulses, flashes, dynamic sizing) imperative.
export const nodeVariants = {
  idle: {
    scale: 1,
    opacity: 0.6,
    backgroundColor: TASK_COLORS.idle,
    transition: {
      // Fast color change to match original
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      // Keep spring for scale/opacity
      scale: springs.default,
      opacity: springs.default,
    },
  },

  running: {
    scale: 0.95,
    opacity: 1,
    backgroundColor: TASK_COLORS.running,
    transition: {
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      scale: springs.default,
      opacity: springs.default,
    },
  },

  completed: {
    scale: 1,
    opacity: 1,
    backgroundColor: TASK_COLORS.success,
    transition: {
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      scale: springs.contentScale,
      opacity: springs.contentScale,
    },
  },

  failed: {
    backgroundColor: TASK_COLORS.error,
    scale: 1,
    opacity: 1,
    transition: {
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      scale: springs.contentScale,
      opacity: springs.contentScale,
    },
  },

  death: {
    backgroundColor: TASK_COLORS.death,
    scale: 1,
    opacity: 1,
    transition: {
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      scale: springs.contentScale,
      opacity: springs.contentScale,
    },
  },

  interrupted: {
    backgroundColor: TASK_COLORS.interrupted,
    opacity: 1,
    scale: 1,
    transition: {
      backgroundColor: { duration: 0.1, ease: 'easeInOut' },
      scale: springs.default,
      opacity: springs.default,
    },
  },
};

// Note: Width, height, and complex animations remain imperative.
// This preserves dynamic width expansion and complex timing sequences.
