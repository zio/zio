import { animate, motion, useMotionValue, useTransform } from 'motion/react';
import { useEffect } from 'react';
import { taskSounds } from '../sounds/taskSounds';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/feedback/NotificationBubble.tsx. Unreachable in round 1
// (nothing in this scenario calls the notify() helper), kept for exact
// fidelity with the source component tree; taskSounds is the silent stub.
export function NotificationBubble({ notification }) {
  const floatY = useMotionValue(0);

  // Play notification chime when notification appears
  useEffect(() => {
    taskSounds.playNotificationChime();
  }, []); // Only play once when component mounts

  // Gentle floating animation
  useEffect(() => {
    let cancelled = false;

    const floatSequence = async () => {
      if (cancelled) return;
      // Faster floating up and down with ease in out
      while (!cancelled) {
        await animate(floatY, -12, {
          duration: 0.8,
          ease: 'easeInOut',
        }).finished;

        if (cancelled) break;

        await animate(floatY, 0, {
          duration: 0.8,
          ease: 'easeInOut',
        }).finished;
      }
    };

    floatSequence();

    return () => {
      cancelled = true;
    };
  }, [floatY]);

  const transform = useTransform([floatY], ([y]) => `translateY(${y}px)`);

  return (
    <motion.div
      style={{
        position: 'absolute',
        bottom: '100%',
        left: '50%',
        x: '-50%',
        zIndex: 1000,
        pointerEvents: 'none',
      }}
      initial={{ opacity: 0, scale: 0, y: 50, filter: 'blur(10px)' }}
      animate={{ opacity: 1, scale: 1, y: 0, filter: 'blur(0px)' }}
      exit={{ opacity: 0, scale: 0, y: 50, filter: 'blur(10px)' }}
      transition={{
        type: 'spring',
        visualDuration: 0.3,
        bounce: 0.1,
      }}
    >
      <motion.div
        style={{
          transform,
        }}
      >
        {/* Arrow pointing down */}
        <div
          style={{
            position: 'absolute',
            bottom: -7,
            left: '50%',
            transform: 'translateX(-50%)',
            width: 0,
            height: 0,
            borderLeft: '8px solid transparent',
            borderRight: '8px solid transparent',
            borderTop: '8px solid #3b82f6', // Blue background
          }}
        />

        {/* Notification content */}
        <div
          className="flex items-center gap-2 p-2 px-3 text-xl font-medium text-white"
          style={{
            background: '#3b82f6', // Blue background
            borderRadius: '8px',
            maxWidth: '200px',
            boxShadow:
              '0 8px 32px rgba(0, 0, 0, 0.4), 0 4px 12px rgba(59, 130, 246, 0.3)',
          }}
        >
          {notification.icon && (
            <span className="text-lg">{notification.icon}</span>
          )}
          <span>{notification.message}</span>
        </div>
      </motion.div>
    </motion.div>
  );
}
