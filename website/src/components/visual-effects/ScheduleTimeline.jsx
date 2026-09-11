import { motion } from 'motion/react';
import React, { useEffect, useRef, useState } from 'react';
import { useVisualEffectState } from './VisualEffect';

// Timeline styling configuration
const TIMELINE_CONFIG = {
  // Dimensions
  height: 50,
  lineThickness: 3,
  dotSize: 12,
  cursorWidth: 3,
  cursorHeight: 30,

  // Colors
  colors: {
    running: 'bg-blue-500',
    runningActive: 'bg-blue-400', // Brighter for active segments
    gap: 'bg-neutral-500',
    gapActive: 'bg-neutral-500', // Brighter for active segments
    cursor: 'bg-white',
    cursorInactive: 'bg-neutral-600', // Darker cursor when stopped
    backgroundLine: 'bg-neutral-800', // Very dark gray background line
    tickMark: 'bg-neutral-800', // Subtle tick marks
  },

  // Raw color values for smooth animations
  rawColors: {
    runningActive: 'var(--color-blue-400)',
    runningInactive: 'var(--color-blue-500)',
    gapActive: 'var(--color-neutral-400)',
    gapInactive: 'var(--color-neutral-600)',
    // backgroundLine/tickMark/cursorActive below were the source's
    // permanently-dark values ('very dark gray, subtle' / white), which
    // read fine against the source's always-dark host but invert into
    // the loudest elements on zio.dev's light theme (a black barcode-like
    // strip, an invisible white cursor). Same class of fix as round 1's
    // EffectExample.jsx card-chrome tokens (borderColorValue/
    // backgroundGradient/headerBackground): swap to theme-reactive
    // --ifm-* tokens so both light and dark mode stay legible.
    backgroundLine: 'var(--ifm-color-emphasis-300)',
    tickMark: 'var(--ifm-color-emphasis-200)',
    cursorActive: 'var(--ifm-font-color-base)',
    cursorInactive: 'var(--color-neutral-500)',
  },

  // Positioning
  dotOffset: 6, // Half of dot size for centering
  lineTopOffset: '50%', // Vertical center
  dotTopOffset: '39%', // Slightly above center for visual balance
  leftPadding: 80, // Padding from left edge to avoid fade zone
  startOffset: 50, // Initial offset to make first dot visible

  // Tick marks
  tickMarkWidth: 1, // Width of tick marks
  tickMarkSpacing: 50, // Spacing between tick marks in pixels

  // Animation
  animation: {
    segmentDuration: 0.1,
    cursorDuration: 0.05,
    dotSpring: { type: 'spring', visualDuration: 0.5, bounce: 0.4 },
    activeToInactive: {
      type: 'spring',
      visualDuration: 0.5,
      bounce: 0.4,
    },
  },
};

export function ScheduleTimeline({
  baseEffect: baseTask,
  className = '',
  pixelsPerSecond = 100,
  repeatEffect: repeatTask,
  scrollThreshold = 0.8,
}) {
  const containerRef = useRef(null);
  const baseState = useVisualEffectState(baseTask);
  const repeatState = useVisualEffectState(repeatTask);
  const [isActive, setIsActive] = useState(false);
  const [startTime, setStartTime] = useState(null);
  const [currentX, setCurrentX] = useState(0);
  const [scrollOffset, setScrollOffset] = useState(0);
  const [trailSegments, setTrailSegments] = useState([]);
  const [lastTaskState, setLastTaskState] = useState(null);
  const [isClearing, setIsClearing] = useState(false);
  const [, setElapsedTime] = useState(0);
  const [, setFinalElapsedTime] = useState(null);

  // Generate unique segment ID
  const generateSegmentId = () => `segment-${Date.now()}-${Math.random()}`;

  // Format time function (same as TaskNode)
  const formatTime = (ms) => {
    return `${ms}ms`;
  };

  // Handle base task state changes and timeline activation
  useEffect(() => {
    // Reset timeline when base task is reset to idle state
    if (baseState.type === 'idle' && (isActive || trailSegments.length > 0)) {
      setIsClearing(true);
      setTimeout(() => {
        setIsActive(false);
        setStartTime(null);
        setLastTaskState(null);
        setIsClearing(false);
        setTrailSegments([]);
        setCurrentX(TIMELINE_CONFIG.startOffset);
        setScrollOffset(0);
        setElapsedTime(0);
        setFinalElapsedTime(null);
      }, 300);
      return;
    }

    // Handle base task state changes for trail coloring
    if (isActive && lastTaskState !== baseState.type && startTime) {
      if (lastTaskState === null && baseState.type === 'running') {
        setLastTaskState('running');
        return;
      }
      const now = Date.now();
      const elapsed = now - startTime;
      const currentPosition =
        TIMELINE_CONFIG.startOffset + (elapsed / 1000) * pixelsPerSecond;

      setTrailSegments((prev) => {
        const updated = [...prev];
        if (updated.length > 0) {
          const lastIndex = updated.length - 1;
          const last = updated[lastIndex];
          if (last) {
            last.endX = currentPosition;
            last.complete = true;
            last.endTime = now;
          }
        }
        const segmentType = baseState.type === 'running' ? 'running' : 'gap';
        updated.push({
          id: generateSegmentId(),
          startX: currentPosition,
          endX: currentPosition,
          type: segmentType,
          complete: false,
          startTime: now,
        });
        return updated;
      });
      setLastTaskState(baseState.type);
    }
  }, [
    baseState.type,
    isActive,
    lastTaskState,
    startTime,
    pixelsPerSecond,
    trailSegments.length,
  ]);

  // Handle repeat task state changes
  useEffect(() => {
    // Start timeline when repeat task starts running
    if (repeatState.type === 'running' && !isActive) {
      setIsActive(true);
      setStartTime(Date.now());
      setCurrentX(TIMELINE_CONFIG.startOffset);
      setScrollOffset(0);
      setTrailSegments([]);
      setLastTaskState(null);
      return;
    }

    // Stop timeline animation when repeat task completes, fails, or is interrupted
    if (
      (repeatState.type === 'completed' ||
        repeatState.type === 'failed' ||
        repeatState.type === 'interrupted') &&
      isActive
    ) {
      setTrailSegments((prev) => {
        const updated = [...prev];
        if (updated.length > 0) {
          const lastIndex = updated.length - 1;
          const last = updated[lastIndex];
          if (last) {
            last.complete = true;
            last.endTime = Date.now();
          }
        }
        return updated;
      });
      if (startTime) {
        setFinalElapsedTime(Date.now() - startTime);
      }
      setIsActive(false);
      return;
    }

    // Reset timeline only when tasks are reset to idle state
    if (repeatState.type === 'idle' && (isActive || trailSegments.length > 0)) {
      setIsClearing(true);
      setTimeout(() => {
        setIsActive(false);
        setStartTime(null);
        setLastTaskState(null);
        setIsClearing(false);
        setTrailSegments([]);
        setCurrentX(TIMELINE_CONFIG.startOffset);
        setScrollOffset(0);
        setElapsedTime(0);
        setFinalElapsedTime(null);
      }, 300);
      return;
    }
  }, [repeatState.type, isActive, startTime, trailSegments.length]);

  // Animation loop - just moves cursor and extends current segment
  useEffect(() => {
    if (!isActive || !startTime) return;

    let animationFrame;

    const animate = () => {
      const now = Date.now();
      const elapsed = now - startTime;

      // Calculate new cursor position
      const newX =
        TIMELINE_CONFIG.startOffset + (elapsed / 1000) * pixelsPerSecond;

      // Update elapsed time
      setElapsedTime(elapsed);

      // Extend the current (last) segment to cursor position
      setTrailSegments((prev) => {
        const updated = [...prev];
        if (updated.length > 0) {
          const lastIndex = updated.length - 1;
          const last = updated[lastIndex];
          if (last) {
            last.endX = newX;
          }
        } else {
          // First segment if none exist - only start when base task is running
          if (baseState.type === 'running') {
            updated.push({
              id: generateSegmentId(),
              startX: TIMELINE_CONFIG.startOffset,
              endX: newX,
              type: 'running',
              complete: false,
              startTime,
            });
          }
        }
        return updated;
      });

      // Handle scrolling when cursor gets near the edge (simplified for now)
      const timelineWidth = containerRef.current?.offsetWidth || 500;
      const scrollThresholdX = timelineWidth * scrollThreshold;
      if (newX > scrollThresholdX) {
        setScrollOffset(newX - scrollThresholdX);
      }

      setCurrentX(newX);

      animationFrame = requestAnimationFrame(animate);
    };

    animationFrame = requestAnimationFrame(animate);

    return () => {
      cancelAnimationFrame(animationFrame);
    };
  }, [isActive, startTime, baseState.type, pixelsPerSecond, scrollThreshold]);

  return (
    <div className={`w-full ${className} relative`} ref={containerRef}>
      {/* Timeline container */}
      {/* Black gradient mask from left to right */}
      {/* <div
        className="absolute pointer-events-none z-20"
        style={{
          left: "-16px",
          right: "0",
          top: "0",
          bottom: "0",
          background:
            "linear-gradient(to right, rgba(0,0,0,1) 0%, transparent 20px)",
        }}
      /> */}

      <div
        className="relative w-full overflow-hidden"
        style={{
          height: `${TIMELINE_CONFIG.height}px`,
        }}
      >
        {/* Content with fade mask */}
        <div className="relative w-full h-full">
          {/* Background line */}
          <div
            className="absolute w-full"
            style={{
              height: `${TIMELINE_CONFIG.lineThickness}px`,
              top: TIMELINE_CONFIG.lineTopOffset,
              transform: 'translateY(-50%)',
              backgroundColor: TIMELINE_CONFIG.rawColors.backgroundLine,
            }}
          />

          {/* Tick marks */}
          {(() => {
            const containerWidth = containerRef.current?.offsetWidth || 1000;
            const totalWidth = containerWidth + scrollOffset + 500; // Add extra width for scrolling
            const tickCount = Math.ceil(
              totalWidth / TIMELINE_CONFIG.tickMarkSpacing,
            );
            const ticks = [];

            for (let i = 1; i <= tickCount; i++) {
              const x = i * TIMELINE_CONFIG.tickMarkSpacing - scrollOffset;
              // Only render ticks that are visible
              if (
                x >= -TIMELINE_CONFIG.tickMarkSpacing &&
                x <= containerWidth + TIMELINE_CONFIG.tickMarkSpacing
              ) {
                ticks.push(
                  <div
                    key={i}
                    className="absolute"
                    style={{
                      left: `${x}px`,
                      width: `${TIMELINE_CONFIG.tickMarkWidth}px`,
                      height: `${TIMELINE_CONFIG.height}px`,
                      top: '0',
                      backgroundColor: TIMELINE_CONFIG.rawColors.tickMark,
                    }}
                  />,
                );
              }
            }

            return ticks;
          })()}

          {/* Trail segments */}
          {trailSegments.map((segment) => {
            const width = segment.endX - segment.startX;
            const left = segment.startX - scrollOffset - 16;

            if (segment.type === 'running') {
              const isActive = !segment.complete;

              return (
                <div key={segment.id}>
                  {/* Blue line */}
                  <motion.div
                    className="absolute"
                    style={{
                      left: `${left}px`,
                      width: `${width}px`,
                      height: `${TIMELINE_CONFIG.lineThickness}px`,
                      top: TIMELINE_CONFIG.lineTopOffset,
                      transform: 'translateY(-50%)',
                    }}
                    initial={{ width: 0 }}
                    animate={{
                      width: `${width}px`,
                      backgroundColor: isActive
                        ? TIMELINE_CONFIG.rawColors.runningActive
                        : TIMELINE_CONFIG.rawColors.runningInactive,
                      opacity: isClearing ? 0 : 1,
                    }}
                    transition={{
                      width: {
                        duration: 0, // No delay for width animation
                      },
                      backgroundColor:
                        TIMELINE_CONFIG.animation.activeToInactive,
                      opacity: {
                        duration: 0.3,
                        ease: 'easeInOut',
                      },
                    }}
                  />
                  {/* Start dot */}
                  <motion.div
                    className="absolute rounded-full z-12"
                    style={{
                      left: `${left - TIMELINE_CONFIG.dotOffset}px`,
                      width: `${TIMELINE_CONFIG.dotSize}px`,
                      height: `${TIMELINE_CONFIG.dotSize}px`,
                      top: TIMELINE_CONFIG.dotTopOffset,
                      transform: 'translateY(-50%)',
                    }}
                    initial={{ scale: 0, opacity: 0 }}
                    animate={{
                      scale: 1,
                      opacity: isClearing ? 0 : 1,
                      backgroundColor: isActive
                        ? TIMELINE_CONFIG.rawColors.runningActive
                        : TIMELINE_CONFIG.rawColors.runningInactive,
                    }}
                    transition={{
                      scale: {
                        ...TIMELINE_CONFIG.animation.dotSpring,
                      },
                      opacity: isClearing
                        ? {
                            duration: 0.3,
                            ease: 'easeInOut',
                          }
                        : {
                            ...TIMELINE_CONFIG.animation.dotSpring,
                          },
                      backgroundColor:
                        TIMELINE_CONFIG.animation.activeToInactive,
                    }}
                  />
                  {/* End dot (only if segment is complete) */}
                  {segment.complete && (
                    <motion.div
                      className="absolute rounded-full z-12"
                      style={{
                        left: `${left + width - TIMELINE_CONFIG.dotOffset}px`,
                        width: `${TIMELINE_CONFIG.dotSize}px`,
                        height: `${TIMELINE_CONFIG.dotSize}px`,
                        top: TIMELINE_CONFIG.dotTopOffset,
                        transform: 'translateY(-50%)',
                      }}
                      initial={{
                        scale: 0,
                        opacity: 0,
                        backgroundColor:
                          TIMELINE_CONFIG.rawColors.runningActive, // Start with active color
                      }}
                      animate={{
                        scale: 1,
                        opacity: isClearing ? 0 : 1,
                        backgroundColor:
                          TIMELINE_CONFIG.rawColors.runningInactive, // Animate to inactive color
                      }}
                      transition={{
                        scale: {
                          ...TIMELINE_CONFIG.animation.dotSpring,
                        },
                        opacity: isClearing
                          ? {
                              duration: 0.3,
                              ease: 'easeInOut',
                            }
                          : {
                              ...TIMELINE_CONFIG.animation.dotSpring,
                            },
                        backgroundColor:
                          TIMELINE_CONFIG.animation.activeToInactive,
                        borderColor: TIMELINE_CONFIG.animation.activeToInactive,
                      }}
                    />
                  )}
                </div>
              );
            } else {
              const isActive = !segment.complete;
              // Calculate current duration - either completed or elapsed so far
              let segmentDuration = null;
              if (segment.startTime) {
                if (segment.endTime) {
                  // Completed segment
                  segmentDuration = segment.endTime - segment.startTime;
                } else if (isActive && startTime) {
                  // Active segment - show elapsed time
                  segmentDuration = Date.now() - segment.startTime;
                }
              }

              // Calculate clean positions without all the offset confusion
              const segmentStartX = segment.startX - scrollOffset;
              const segmentEndX = segment.endX - scrollOffset;
              const segmentWidth = segmentEndX - segmentStartX;

              return (
                <React.Fragment key={segment.id}>
                  {/* Gap line */}
                  <motion.div
                    className="absolute rounded-full"
                    style={{
                      left: `${segmentStartX - 16}px`, // Apply -16 offset only here
                      width: `${segmentWidth}px`,
                      height: `${TIMELINE_CONFIG.lineThickness}px`,
                      top: TIMELINE_CONFIG.lineTopOffset,
                      transform: 'translateY(-50%)',
                    }}
                    initial={{ width: 0 }}
                    animate={{
                      width: `${segmentWidth}px`,
                      backgroundColor: isActive
                        ? TIMELINE_CONFIG.rawColors.gapActive
                        : TIMELINE_CONFIG.rawColors.gapInactive,
                      opacity: isClearing ? 0 : 1,
                    }}
                    transition={{
                      width: {
                        duration: 0, // No delay for width animation
                      },
                      backgroundColor:
                        TIMELINE_CONFIG.animation.activeToInactive,
                      opacity: {
                        duration: 0.3,
                        ease: 'easeInOut',
                      },
                    }}
                  />

                  {/* Duration label for gap segments */}
                  {segmentDuration !== null && segmentWidth > 50 && (
                    <div
                      className="absolute pointer-events-none"
                      style={{
                        left: `${segmentStartX - 16 + segmentWidth / 2}px`,
                        top: `${TIMELINE_CONFIG.height / 2}px`,
                        transform: 'translate(-50%, -50%)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <motion.div
                        className="bg-neutral-900/90 px-2 py-0.5 rounded text-xs font-mono text-neutral-300 border border-neutral-700 whitespace-nowrap"
                        initial={{ opacity: 0, scale: 0.8 }}
                        animate={{
                          opacity: isClearing ? 0 : 1,
                          scale: 1,
                        }}
                        transition={{
                          duration: 0.3,
                          ease: 'easeOut',
                        }}
                      >
                        {formatTime(segmentDuration)}
                      </motion.div>
                    </div>
                  )}
                </React.Fragment>
              );
            }
          })}

          {/* Cursor - vertical line that changes color when stopped */}
          <motion.div
            className="absolute"
            style={{
              left: `${currentX - scrollOffset - 16}px`,
              width: `${TIMELINE_CONFIG.cursorWidth}px`,
              height: `${TIMELINE_CONFIG.height}px`,
              top: '0',
            }}
            animate={{
              left: `${currentX - scrollOffset - 16}px`,
              backgroundColor: isActive
                ? TIMELINE_CONFIG.rawColors.cursorActive
                : TIMELINE_CONFIG.rawColors.cursorInactive,
              opacity: isClearing ? 0 : 1,
            }}
            transition={{
              left: {
                duration: TIMELINE_CONFIG.animation.cursorDuration,
                ease: 'linear',
              },
              backgroundColor: {
                duration: 0.3,
                ease: 'easeInOut',
              },
              opacity: {
                duration: 0.8,
                ease: 'easeInOut',
              },
            }}
          />
        </div>
      </div>
    </div>
  );
}
