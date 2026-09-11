import {
  ArrowCounterClockwiseIcon,
  CheckIcon,
  LinkIcon,
  PlayIcon,
  StarFourIcon,
  StopIcon,
} from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'motion/react';
import { memo, useCallback, useEffect, useState } from 'react';
import { GLOW_COLORS, TASK_COLORS } from './colors';
import { useOptionKey } from './hooks/useOptionKey';
import { taskSounds } from './sounds/taskSounds';
import { useVisualEffectSubscription } from './VisualEffect';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/HeaderView.tsx.
function HeaderViewComponent({
  description,
  exampleId,
  name,
  refs = [],
  effect: task,
  variant,
}) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPressed, setIsPressed] = useState(false);
  const [showCheckmark, setShowCheckmark] = useState(false);
  const [hasPlayedHoverSound, setHasPlayedHoverSound] = useState(false);
  const isOptionPressed = useOptionKey();
  useVisualEffectSubscription(task);
  const { state } = task;

  const isRunning = state.type === 'running';
  const isCompleted = state.type === 'completed';
  const isFailed = state.type === 'failed';
  const isInterrupted = state.type === 'interrupted';
  const isDeath = state.type === 'death';
  const canReset = isCompleted || isFailed || isInterrupted || isDeath;

  const runWithDependencies = useCallback(async () => {
    await task.run();
  }, [task]);

  const resetWithDependencies = useCallback(() => {
    task.reset();
    // Also reset refs passed in
    refs.forEach((refItem) => {
      refItem.reset();
    });
    // Play reset sound
    taskSounds.playReset();
  }, [task, refs]);

  const handleAction = useCallback(() => {
    // If Option is pressed and we have an exampleId, copy link
    if (isOptionPressed && exampleId) {
      const url = `${window.location.origin}/${exampleId}`;
      navigator.clipboard.writeText(url).then(() => {
        setShowCheckmark(true);
        // Play copy success sound
        taskSounds.playLinkCopied();
        // Hide checkmark after 1.5 seconds
        setTimeout(() => {
          setShowCheckmark(false);
        }, 1500);
      });
      return;
    }

    const currentState = task.state;
    const running = currentState.type === 'running';
    const resettable =
      currentState.type === 'completed' ||
      currentState.type === 'failed' ||
      currentState.type === 'interrupted' ||
      currentState.type === 'death';

    if (running) {
      task.interrupt();
    } else if (resettable) {
      resetWithDependencies();
    } else {
      runWithDependencies();
    }
  }, [
    task,
    resetWithDependencies,
    runWithDependencies,
    isOptionPressed,
    exampleId,
  ]);

  // Icon-centering fix (not in the source, which never embeds this
  // component inside a foreign host page): a bare inline <svg> keeps its
  // default `vertical-align: baseline`, which leaves a few px of descender
  // gap below the glyph and pushes it visually upward inside its flex
  // parent. Every icon wrapper below is itself `flex` so the icon centers
  // on both axes regardless of that baseline quirk.
  const iconWrapperClassName = 'flex items-center justify-center';

  const getIcon = () => {
    // Show checkmark after copying
    if (showCheckmark) {
      return (
        <motion.div
          key="check"
          className={iconWrapperClassName}
          initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
          animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
          exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
          transition={{ type: 'spring', stiffness: 300, damping: 20 }}
        >
          <CheckIcon size={24} weight="bold" />
        </motion.div>
      );
    }

    // Show link icon when Option is pressed AND hovering
    if (isOptionPressed && isHovered && exampleId) {
      return (
        <motion.div
          key="link"
          className={iconWrapperClassName}
          initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
          animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
          exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
          transition={{ type: 'spring', stiffness: 300, damping: 20 }}
        >
          <LinkIcon size={24} weight="bold" />
        </motion.div>
      );
    }

    if (isHovered) {
      if (isRunning) {
        return (
          <motion.div
            key="stop"
            className={iconWrapperClassName}
            initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
            animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
            exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
            transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          >
            <StopIcon size={24} weight="fill" />
          </motion.div>
        );
      } else if (canReset) {
        return (
          <motion.div
            key="reset"
            className={iconWrapperClassName}
            initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
            animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
            exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
            transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          >
            <ArrowCounterClockwiseIcon size={24} weight="bold" />
          </motion.div>
        );
      } else {
        return (
          <motion.div
            key="play"
            className={iconWrapperClassName}
            initial={{ scale: 0, rotate: -180, filter: 'blur(10px)' }}
            animate={{ scale: 1, rotate: 0, filter: 'blur(0px)' }}
            exit={{ scale: 0, rotate: 180, filter: 'blur(10px)' }}
            transition={{ type: 'spring', stiffness: 300, damping: 20 }}
          >
            {/* Play triangle glyph is visually left-heavy; nudge right to
                sit optically centered rather than geometrically centered. */}
            <PlayIcon size={24} weight="fill" style={{ marginLeft: 2 }} />
          </motion.div>
        );
      }
    }

    return (
      <motion.div
        key="star"
        className={iconWrapperClassName}
        initial={{ scale: 0, filter: 'blur(10px)' }}
        animate={
          isRunning
            ? { rotate: 360, scale: 1, filter: 'blur(0px)' }
            : { rotate: 0, scale: 1, filter: 'blur(0px)' }
        }
        exit={{ scale: 0, filter: 'blur(10px)' }}
        transition={
          isRunning
            ? {
                rotate: {
                  duration: 1,
                  repeat: Infinity,
                  ease: 'circInOut',
                },
                scale: { type: 'spring', stiffness: 300, damping: 20 },
                filter: { type: 'spring', stiffness: 300, damping: 20 },
              }
            : {
                type: 'spring',
                stiffness: 300,
                damping: 20,
              }
        }
      >
        <StarFourIcon size={24} weight="fill" />
      </motion.div>
    );
  };

  // Play hover sound effect when Option is pressed and hovering
  useEffect(() => {
    if (isOptionPressed && isHovered && exampleId && !hasPlayedHoverSound) {
      taskSounds.playLinkHover();
      setHasPlayedHoverSound(true);
    } else if (!isOptionPressed || !isHovered) {
      setHasPlayedHoverSound(false);
    }
  }, [isOptionPressed, isHovered, exampleId, hasPlayedHoverSound]);

  return (
    <motion.div
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onMouseDown={() => setIsPressed(true)}
      onMouseUp={() => setIsPressed(false)}
      onClick={handleAction}
      initial={{
        borderColor: 'rgba(255, 255, 255, 1)',
      }}
      animate={{
        borderColor: 'rgba(255, 255, 255, 0)',
      }}
      className="-m-3 -mx-4 flex cursor-pointer items-center gap-4 rounded-lg p-3 px-4"
    >
      <motion.div
        animate={{
          scale: isPressed ? 0.95 : isHovered ? 1.05 : 1,
          background: showCheckmark
            ? '#4f46e5' // indigo-600 for success state
            : isOptionPressed && isHovered && exampleId
              ? '#6366f1' // indigo-500 for link copy mode
              : isRunning
                ? TASK_COLORS.running
                : isInterrupted
                  ? TASK_COLORS.interrupted
                  : isCompleted
                    ? TASK_COLORS.success
                    : isFailed
                      ? TASK_COLORS.error
                      : isDeath
                        ? TASK_COLORS.death
                        : TASK_COLORS.idle,
        }}
        transition={{
          scale: { type: 'spring', stiffness: 300, damping: 20 },
          background: { duration: 0.2, ease: 'easeInOut' },
        }}
        className="relative flex h-10 w-10 items-center justify-center overflow-hidden rounded-md text-white"
      >
        <AnimatePresence mode="popLayout">{getIcon()}</AnimatePresence>

        {isRunning && (
          <motion.div
            className="absolute -inset-0.5 -z-10"
            style={{
              background: `radial-gradient(circle, ${GLOW_COLORS.running} 0%, transparent 70%)`,
            }}
            animate={{
              scale: [1, 1.3, 1],
              opacity: [0.5, 0, 0.5],
            }}
            transition={{
              duration: 2,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          />
        )}

        {/* Glow effect for link copy mode */}
        {isOptionPressed && isHovered && exampleId && !showCheckmark && (
          <motion.div
            className="absolute -inset-0.5 -z-10"
            style={{
              background:
                'radial-gradient(circle, #6366f1 0%, transparent 70%)',
            }}
            animate={{
              scale: [1, 1.2, 1],
              opacity: [0.3, 0.1, 0.3],
            }}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          />
        )}

        {/* Glow effect for success checkmark */}
        {showCheckmark && (
          <motion.div
            className="absolute -inset-0.5 -z-10"
            style={{
              background:
                'radial-gradient(circle, #4f46e5 0%, transparent 70%)',
            }}
            animate={{
              scale: [1, 1.4, 1],
              opacity: [0.6, 0, 0.6],
            }}
            transition={{
              duration: 1,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          />
        )}
      </motion.div>

      <div className="flex flex-1 flex-col">
        {/* text-white/text-neutral-* below were sized for the source's
            permanently-dark host page; use theme-aware tokens instead so
            the name/description stay legible once the card itself follows
            zio.dev's light/dark toggle (see EffectExample.jsx). */}
        <h2 className="flex items-baseline gap-2 text-xl font-semibold text-[var(--ifm-font-color-base)]">
          <span>{name}</span>
          {variant && (
            <span className="font-medium text-[var(--ifm-color-emphasis-600)]">
              {variant}
            </span>
          )}
        </h2>
        {description && (
          <p className="text-sm text-[var(--ifm-color-emphasis-700)]">
            {description}
          </p>
        )}
      </div>
    </motion.div>
  );
}

export const HeaderView = memo(HeaderViewComponent);
