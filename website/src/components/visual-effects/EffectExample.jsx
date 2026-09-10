import { ArrowRightIcon } from '@phosphor-icons/react';
import { motion } from 'motion/react';
import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { CodeBlock } from './CodeBlock';
import EffectNode from './effect-node/EffectNode';
import { FloatingHighlight } from './feedback/FloatingHighlight';
import { HeaderView } from './HeaderView';

// Ported (TS types stripped) from the source engine's
// src/components/display/EffectExample.tsx. The source also supports a
// `scope`/`refs`/`showScheduleTimeline` prop for examples that visualize
// ZIO.scoped/Ref/Schedule — dropped here since effect-race.jsx (the only
// example mounted on this site) uses none of them; nothing in this file's
// actual rendering logic changed for the props it does use.
const EMPTY_REFS_ARRAY = [];

function EffectExampleComponent({
  code,
  configurationPanel,
  description,
  exampleId,
  isDarkMode = false,
  name,
  refs = EMPTY_REFS_ARRAY,
  resultEffect,
  effectHighlightMap,
  effects,
  variant,
}) {
  const [hoveredEffect, setHoveredEffect] = useState(null);
  const [delayedHoveredEffect, setDelayedHoveredEffect] = useState(null);
  const hoverTimeoutRef = useRef(null);
  const codeContainerRef = useRef(null);

  // Memoize hover handlers to prevent re-creation on every render
  const handleMouseEnter = useCallback((effectName) => {
    setHoveredEffect(effectName);
  }, []);

  const handleMouseLeave = useCallback(() => {
    setHoveredEffect(null);
  }, []);

  // Handle hover with delay
  useEffect(() => {
    // Clear any existing timeout
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
      hoverTimeoutRef.current = null;
    }

    if (hoveredEffect) {
      // Immediately show highlight when hovering
      setDelayedHoveredEffect(hoveredEffect);
    } else {
      // Delay hiding the highlight
      hoverTimeoutRef.current = setTimeout(() => {
        setDelayedHoveredEffect(null);
      }, 500); // 500ms delay before hiding
    }

    return () => {
      if (hoverTimeoutRef.current) {
        clearTimeout(hoverTimeoutRef.current);
      }
    };
  }, [hoveredEffect]);

  // Determine if this is a single effect example
  const isSingleEffect =
    !resultEffect || (effects.length === 1 && effects[0] === resultEffect);
  const headerEffect = resultEffect || effects[0];

  if (!headerEffect) {
    throw new Error('EffectExample requires at least one effect');
  }

  // Shared UI values
  const borderColorValue = isDarkMode
    ? 'rgba(127, 29, 29, 0.5)'
    : 'rgba(64, 64, 64, 0.5)';
  const backgroundGradient = isDarkMode
    ? 'linear-gradient(to bottom right, black, rgba(127, 29, 29, 0.2))'
    : 'linear-gradient(to bottom right, rgba(23, 23, 23, 0.8), rgba(23, 23, 23, 0.4))';
  const headerBackground = isDarkMode
    ? 'rgba(0, 0, 0, 0.5)'
    : 'rgba(38, 38, 38, 0.5)';
  const standardTransition = { duration: 0.2, ease: 'easeInOut' };

  const highlightTarget = delayedHoveredEffect
    ? effectHighlightMap[delayedHoveredEffect] || null
    : null;

  // If the result node should display elapsed ms from a timer-enabled effect, prefer its own timer,
  // otherwise fall back to the first input effect that has a timer.
  const labelEffectForResult = resultEffect?.showTimer
    ? resultEffect
    : effects.find((e) => e.showTimer);

  return (
    <motion.div
      className="relative flex w-full flex-col rounded-2xl border shadow-2xl"
      initial={{
        boxShadow: isDarkMode
          ? '0 0 40px rgba(220, 38, 38, 0.3)'
          : '0 0 0 0 rgba(59, 130, 250, 0)',
        borderColor: borderColorValue,
        background: backgroundGradient,
      }}
      animate={{
        boxShadow: isDarkMode
          ? '0 0 40px rgba(220, 38, 38, 0.3)'
          : '0 0 0 0 rgba(59, 130, 250, 0)',
        borderColor: borderColorValue,
        background: backgroundGradient,
      }}
      transition={{
        borderColor: standardTransition,
        background: standardTransition,
      }}
    >
      {/* Header with interactive controls */}
      <motion.div
        className="rounded-t-2xl border-b p-4"
        initial={{
          borderColor: borderColorValue,
          backgroundColor: headerBackground,
        }}
        animate={{
          borderColor: borderColorValue,
          backgroundColor: headerBackground,
        }}
        transition={standardTransition}
      >
        <HeaderView
          effect={headerEffect}
          name={name}
          {...(variant && { variant })}
          description={description}
          refs={refs}
          exampleId={exampleId}
        />
      </motion.div>

      {/* Configuration Panel */}
      {configurationPanel && (
        <motion.div
          initial={{
            borderColor: borderColorValue,
          }}
          animate={{
            borderColor: borderColorValue,
          }}
          transition={standardTransition}
          className="border-b"
        >
          {configurationPanel}
        </motion.div>
      )}

      {/* Main visualization */}
      <motion.div
        className="border-b px-4 py-5"
        initial={{
          borderColor: borderColorValue,
        }}
        animate={{
          borderColor: borderColorValue,
        }}
        transition={standardTransition}
      >
        {isSingleEffect ? (
          // Single effect - just show the effect
          <div className="flex justify-start">
            <div
              onMouseEnter={() => handleMouseEnter(headerEffect.name)}
              onMouseLeave={handleMouseLeave}
            >
              <EffectNode effect={headerEffect} />
            </div>
          </div>
        ) : (
          // Multiple effects with arrow and result
          <div className="flex flex-row items-center justify-start gap-6">
            {/* Input effects - wrap on mobile */}
            <div className="flex flex-wrap justify-center gap-6">
              {effects.map((effect) => (
                <div
                  key={effect.name}
                  onMouseEnter={() => handleMouseEnter(effect.name)}
                  onMouseLeave={handleMouseLeave}
                >
                  <EffectNode effect={effect} />
                </div>
              ))}
            </div>

            {/* Arrow - rotate on mobile */}
            <div className="relative top-[-13px] flex rotate-0 items-center text-neutral-500">
              <ArrowRightIcon size={24} weight="fill" />
            </div>

            {/* Result */}
            {resultEffect && (
              <div
                onMouseEnter={() => handleMouseEnter(resultEffect.name)}
                onMouseLeave={handleMouseLeave}
              >
                <EffectNode
                  effect={resultEffect}
                  {...(labelEffectForResult && {
                    labelEffect: labelEffectForResult,
                  })}
                />
              </div>
            )}
          </div>
        )}
      </motion.div>

      {/* Code block */}
      <div
        className="relative p-4 text-base"
        ref={codeContainerRef}
        style={{ position: 'relative' }}
      >
        <CodeBlock code={code} activeLines={[]} />
        <FloatingHighlight
          containerRef={codeContainerRef}
          target={highlightTarget}
        />
      </div>
    </motion.div>
  );
}

// Helper function to compare arrays by reference and length
function areArraysEqual(a, b) {
  if (a === b) return true;
  if (!a || !b) return a === b;
  if (a.length !== b.length) return false;
  return a.every((item, index) => item === b[index]);
}

// Memoized component with custom comparison function
export const EffectExample = memo(
  EffectExampleComponent,
  (prevProps, nextProps) => {
    return (
      prevProps.name === nextProps.name &&
      prevProps.variant === nextProps.variant &&
      prevProps.code === nextProps.code &&
      prevProps.index === nextProps.index &&
      prevProps.isDarkMode === nextProps.isDarkMode &&
      areArraysEqual(prevProps.effects, nextProps.effects) &&
      prevProps.resultEffect === nextProps.resultEffect &&
      areArraysEqual(prevProps.refs, nextProps.refs) &&
      prevProps.exampleId === nextProps.exampleId &&
      prevProps.effectHighlightMap === nextProps.effectHighlightMap
    );
  },
);
