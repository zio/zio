import {
  animate,
  useMotionValue,
  useSpring,
  useTransform,
  useVelocity,
} from 'motion/react';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { colors, effects, shake, springs, timing } from '../animations';
import { dimensions } from '../dimensions';
import { useStateTransition } from '../hooks/useStateTransition';
import { theme } from '../theme';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/useEffectMotion.ts.

/** tiny util: stable init once without eslint disables */
function useConst(init) {
  const ref = useRef(null);
  if (ref.current === null) ref.current = init();
  return ref.current;
}

/** minimal, SSR-safe reduced-motion hook */
function usePrefersReducedMotion() {
  const [prefers, setPrefers] = useState(false);
  useEffect(() => {
    if (typeof window === 'undefined' || !('matchMedia' in window)) return;
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    const set = () => setPrefers(mql.matches);
    set();
    // legacy Safari support
    const onChange = (e) => setPrefers(e.matches);
    mql.addEventListener
      ? mql.addEventListener('change', onChange)
      : mql.addListener(onChange);
    return () => {
      mql.removeEventListener
        ? mql.removeEventListener('change', onChange)
        : mql.removeListener(onChange);
    };
  }, []);
  return prefers;
}

// ------------------------
// useEffectMotion
// ------------------------
export function useEffectMotion() {
  const nodeWidth = useSpring(dimensions.node.width, springs.nodeWidth);
  const contentOpacity = useSpring(1, springs.default);
  const flashOpacity = useMotionValue(0);
  const flashColor = useMotionValue(colors.flash);
  const borderRadius = useSpring(theme.radius.md, springs.default);
  const nodeHeight = useMotionValue(64);
  const rotation = useMotionValue(0);
  const shakeX = useMotionValue(0);
  const shakeY = useMotionValue(0);
  const contentScale = useSpring(1, springs.default);
  const borderColor = useMotionValue(colors.border.default);
  const borderOpacity = useSpring(1, springs.default);
  const glowIntensity = useSpring(0, springs.default);

  const rotationVelocity = useVelocity(rotation);

  // Optional smoothing of velocity -> blur to avoid flicker
  const blurAmount = useTransform(rotationVelocity, [-100, 0, 100], [1, 0, 1], {
    clamp: true,
  });

  // Stable container without eslint disables
  const motionValues = useConst(() => ({
    nodeWidth,
    nodeHeight,
    contentOpacity,
    flashOpacity,
    flashColor,
    borderRadius,
    rotation,
    shakeX,
    shakeY,
    contentScale,
    blurAmount,
    borderColor,
    borderOpacity,
    glowIntensity,
  }));

  return motionValues;
}

// ------------------------
// useRunningAnimation
// ------------------------
export function useRunningAnimation(isRunning, motionValues) {
  const prefersReducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    let cancelled = false;
    let rafId = null; // single RAF id (fix leak)
    const animControls = { border: undefined, glow: undefined };

    const stopAll = () => {
      if (animControls.border) animControls.border.stop();
      if (animControls.glow) animControls.glow.stop();
      if (rafId !== null) cancelAnimationFrame(rafId);
      // reset quickly
      animate(motionValues.rotation, 0, {
        duration: timing.exit.duration,
        ease: timing.exit.ease,
      });
      animate(motionValues.shakeX, 0, {
        duration: timing.exit.duration,
        ease: timing.exit.ease,
      });
      animate(motionValues.shakeY, 0, {
        duration: timing.exit.duration,
        ease: timing.exit.ease,
      });
      motionValues.borderOpacity.set(1);
      motionValues.glowIntensity.set(0);
    };

    if (!isRunning || prefersReducedMotion) {
      stopAll();
      return;
    }

    // border pulse
    animControls.border = animate(
      motionValues.borderOpacity,
      [...timing.borderPulse.values],
      {
        duration: timing.borderPulse.duration,
        ease: 'easeInOut',
        repeat: Infinity,
      },
    );

    // glow pulse
    animControls.glow = animate(
      motionValues.glowIntensity,
      [...timing.glowPulse.values],
      {
        duration: timing.glowPulse.duration,
        ease: 'easeInOut',
        repeat: Infinity,
      },
    );

    const jitter = () => {
      if (cancelled) return;

      const angle =
        (Math.random() * shake.running.angleRange + shake.running.angleBase) *
        (Math.random() < 0.5 ? 1 : -1);

      const offset =
        (Math.random() * shake.running.offsetRange + shake.running.offsetBase) *
        (Math.random() < 0.5 ? -1 : 1);

      const offsetY =
        (Math.random() * shake.running.offsetYRange +
          shake.running.offsetYBase) *
        (Math.random() < 0.5 ? -1 : 1);

      const min = shake.running.durationMin;
      const max = shake.running.durationMax ?? min * 2;
      const duration = min + Math.random() * Math.max(0.001, max - min);

      const rot = animate(motionValues.rotation, angle, {
        duration,
        ease: 'circInOut',
      });
      const x = animate(motionValues.shakeX, offset, {
        duration,
        ease: 'easeInOut',
      });
      const y = animate(motionValues.shakeY, offsetY, {
        duration,
        ease: 'easeInOut',
      });

      // When this triple finishes, schedule next cycle on next frame
      Promise.all([rot.finished, x.finished, y.finished]).then(() => {
        if (cancelled) return;
        rafId = requestAnimationFrame(jitter);
      });
    };

    rafId = requestAnimationFrame(jitter);

    return () => {
      cancelled = true;
      stopAll();
    };
  }, [
    isRunning,
    motionValues.rotation,
    motionValues.shakeX,
    motionValues.shakeY,
    motionValues.borderOpacity,
    motionValues.glowIntensity,
  ]);
}

// ------------------------
// useStateAnimations
// ------------------------
export function useStateAnimations(state, motionValues) {
  const isRunning = state.type === 'running';

  // Radius
  useEffect(() => {
    motionValues.borderRadius.set(isRunning ? 15 : theme.radius.md);
  }, [isRunning, motionValues.borderRadius]);

  // Height
  useEffect(() => {
    animate(motionValues.nodeHeight, isRunning ? 64 * 0.4 : 64, {
      duration: 0.4,
      bounce: isRunning ? 0.3 : 0.5,
      type: 'spring',
    });
  }, [isRunning, motionValues.nodeHeight]);

  // Width & content opacity
  useEffect(() => {
    const hasResult = state.type === 'completed';

    if (!hasResult) {
      motionValues.nodeWidth.set(64); // if you want this animated, swap to animate(...)
    }

    motionValues.contentOpacity.set(
      hasResult ? 1 : state.type === 'running' ? 0 : 1,
    );
  }, [state, motionValues, isRunning]);
}

// ------------------------
// useEffectAnimations
// ------------------------
export function useEffectAnimations(
  state,
  motionValues,
  isHovering,
  setShowErrorBubble,
) {
  const prefersReducedMotion = usePrefersReducedMotion();
  const isFailish = state.type === 'failed' || state.type === 'death';

  // Error bubble visibility (comment aligned with code: 1.5s)
  useEffect(() => {
    let timer;

    if (isFailish) {
      setShowErrorBubble(true);
      timer = setTimeout(() => {
        if (!isHovering) setShowErrorBubble(false);
      }, 1500);
    } else {
      setShowErrorBubble(false);
    }

    return () => {
      if (timer) clearTimeout(timer);
    };
  }, [isFailish, isHovering, setShowErrorBubble]);

  // Flash on start/complete
  const transition = useStateTransition(state);
  useEffect(() => {
    if (transition.justCompleted || transition.justStarted) {
      const up = animate(motionValues.flashOpacity, 0.6, {
        duration: 0.02,
        ease: 'circOut',
      });
      up.finished.then(() => {
        if (prefersReducedMotion) {
          motionValues.flashOpacity.set(0);
        } else {
          animate(motionValues.flashOpacity, 0, {
            duration: timing.flash.duration,
            ease: timing.flash.ease,
          });
        }
      });
    }
  }, [
    transition.justCompleted,
    transition.justStarted,
    motionValues.flashOpacity,
    prefersReducedMotion,
  ]);

  // Failure/Death shake
  useEffect(() => {
    if (
      !(state.type === 'failed' || state.type === 'death') ||
      prefersReducedMotion
    )
      return;

    let cancelled = false;

    const shakeSequence = async () => {
      const { intensity, duration, count, rotationRange, returnDuration } =
        shake.failure;

      for (let i = 0; i < count && !cancelled; i++) {
        const xOffset = (Math.random() - 0.5) * intensity;
        const yOffset = (Math.random() - 0.5) * intensity;
        const rotOffset = (Math.random() - 0.5) * rotationRange;

        const anims = [
          animate(motionValues.shakeX, xOffset, {
            duration,
            ease: 'easeInOut',
          }),
          animate(motionValues.shakeY, yOffset, {
            duration,
            ease: 'easeInOut',
          }),
          animate(motionValues.rotation, rotOffset, {
            duration,
            ease: 'easeInOut',
          }),
        ];
        await Promise.all(anims.map((a) => a.finished));
      }

      if (!cancelled) {
        await Promise.all([
          animate(motionValues.shakeX, 0, {
            duration: returnDuration,
            ease: 'easeOut',
          }).finished,
          animate(motionValues.shakeY, 0, {
            duration: returnDuration,
            ease: 'easeOut',
          }).finished,
          animate(motionValues.rotation, 0, {
            duration: returnDuration,
            ease: 'easeOut',
          }).finished,
        ]);
      }
    };

    shakeSequence();
    return () => {
      cancelled = true;
    };
  }, [
    state,
    prefersReducedMotion,
    motionValues.shakeX,
    motionValues.shakeY,
    motionValues.rotation,
  ]);

  // Death glitch
  useEffect(() => {
    if (state.type !== 'death' || prefersReducedMotion) {
      motionValues.glowIntensity.set(0);
      return;
    }

    let cancelled = false;
    let timeoutId = null;

    const scheduleIdle = (cb, delay) => {
      timeoutId = setTimeout(() => {
        const win = window;
        if (typeof win.requestIdleCallback === 'function') {
          win.requestIdleCallback(cb);
        } else {
          cb();
        }
      }, delay);
    };

    const glitchSequence = async () => {
      const t = timing.glitch;
      const e = effects.glitch;

      // initial pulses
      for (let i = 0; i < t.initialCount && !cancelled; i++) {
        motionValues.contentScale.set(1 + Math.random() * e.scaleRange);
        motionValues.glowIntensity.set(Math.random() * e.intensePulseMax);

        await new Promise((resolve) => {
          scheduleIdle(
            resolve,
            t.initialDelayMin +
              Math.random() *
                Math.max(0, t.initialDelayMax - t.initialDelayMin),
          );
        });

        if (cancelled) break;
        motionValues.contentScale.set(1);
        motionValues.glowIntensity.set(e.glowMax);

        await new Promise((resolve) => {
          scheduleIdle(
            resolve,
            t.pauseMin + Math.random() * Math.max(0, t.pauseMax - t.pauseMin),
          );
        });
      }

      // subtle loop (only one timeout pending at any time)
      const subtle = () => {
        if (cancelled) return;
        motionValues.glowIntensity.set(
          e.glowMin + Math.random() * (e.glowMax - e.glowMin),
        );
        scheduleIdle(
          subtle,
          t.subtleDelayMin +
            Math.random() * Math.max(0, t.subtleDelayMax - t.subtleDelayMin),
        );
      };
      subtle();
    };

    glitchSequence();

    return () => {
      cancelled = true;
      if (timeoutId) clearTimeout(timeoutId);
      motionValues.glowIntensity.set(0);
    };
  }, [
    state,
    prefersReducedMotion,
    motionValues.contentScale,
    motionValues.glowIntensity,
  ]);

  // Content scale pop on completion
  useLayoutEffect(() => {
    if (transition.justCompleted) {
      motionValues.contentScale.set(0);
      animate(motionValues.contentScale, [1.3, 1], springs.contentScale);
    }
  }, [transition.justCompleted, motionValues.contentScale]);
}
