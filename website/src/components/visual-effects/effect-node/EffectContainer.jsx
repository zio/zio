import { motion, useTransform } from 'motion/react';
import { colors, effects } from '../animations';
import { nodeVariants } from './nodeVariants';
import { getTaskShadow } from './taskUtils';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/EffectContainer.tsx.
export function EffectContainer({
  motionValues,
  children,
  onMouseEnter,
  onMouseLeave,
  state,
}) {
  const isDeath = state.type === 'death';
  // Use variants for static state-based properties
  const current = state.type;

  return (
    <motion.div
      // Hybrid approach: variants handle static properties
      variants={nodeVariants}
      animate={current}
      initial={false}
      style={{
        // Imperative motion values drive dynamic sizing
        width: motionValues.nodeWidth,
        height: motionValues.nodeHeight,
        borderRadius: motionValues.borderRadius,
        position: 'absolute',
        overflow: 'hidden',
        // Variants still own scale, opacity, background color
        rotate: motionValues.rotation,
        x: motionValues.shakeX,
        y: motionValues.shakeY,
        cursor: 'auto',
        border: isDeath
          ? `2px solid ${colors.border.death}`
          : `1px solid ${colors.border.default}`,
        // Promote to its own GPU layer and limit reflows/paints
        contain: 'layout style paint',
        willChange: 'transform, filter',
        transform: 'translateZ(0)', // ensure GPU compositing

        filter: useTransform([motionValues.blurAmount], ([blur = 0]) => {
          // Cap blur radius to 2px max for better performance
          const cappedBlur = Math.min(blur, 2);

          return isDeath
            ? `blur(${cappedBlur}px) contrast(${effects.death.contrast}) brightness(${effects.death.brightness})`
            : `blur(${cappedBlur}px)`;
        }),
        // Use box-shadow for glow instead of expensive drop-shadow
        boxShadow: useTransform([motionValues.glowIntensity], ([glow = 0]) => {
          const cappedGlow = Math.min(glow, 8);
          const baseGlow = getTaskShadow(state);

          if (isDeath) {
            return cappedGlow > 0
              ? `${baseGlow}, 0 0 ${cappedGlow * 2}px ${colors.glow.death}`
              : baseGlow;
          }

          return cappedGlow > 0
            ? `${baseGlow}, 0 0 ${cappedGlow}px ${colors.glow.running}`
            : baseGlow;
        }),
      }}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      {children}
    </motion.div>
  );
}
