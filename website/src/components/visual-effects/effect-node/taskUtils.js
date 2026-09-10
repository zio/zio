import { SHADOW_COLORS } from '../colors';
import { theme } from '../theme';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/taskUtils.ts.
export function getTaskShadow(state) {
  switch (state.type) {
    case 'running':
      return SHADOW_COLORS.running;
    default:
      return theme.shadow.sm;
  }
}
