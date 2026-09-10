import { AnimatePresence, motion } from 'motion/react';
import { Highlight, themes } from 'prism-react-renderer';
import { useEffect, useMemo, useRef } from 'react';

const { oneDark } = themes;

// Ported verbatim (TS types stripped) from the source engine's
// src/components/CodeBlock.tsx. Always renders with the oneDark theme
// (not theme-reactive), matching the source exactly.
export function CodeBlock({
  activeLines = [],
  code,
  language = 'typescript',
  onLineHover,
  style,
}) {
  // Ensure active lines are unique for comparison in effect deps
  const active = Array.from(new Set(activeLines));

  // Store previous lines to compare for changes
  const prevLinesRef = useRef([]);
  const isInitialRender = useRef(true);

  // Split current code into lines for comparison
  const currentLines = useMemo(() => code.trim().split('\n'), [code]);

  // Determine stable vs new/removed lines by content
  const lineStates = useMemo(() => {
    const prev = prevLinesRef.current;
    const prevSet = new Set(prev);
    const currentSet = new Set(currentLines);

    // Don't animate on initial render
    if (isInitialRender.current) {
      prevLinesRef.current = [...currentLines];
      isInitialRender.current = false;
      return { stable: currentSet, new: new Set(), removed: new Set() };
    }

    const stableLines = new Set();
    const newLines = new Set();
    const removedLines = new Set();

    // Find stable lines (exist in both)
    for (const line of currentLines) {
      if (prevSet.has(line)) {
        stableLines.add(line);
      } else {
        newLines.add(line);
      }
    }

    // Find removed lines (existed before but not now)
    for (const line of prev) {
      if (!currentSet.has(line)) {
        removedLines.add(line);
      }
    }

    // Update the ref for next comparison
    prevLinesRef.current = [...currentLines];

    return { stable: stableLines, new: newLines, removed: removedLines };
  }, [currentLines]);

  useEffect(() => {
    if (active.length === 0) return;
    // Scroll the first active line into view smoothly
    const selector = `[data-line-no="${active[0]}"]`;
    const el = document.querySelector(selector);
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [active]);

  return (
    <motion.div
      transition={{
        type: 'spring',
        visualDuration: 0.1,
        bounce: 0,
      }}
      style={{ overflow: 'hidden' }}
    >
      <Highlight theme={oneDark} code={code.trim()} language={language}>
        {(highlightProps) => {
          const {
            className,
            getLineProps,
            getTokenProps,
            style: defaultStyle,
            tokens,
          } = highlightProps;

          return (
            <pre
              className={className}
              style={{
                ...defaultStyle,
                margin: 0,
                borderRadius: 0,
                padding: 0,
                fontFamily: "Consolas, Monaco, 'Courier New', monospace",
                lineHeight: 1.6,
                backgroundColor: 'transparent',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                wordWrap: 'break-word',
                maxWidth: '100%',
                width: '100%',
                ...style,
              }}
            >
              <AnimatePresence mode="popLayout" initial={false}>
                {tokens.map((lineTokens, lineIndex) => {
                  const lineNo = lineIndex + 1;
                  const isActive = active.includes(lineNo);
                  const lineContent = currentLines[lineIndex] ?? '';
                  const isNewLine = lineStates.new.has(lineContent);

                  const lineProps = getLineProps({
                    line: lineTokens,
                    key: lineIndex,
                  });
                  const lineStyle = {
                    display: 'block',
                    paddingLeft: 0,
                    overflow: 'hidden',
                    ...(isActive
                      ? { background: 'rgba(56, 189, 248, 0.15)' }
                      : {}),
                    ...(lineProps.style ?? {}),
                  };

                  return (
                    <motion.div
                      key={`${lineIndex}-${lineContent}`}
                      className={lineProps.className}
                      style={lineStyle}
                      data-line-no={lineNo}
                      initial={
                        isNewLine
                          ? { opacity: 0, filter: 'blur(6px)', height: 0 }
                          : false
                      }
                      animate={{
                        opacity: 1,
                        filter: 'blur(0px)',
                        height: 'auto',
                      }}
                      exit={{ opacity: 0, filter: 'blur(6px)', height: 0 }}
                      transition={{
                        type: 'spring',
                        visualDuration: 0.1,
                        bounce: 0,
                      }}
                      onMouseEnter={() => onLineHover?.(lineNo)}
                      onMouseLeave={() => onLineHover?.(null)}
                    >
                      {lineTokens.map((token, tokenIndex) => {
                        const tokenProps = getTokenProps({
                          token,
                          key: tokenIndex,
                        });
                        // React keys must be passed directly rather than via spread props
                        const { key: _ignored, ...restTokenProps } = tokenProps;
                        return (
                          <span
                            key={`${lineNo}-${tokenIndex}`}
                            {...restTokenProps}
                          />
                        );
                      })}
                    </motion.div>
                  );
                })}
              </AnimatePresence>
            </pre>
          );
        }}
      </Highlight>
    </motion.div>
  );
}
