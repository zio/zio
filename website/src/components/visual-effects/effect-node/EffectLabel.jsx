import { motion } from 'motion/react';
import { Timer } from '../Timer';
import { theme } from '../theme';
import { useVisualEffectState } from '../VisualEffect';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/EffectLabel.tsx.
export function EffectLabel({ effect }) {
  const state = useVisualEffectState(effect);

  return (
    <motion.div
      style={{
        marginTop: theme.spacing.sm,
        fontSize: '0.75rem',
        textAlign: 'center',
        fontWeight: 500,
        color: theme.colors.textMuted,
      }}
      animate={{
        color:
          state.type === 'idle'
            ? theme.colors.textMuted
            : theme.colors.textSecondary,
      }}
      transition={{ duration: 0.3 }}
    >
      <Timer effect={effect} />
    </motion.div>
  );
}
