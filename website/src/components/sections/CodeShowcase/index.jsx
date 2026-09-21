import React, { useState, useRef, useEffect, Suspense } from 'react';
import useIsBrowser from '@docusaurus/useIsBrowser';
import BrowserOnly from '@docusaurus/BrowserOnly';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import { FaCopy, FaCheck, FaArrowRight } from 'react-icons/fa6';
import styles from './styles.module.css';

import { examples } from './data';

// Code-split via React.lazy so `motion`, `effect`, and the rest of the
// animation engine only load into a separate chunk when a visitor actually
// opens the Visual tab, instead of shipping in the main homepage bundle.
// Still only ever rendered inside <BrowserOnly> below, so it never executes
// during Docusaurus's Node.js prerender of this page.
const VISUAL_COMPONENTS = {
  concurrency: React.lazy(
    () => import('../../visual-effects/scenarios/RaceVisual'),
  ),
  errors: React.lazy(
    () => import('../../visual-effects/scenarios/RetryExponentialVisual'),
  ),
  resources: React.lazy(
    () => import('../../visual-effects/scenarios/AcquireReleaseVisual'),
  ),
  streaming: React.lazy(
    () => import('../../visual-effects/scenarios/StreamingVisual'),
  ),
  di: React.lazy(
    () => import('../../visual-effects/scenarios/DependencyInjectionVisual'),
  ),
};

// Editor-style code panel ported from zio-http's HomepageCodeSnippet
// (website/src/components/HomepageCodeSnippet in the zio/zio-http repo):
// theme-aware editor panel with a tab bar, line numbers, and a copy toolbar.
export default function CodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
  const [copied, setCopied] = useState(false);
  const isBrowser = useIsBrowser();
  const timeoutRef = useRef(null);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const handleTabClick = (idx) => {
    setActiveTab(idx);
    setCopied(false);
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
  };

  const handleCopy = () => {
    if (!isBrowser) return;

    const textToCopy = examples[activeTab].code.trim();

    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard API is not available');
      }

      navigator.clipboard
        .writeText(textToCopy)
        .then(() => {
          setCopied(true);
          if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
          }
          timeoutRef.current = setTimeout(() => {
            setCopied(false);
            timeoutRef.current = null;
          }, 2000);
        })
        .catch((err) => {
          console.error('Failed to copy:', err);
        });
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const active = examples[activeTab];

  return (
    <section className={styles.codeSnippetSection}>
      <div className={styles.innerContainer}>
        {/* Left Column */}
        <div className={styles.leftColumn}>
          <h2>The ZIO Way</h2>
          <div className={styles.headingRule} />
          <p className={styles.takeaway}>{active.takeaway}</p>
          <ul className={styles.points}>
            {active.points.map((point, i) => (
              <li key={i}>
                <span className={styles.pointIcon} aria-hidden="true">
                  <FaCheck />
                </span>
                <span>{point}</span>
              </li>
            ))}
          </ul>
          <div className={styles.ctaWrap}>
            <Link className={styles.ctaButton} to="/overview/getting-started">
              <span>Explore the Docs</span>
              <FaArrowRight aria-hidden="true" />
            </Link>
          </div>
        </div>

        {/* Right Column */}
        <div className={styles.rightColumn}>
          <div className={styles.codePanel}>
            {/* Tab Bar */}
            <div className={styles.tabBar} role="tablist">
              {examples.map((example, idx) => (
                <button
                  key={example.value}
                  id={`tab-${idx}`}
                  data-label={example.label}
                  className={clsx(
                    styles.tab,
                    activeTab === idx && styles.tabActive,
                  )}
                  onClick={() => handleTabClick(idx)}
                  aria-selected={activeTab === idx}
                  aria-controls={`tabpanel-${idx}`}
                  type="button"
                  role="tab"
                >
                  {example.label}
                </button>
              ))}
            </div>

            {/* Visual area. There is no Code view any more: every tab has a
                visual, and each one already shows its own snippet inside the
                card, so a second copy behind a toggle was one control and one
                rendering path for nothing. The Copy button still copies the
                snippet. */}
            <div
              id={`tabpanel-${activeTab}`}
              className={styles.visualArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}
            >
              <BrowserOnly fallback={<div className={styles.visualArea} />}>
                {() => {
                  const VisualComponent = VISUAL_COMPONENTS[active.visual];
                  if (!VisualComponent) return null;
                  return (
                    <Suspense fallback={<div className={styles.visualArea} />}>
                      <VisualComponent />
                    </Suspense>
                  );
                }}
              </BrowserOnly>
            </div>

            {/* Toolbar */}
            <div className={styles.toolbar}>
              <div className={styles.toolbarLeft}>
                <span className={styles.langBadge}>Scala</span>
              </div>
              {isBrowser && (
                <button
                  type="button"
                  className={clsx(
                    styles.copyButton,
                    copied && styles.copyButtonCopied,
                  )}
                  onClick={handleCopy}
                  aria-label={copied ? 'Copied!' : 'Copy code'}
                  title={copied ? 'Copied!' : 'Copy to clipboard'}
                >
                  {copied ? <FaCheck size={14} /> : <FaCopy size={14} />}
                  <span>{copied ? 'Copied!' : 'Copy'}</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
