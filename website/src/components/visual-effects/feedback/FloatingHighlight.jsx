import {
  animate,
  motion,
  useMotionValue,
  useSpring,
  useTransform,
} from 'motion/react';
import { useEffect, useRef } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/components/feedback/FloatingHighlight.tsx.
export function FloatingHighlight({ containerRef, target }) {
  // Snappy spring animations for position and size
  const springConfig = { bounce: 0.0, visualDuration: 0.2 };
  const x = useSpring(0, springConfig);
  const y = useSpring(0, springConfig);
  const width = useSpring(0, springConfig);
  const height = useSpring(0, springConfig);
  const opacity = useMotionValue(0);
  const scale = useSpring(1, { stiffness: 300, damping: 20 });
  const highlightRef = useRef(null);

  // Map opacity to blur: 0 opacity = 4px blur, 1 opacity = 0px blur
  const blur = useTransform(opacity, [0, 1], [4, 0]);

  useEffect(() => {
    if (!containerRef.current || !target) {
      animate(opacity, 0, { ease: 'linear' });
      scale.set(0.9);
      return;
    }

    // Use requestAnimationFrame to ensure DOM is ready
    const rafId = requestAnimationFrame(() => {
      if (!containerRef.current) return;

      // Get the pre element inside the container
      const preElement = containerRef.current.querySelector('pre');
      if (!preElement) {
        animate(opacity, 0, { ease: 'linear' });
        scale.set(0.95);
        return;
      }

      // Get all text content and build a map of character positions
      const textNodes = [];
      let totalLength = 0;

      // Walk through all text nodes in the pre element
      const walker = document.createTreeWalker(
        preElement,
        NodeFilter.SHOW_TEXT,
        null,
      );

      let node;
      while ((node = walker.nextNode())) {
        const text = node.textContent || '';
        textNodes.push({ node, start: totalLength, text });
        totalLength += text.length;
      }

      // Get the full text content
      const fullText = textNodes.map((n) => n.text).join('');

      // Find the target text in the full content
      const targetIndex = fullText.indexOf(target.text);

      if (targetIndex === -1) {
        animate(opacity, 0, { ease: 'linear' });
        scale.set(0.95);
        return;
      }

      // Find which nodes contain the start and end of the target
      const targetEnd = targetIndex + target.text.length;
      let startNode = null;
      let startOffset = 0;
      let endNode = null;
      let endOffset = 0;

      for (const { node, start, text } of textNodes) {
        const nodeEnd = start + text.length;

        // Check if target starts in this node
        if (!startNode && targetIndex >= start && targetIndex < nodeEnd) {
          startNode = node;
          startOffset = targetIndex - start;
        }

        // Check if target ends in this node
        if (!endNode && targetEnd > start && targetEnd <= nodeEnd) {
          endNode = node;
          endOffset = targetEnd - start;
        }

        // If we found both, we can stop
        if (startNode && endNode) break;
      }

      if (startNode && endNode) {
        try {
          const range = document.createRange();
          range.setStart(startNode, startOffset);
          range.setEnd(endNode, endOffset);

          // Get all client rects (in case text spans multiple lines)
          const rects = Array.from(range.getClientRects());

          if (rects.length > 0) {
            const firstRect = rects[0];
            if (!firstRect) return;

            // Calculate bounding box
            const bounds = rects.reduce(
              (acc, rect) => ({
                left: Math.min(acc.left, rect.left),
                top: Math.min(acc.top, rect.top),
                right: Math.max(acc.right, rect.right),
                bottom: Math.max(acc.bottom, rect.bottom),
              }),
              {
                left: firstRect.left,
                top: firstRect.top,
                right: firstRect.right,
                bottom: firstRect.bottom,
              },
            );

            // Get container position
            const containerRect = containerRef.current.getBoundingClientRect();

            // Calculate relative position
            const relX = bounds.left - containerRect.left;
            const relY = bounds.top - containerRect.top;
            const relWidth = bounds.right - bounds.left;
            const relHeight = bounds.bottom - bounds.top;

            // Apply with padding
            const paddingX = 8;
            const paddingY = 6;
            x.set(relX - paddingX);
            y.set(relY - paddingY);
            width.set(relWidth + paddingX * 2);
            height.set(relHeight + paddingY * 2);
            animate(opacity, 1, { ease: 'linear' });
            scale.set(1);
          }
        } catch (e) {
          console.error('Error creating range:', e);
          animate(opacity, 0, { ease: 'linear' });
          scale.set(0.95);
        }
      } else {
        animate(opacity, 0, { ease: 'linear' });
        scale.set(0.95);
      }
    });

    return () => cancelAnimationFrame(rafId);
  }, [target, containerRef, x, y, width, height, opacity, scale]);

  return (
    <motion.div
      ref={highlightRef}
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        x,
        y,
        width,
        height,
        opacity,
        scale,
        borderRadius: 6,
        background: 'rgba(56, 189, 248, 0.15)',
        border: '1px solid rgba(56, 189, 248, 0.6)',
        boxShadow: '0 0 10px rgba(56, 189, 248, 0.3)',
        pointerEvents: 'none',
        zIndex: 10,
        filter: useTransform(blur, (v) => `blur(${Math.max(0, v)}px)`),
      }}
    />
  );
}
