// Centralized color constants for task states. Ported verbatim (TS types
// stripped) from the source engine's src/constants/colors.ts. The
// var(--color-*) tokens resolve against Tailwind v4's default palette,
// which this site already exposes globally via `@import "tailwindcss/theme.css"`
// in src/css/custom.css.
export const TASK_COLORS = {
  idle: 'var(--color-slate-600)',
  running: 'var(--color-blue-500)',
  success: 'var(--color-green-700)',
  error: '#ef4444',
  interrupted: 'var(--color-orange-500)',
  death: '#991b1b',
};

export const GLOW_COLORS = {
  running: 'var(--color-blue-500)',
  success: 'var(--color-green-700)',
  error: 'var(--color-red-500)',
};

export const SHADOW_COLORS = {
  running: '0 0 24px rgba(59, 130, 246, 0.2)',
};
