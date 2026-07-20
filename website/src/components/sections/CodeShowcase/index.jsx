import React, { useState, useRef, useEffect } from 'react';
import { Highlight, themes, Prism } from 'prism-react-renderer';
import useIsBrowser from '@docusaurus/useIsBrowser';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import { FaCopy, FaCheck } from 'react-icons/fa6';
import styles from './styles.module.css';

import { examples } from './data';

// prism-react-renderer v2 highlights against the shared prismjs instance.
// The homepage has no @theme/CodeBlock to trigger Docusaurus's language
// loader, so register Scala (extends Java) here against that same instance.
// Idempotent if Docusaurus already loaded it elsewhere.
globalThis.Prism = Prism;
require('prismjs/components/prism-java');
require('prismjs/components/prism-scala');
delete globalThis.Prism;

// Editor-style code panel ported from zio-http's HomepageCodeSnippet
// (website/src/components/HomepageCodeSnippet in the zio/zio-http repo):
// always-dark dracula panel with a tab bar, line numbers, and a copy toolbar.
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
          <h2>ZIO by Example</h2>
          <p className={styles.takeaway}>{active.takeaway}</p>
          <ul className={styles.points}>
            {active.points.map((point, i) => (
              <li key={i}>{point}</li>
            ))}
          </ul>
          <div>
            <Link
              className="button button--outline button--lg"
              to="/overview/getting-started"
            >
              Explore the Docs
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

            {/* Code Area */}
            <div
              id={`tabpanel-${activeTab}`}
              className={styles.codeArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}
            >
              <Highlight
                key={activeTab}
                theme={themes.dracula}
                code={active.code.trim()}
                language="scala"
              >
                {({
                  className,
                  style,
                  tokens,
                  getLineProps,
                  getTokenProps,
                }) => (
                  <pre className={`${className} ${styles.pre}`} style={style}>
                    <code>
                      {tokens.map((line, i) => (
                        <div
                          key={i}
                          {...getLineProps({ line, key: i })}
                          className={styles.codeLine}
                        >
                          <span className={styles.lineNumber}>{i + 1}</span>
                          <span className={styles.lineContent}>
                            {line.map((token, key) => (
                              <span
                                key={key}
                                {...getTokenProps({ token, key })}
                              />
                            ))}
                          </span>
                        </div>
                      ))}
                    </code>
                  </pre>
                )}
              </Highlight>
            </div>

            {/* Toolbar */}
            <div className={styles.toolbar}>
              <span className={styles.langBadge}>Scala</span>
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
