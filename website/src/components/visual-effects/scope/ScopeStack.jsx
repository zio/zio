// website/src/components/visual-effects/scope/ScopeStack.jsx
import { CaretRightIcon } from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'motion/react';
import { useLayoutEffect, useRef, useState } from 'react';
import { useVisualScope } from '../hooks/useVisualScope';
import { FinalizerCard } from './FinalizerCard';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/scope/ScopeStack.tsx.
export function ScopeStack({ scope }) {
  useVisualScope(scope);
  const containerRef = useRef(null);
  const [containerWidth, setContainerWidth] = useState(0);
  const cardWidth = 200; // Based on minWidth in FinalizerCard

  useLayoutEffect(() => {
    const updateWidth = () => {
      if (containerRef.current) {
        setContainerWidth(containerRef.current.offsetWidth);
      }
    };

    updateWidth();

    const resizeObserver = new ResizeObserver(updateWidth);
    if (containerRef.current) {
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, []);

  const pendingFinalizers = scope.finalizers.filter((f) => f.state === 'pending');
  const completedFinalizers = scope.finalizers.filter((f) => f.state === 'completed');

  // Don't render until we have container width
  if (containerWidth === 0) {
    return (
      <div
        ref={containerRef}
        className="relative flex h-[88px] items-center justify-between rounded-xl border border-dashed border-neutral-700 m-4"
      >
        <div className="absolute inset-0 flex items-center justify-center text-neutral-700">
          <div className="flex items-center gap-2">
            {/* Left chevrons */}
            <div className="flex gap-1">
              {[0, 1, 2].map((i) => (
                <motion.span
                  key={`left-${i}`}
                  className="text-neutral-600"
                  animate={{
                    opacity: [0.2, 1, 0.2],
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                    delay: i * 0.15,
                    ease: 'easeInOut',
                  }}
                >
                  ›
                </motion.span>
              ))}
            </div>
            <span className="tracking-widest">FINALIZERS!</span>

            {/* Right chevrons */}
            <div className="flex gap-1">
              {[3, 4, 5].map((i) => (
                <motion.span
                  key={`right-${i}`}
                  className="text-neutral-600"
                  animate={{
                    opacity: [0.2, 1, 0.2],
                  }}
                  transition={{
                    duration: 2,
                    repeat: Infinity,
                    delay: i * 0.15,
                    ease: 'easeInOut',
                  }}
                >
                  ›
                </motion.span>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative flex h-[88px] items-center justify-between">
      <div className="absolute inset-0 flex items-center justify-center text-neutral-700">
        <div className="flex items-center gap-2">
          {/* Left chevrons */}
          <div className="flex gap-1">
            {[0, 1, 2].map((i) => (
              <motion.span
                key={`left-${i}`}
                className="text-neutral-600"
                animate={{
                  opacity: [0.2, 1, 0.2],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  delay: i * 0.1,
                  ease: 'easeInOut',
                }}
              >
                <CaretRightIcon size={16} weight="fill" />
              </motion.span>
            ))}
          </div>

          <span className="tracking-wider">FINALIZERS</span>

          {/* Right chevrons */}
          <div className="flex gap-1">
            {[4, 5, 6].map((i) => (
              <motion.span
                key={`right-${i}`}
                className="text-neutral-600"
                animate={{
                  opacity: [0.2, 1, 0.2],
                }}
                transition={{
                  duration: 2,
                  repeat: Infinity,
                  delay: i * 0.15,
                  ease: 'easeInOut',
                }}
              >
                <CaretRightIcon size={16} weight="fill" />
              </motion.span>
            ))}
          </div>
        </div>
      </div>
      <AnimatePresence mode="popLayout">
        {scope.finalizers.map((finalizer) => {
          const isRunning = finalizer.state === 'running';
          const isPending = finalizer.state === 'pending';
          const isCompleted = finalizer.state === 'completed';

          // Find indices
          const pendingIndex = pendingFinalizers.indexOf(finalizer);
          const completedIndex = completedFinalizers.indexOf(finalizer);

          // Calculate x position from left edge
          let xPosition = 0;
          let zIndex = 10;

          if (isRunning) {
            // Center the running finalizer
            xPosition = (containerWidth - cardWidth) / 2;
            zIndex = 20;
          } else if (isPending) {
            // Stack pending on the left
            xPosition = pendingIndex * 35 + 16;
            zIndex = 10 + pendingIndex;
          } else if (isCompleted) {
            // Stack completed on the right (calculate from left)
            const rightOffset = (completedFinalizers.length - 1 - completedIndex) * 35 + 16;
            xPosition = containerWidth - rightOffset - cardWidth;
            zIndex = 10 - completedIndex;
          }

          const scale = isRunning ? 1.05 : 1;

          return (
            <motion.div
              key={finalizer.id}
              layoutId={finalizer.id}
              className="absolute"
              style={{
                zIndex,
                willChange: 'transform',
                translateZ: 0,
              }}
              animate={{
                x: xPosition,
                scale,
              }}
              transition={{
                type: 'spring',
                visualDuration: 0.5,
                bounce: 0.0,
              }}
            >
              <FinalizerCard finalizer={finalizer} />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
