import React, { useEffect, useState } from 'react';
import clsx from 'clsx';
import CodeBlock from '@theme/CodeBlock';

import SectionWrapper from '@site/src/components/ui/SectionWrapper';

import { examples } from './data';
import styles from './styles.module.css';

const MOBILE_QUERY = '(max-width: 996px)';

export default function CodeShowcase() {
  const [activeValue, setActiveValue] = useState(examples[0].value);
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY);
    const update = () => setIsMobile(mql.matches);
    update();
    mql.addEventListener('change', update);
    return () => mql.removeEventListener('change', update);
  }, []);

  const activeIndex = examples.findIndex((e) => e.value === activeValue);
  const active = examples[activeIndex];

  const selectByIndex = (index, idPrefix, focusItem = true) => {
    const next = examples[(index + examples.length) % examples.length];
    setActiveValue(next.value);
    if (focusItem) {
      const el = document.getElementById(`${idPrefix}${next.value}`);
      if (el) el.focus();
    }
  };

  const handleKeyDown = (idPrefix) => (event) => {
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowRight':
        event.preventDefault();
        selectByIndex(activeIndex + 1, idPrefix);
        break;
      case 'ArrowUp':
      case 'ArrowLeft':
        event.preventDefault();
        selectByIndex(activeIndex - 1, idPrefix);
        break;
      case 'Home':
        event.preventDefault();
        selectByIndex(0, idPrefix);
        break;
      case 'End':
        event.preventDefault();
        selectByIndex(examples.length - 1, idPrefix);
        break;
      default:
        break;
    }
  };

  return (
    <SectionWrapper
      title="Show me the code"
      subtitle="Five everyday problems, solved the ZIO way"
    >
      <div className="container">
        <div className={styles.grid}>
          <div>
            <div
              className={styles.rail}
              role="tablist"
              aria-orientation="vertical"
              onKeyDown={handleKeyDown('code-showcase-tab-')}
            >
              {examples.map((example) => {
                const isActive = example.value === activeValue;
                return (
                  <button
                    key={example.value}
                    id={`code-showcase-tab-${example.value}`}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    aria-controls="code-showcase-panel"
                    tabIndex={isActive ? 0 : -1}
                    className={clsx(styles.railItem, {
                      [styles.railItemActive]: isActive,
                    })}
                    onClick={() => setActiveValue(example.value)}
                  >
                    <span className={styles.railItemTitle}>
                      {example.label}
                    </span>
                    {isActive ? (
                      <>
                        <span className={styles.railTakeaway}>
                          {example.takeaway}
                        </span>
                        <span
                          className={clsx(
                            styles.railDescription,
                            'text-zinc-600 dark:text-zinc-400',
                          )}
                        >
                          {example.description}
                        </span>
                      </>
                    ) : null}
                  </button>
                );
              })}
            </div>

            <div
              className={styles.chipRow}
              role="tablist"
              aria-orientation="horizontal"
              onKeyDown={handleKeyDown('code-showcase-chip-')}
            >
              {examples.map((example) => {
                const isActive = example.value === activeValue;
                return (
                  <button
                    key={example.value}
                    id={`code-showcase-chip-${example.value}`}
                    type="button"
                    role="tab"
                    aria-selected={isActive}
                    aria-controls="code-showcase-panel"
                    tabIndex={isActive ? 0 : -1}
                    className={clsx(styles.chip, {
                      [styles.chipActive]: isActive,
                    })}
                    onClick={() => setActiveValue(example.value)}
                  >
                    {example.label}
                  </button>
                );
              })}
            </div>

            <p
              className={clsx(
                styles.mobileDescription,
                'text-zinc-600 dark:text-zinc-400',
              )}
            >
              {active.description}
            </p>
          </div>

          <div
            className={styles.codePanel}
            role="tabpanel"
            id="code-showcase-panel"
            aria-labelledby={
              isMobile
                ? `code-showcase-chip-${active.value}`
                : `code-showcase-tab-${active.value}`
            }
          >
            <CodeBlock language="scala">{active.code}</CodeBlock>
          </div>
        </div>
      </div>
    </SectionWrapper>
  );
}
