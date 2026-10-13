// website/src/components/visual-effects/scope/FinalizerCard.jsx
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { springs } from '../animations';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/scope/FinalizerCard.tsx.
export function FinalizerCard({ finalizer }) {
  const isRunning = finalizer.state === 'running';
  const isCompleted = finalizer.state === 'completed';

  // Track state transitions
  const prevStateRef = useRef(finalizer.state);
  const [justCompleted, setJustCompleted] = useState(false);

  useEffect(() => {
    if (prevStateRef.current !== 'completed' && finalizer.state === 'completed') {
      setJustCompleted(true);
      const timeout = setTimeout(() => setJustCompleted(false), 600); // Match animation duration
      return () => clearTimeout(timeout);
    }
    prevStateRef.current = finalizer.state;
  }, [finalizer.state]);

  return (
    <motion.div
      initial={{
        opacity: 0,
        scale: 1.2,
        filter: 'blur(4px)',
      }}
      animate={{
        opacity: 1,
        scale: 1,
        filter: 'blur(0px)',
      }}
      exit={{
        opacity: 0,
        scale: 0.8,
        filter: 'blur(4px)',
      }}
      transition={{
        type: 'spring',
        visualDuration: 0.3,
        bounce: 0.3,
      }}
      className={`relative flex h-[52px] items-center gap-3 rounded-lg px-4 py-3 shadow-lg shadow-neutral-900 transition-colors duration-200 ${
        finalizer.state === 'pending'
          ? 'bg-neutral-800 border border-neutral-700'
          : isRunning
            ? 'bg-blue-900 border border-blue-500 '
            : 'bg-green-900 border border-green-500'
      }`}
      style={{
        minWidth: '200px',
        willChange: 'transform, opacity, filter',
        translateZ: 0,
      }}
    >
      {/* Checkbox container */}
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={springs.default}
        className={`relative flex h-6 w-6 items-center justify-center rounded border transition-colors duration-200 ${
          isCompleted
            ? 'bg-green-500 border-green-500'
            : isRunning
              ? 'bg-blue-800 border-blue-500'
              : 'bg-neutral-700 border-neutral-500'
        }`}
      >
        <AnimatePresence mode="popLayout">
          {isCompleted && (
            <motion.div
              key="check"
              initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
              animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
              exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              className="absolute inset-0 flex items-center justify-center"
            >
              <svg width="14" height="10" viewBox="0 0 14 10" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M1.5 5L5 8.5L12.5 1"
                  stroke="white"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Running animation */}
        {isRunning && (
          <motion.div
            className="absolute inset-0 rounded"
            animate={{
              scale: [1, 1.3, 1],
              opacity: [0.3, 0, 0.3],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
            style={{
              background: 'radial-gradient(circle, rgba(59, 130, 246, 0.4) 0%, transparent 70%)',
            }}
          />
        )}
      </motion.div>
      {/* Running pulse effect */}
      {isRunning && (
        <motion.div
          className="absolute inset-0 rounded-lg"
          initial={{ opacity: 0 }}
          animate={{
            opacity: [0, 0.3, 0],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
          style={{
            background: 'radial-gradient(ellipse at center, rgba(59, 130, 246, 0.2) 0%, transparent 70%)',
          }}
        />
      )}

      {/* Completion flash */}
      {justCompleted && (
        <motion.div
          className="absolute inset-0 rounded-lg"
          initial={{
            opacity: 0.8,
            scale: 1,
          }}
          animate={{
            opacity: 0,
            scale: 1.05,
          }}
          transition={{
            duration: 0.6,
            ease: 'easeOut',
          }}
          style={{
            background: 'radial-gradient(ellipse at center, rgba(34, 197, 94, 0.4) 0%, transparent 70%)',
          }}
        />
      )}

      {/* Content */}
      <div className="relative z-10">
        <motion.span
          className={`font-mono text-base font-medium ${
            finalizer.state === 'pending'
              ? 'text-white'
              : isRunning
                ? 'text-blue-300'
                : 'text-green-300/90'
          }`}
          animate={{
            opacity: 1,
          }}
          transition={{ duration: 0.2 }}
        >
          {finalizer.name}
        </motion.span>
      </div>
    </motion.div>
  );
}
