import {
  SkullIcon,
  StarFourIcon,
  WarningOctagonIcon,
} from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'motion/react';
import { useLayoutEffect, useRef } from 'react';
import { springs } from '../animations';
import { isRenderableResult, renderResult } from '../renderers';
import { theme } from '../theme';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/effect/EffectContent.tsx.

function TaskIcon({ size, type }) {
  const iconSize = size * 0.5;
  const iconProps = {
    size: iconSize,
    color: 'rgba(255, 255, 255, 0.9)',
  };

  switch (type) {
    case 'failed':
      return <SkullIcon {...iconProps} weight="fill" />;
    case 'death':
      return <SkullIcon {...iconProps} weight="fill" color="#dc2626" />;
    case 'interrupted':
      return <WarningOctagonIcon {...iconProps} weight="fill" />;
    default:
      return <StarFourIcon {...iconProps} weight="fill" />;
  }
}

function TaskContentInner({ state }) {
  switch (state.type) {
    case 'failed':
    case 'interrupted':
    case 'death':
      return (
        <motion.div
          key={state.type}
          initial={{ scale: 0, filter: 'blur(10px)' }}
          animate={{ scale: 1, filter: 'blur(0px)' }}
          exit={{ scale: 0, filter: 'blur(10px)' }}
          transition={{
            type: 'spring',
            bounce: state.type === 'interrupted' ? 0.5 : 0.3,
            visualDuration: 0.3,
          }}
        >
          <TaskIcon type={state.type} size={64} />
        </motion.div>
      );

    case 'completed': {
      const { result } = state;
      const content = isRenderableResult(result)
        ? renderResult(result)
        : String(result);

      return (
        <motion.div
          key="result"
          initial={{ opacity: 0, scale: 0.5, filter: 'blur(10px)' }}
          animate={{ opacity: 1, scale: 1, filter: 'blur(0px)' }}
          exit={{ opacity: 0, scale: 0.5, filter: 'blur(10px)' }}
          transition={{
            ...springs.bouncy,
            stiffness: 260,
            damping: 18,
          }}
        >
          {content}
        </motion.div>
      );
    }

    case 'running':
      return null;

    default:
      return (
        <motion.div
          key="star"
          initial={{ scale: 0, filter: 'blur(10px)' }}
          animate={{ scale: 1, filter: 'blur(0px)' }}
          exit={{ scale: 0, filter: 'blur(10px)' }}
          transition={{ type: 'spring', bounce: 0.3, visualDuration: 0.3 }}
        >
          <TaskIcon type="default" size={64} />
        </motion.div>
      );
  }
}

export function EffectContent({ motionValues, state }) {
  const contentRef = useRef(null);

  // Auto-resize width based on content (synchronous, before paint)
  useLayoutEffect(() => {
    if (state.type === 'completed' && contentRef.current) {
      const actualWidth = contentRef.current.scrollWidth;

      if (actualWidth > 64 - 16) {
        motionValues.nodeWidth.set(actualWidth + 24);
      }
    }
  }, [state, motionValues.nodeWidth]);

  return (
    <motion.div
      style={{
        position: 'absolute',
        inset: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: 600,
        color: theme.colors.textPrimary,
        opacity: motionValues.contentOpacity,
        scale: motionValues.contentScale,
        padding: '0 8px',
      }}
    >
      <div ref={contentRef} style={{ whiteSpace: 'nowrap' }}>
        <AnimatePresence mode="popLayout">
          <TaskContentInner state={state} />
        </AnimatePresence>
      </div>
    </motion.div>
  );
}
