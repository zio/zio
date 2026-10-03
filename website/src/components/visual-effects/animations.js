// Animation configuration for consistent motion design across components.
// Ported verbatim (TS types stripped) from the source visual-effect engine's
// src/animations.ts.

// Default spring for MotionConfig wrapper
export const defaultSpring = {
  type: 'spring',
  // Critical damping occurs when damping ≈ 2 * sqrt(stiffness * mass).
  // With mass = 1, choose stiffness that feels snappy but natural and
  // compute damping accordingly.
  mass: 1,
  stiffness: 200,
  damping: 2 * Math.sqrt(200), // ≈ 28.28
  // No bounce/overshoot for critically damped motion
  bounce: 0,
};

// Spring presets
export const springs = {
  // Main spring for general animations
  default: {
    type: 'spring',
    stiffness: 180,
    damping: 25,
    mass: 0.8,
  },
  // Bouncy spring for completion animations
  bouncy: {
    type: 'spring',
    bounce: 0.3,
    visualDuration: 0.5,
  },
  // Node width animation with custom bounce
  nodeWidth: {
    type: 'spring',
    stiffness: 180,
    damping: 25,
    mass: 0.8,
    visualDuration: 0.6,
    bounce: 0.3,
  },
  // Content scale animation for completion
  contentScale: {
    type: 'spring',
    bounce: 0.3,
    visualDuration: 0.5,
    stiffness: 260,
    damping: 18,
  },
  // Failure bubble animation
  failureBubble: {
    type: 'spring',
    visualDuration: 0.2,
    delay: 0.05,
    bounce: 0.3,
  },
};

// Shake animation constants
export const shake = {
  // Running state jitter
  running: {
    angleRange: 4,
    angleBase: 0.5,
    offsetRange: 1.5,
    offsetBase: 0.5,
    offsetYRange: 0.6,
    offsetYBase: 0.1,
    durationMin: 0.1,
    durationMax: 0.2,
  },
  // Failure/death shake - more intense
  failure: {
    intensity: 8,
    duration: 0.08,
    count: 6,
    rotationRange: 8,
    returnDuration: 0.3,
  },
  // Failure bubble shake - gentler
  bubble: {
    intensity: 4,
    duration: 0.08,
    count: 4,
    rotationRange: 4,
    yOffset: -5,
    returnDuration: 0.3,
    delay: 100,
  },
};

// Animation durations and timing
export const timing = {
  // Border pulsing
  borderPulse: {
    duration: 1.5,
    values: [1, 0.3, 1],
  },
  // Glow pulsing
  glowPulse: {
    duration: 0.5,
    values: [1, 5, 1],
  },
  // Flash animation
  flash: {
    duration: 1.0,
    ease: 'linear',
  },
  // Smooth exit animations
  exit: {
    duration: 0.3,
    ease: [0.4, 0, 0.6, 1],
  },
  // Glitch effect timing
  glitch: {
    initialCount: 3,
    initialDelayMin: 20,
    initialDelayMax: 70,
    pauseMin: 50,
    pauseMax: 150,
    subtleDelayMin: 300,
    subtleDelayMax: 800,
  },
};

// Color values for animations
export const colors = {
  // Flash colors
  flash: 'rgba(255, 255, 255, 0.8)',

  // Border colors
  border: {
    default: 'rgba(255, 255, 255, 0.1)',
    death: 'rgba(220, 38, 38, 0.4)',
  },

  // Failure bubble colors
  failureBubble: {
    background: 'rgba(239, 68, 68, 0.95)',
    text: 'text-red-50',
    shadow: '0 0px 16px rgba(0, 0, 0, 0.5)',
  },

  // Glow effects
  glow: {
    death: 'rgba(220, 38, 38, 0.8)',
    running: 'rgba(100, 200, 255, 0.2)',
  },
};

// Transform and filter values
export const effects = {
  // Death filter effects
  death: {
    contrast: 1.2,
    brightness: 0.8,
  },
  // Glitch intensity ranges
  glitch: {
    scaleRange: 0.2,
    glowMin: 3,
    glowMax: 7,
    intensePulseMax: 10,
  },
};
