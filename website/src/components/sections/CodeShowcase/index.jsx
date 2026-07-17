import React, { useState } from 'react';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import clsx from 'clsx';
import styles from './styles.module.css';

import { examples } from './data';

// Editor-style panel inspired by zio-http's HomepageCodeSnippet, but the
// code area uses Docusaurus's @theme/CodeBlock so Scala highlighting, the
// copy button, and theme awareness come from the platform.
export default function CodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
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
                  onClick={() => setActiveTab(idx)}
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
              <CodeBlock language="scala" showLineNumbers>
                {active.code.trim()}
              </CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
