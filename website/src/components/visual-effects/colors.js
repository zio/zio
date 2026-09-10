// State colors for VisualEffect nodes. Semantic (blue=running,
// green=completed, red=failed, orange=interrupted), not brand colors, so
// they don't need to match zio.dev's red/amber accent palette.
export const TASK_COLORS = {
  idle: '#64748b',
  running: '#3b82f6',
  completed: '#15803d',
  failed: '#ef4444',
  interrupted: '#f97316',
};
