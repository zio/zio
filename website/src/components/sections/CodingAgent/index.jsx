import React from 'react';
import clsx from 'clsx';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageCodingAgent() {
  const installCommand = `npx skills add zio/zio-skills --skill zio-knowledge`;

  return (
    <section className={styles.codingAgent}>
      <div className={styles.wideContainer}>
        <div className={styles.singleColumn}>
          <div className={styles.agentContent}>
            <h2 className={clsx("sectionHeader", "text-4xl", "text-center")}>Teach Your Coding Agent the Latest ZIO Knowledge</h2>
            <p>
              The <code>zio-knowledge</code> skill teaches your coding agent to fetch live documentation
              from zio.dev before answering any ZIO question — so you always get accurate, up-to-date
              answers, not guesses from stale training data.
            </p>
            <ul>
              <li>Covers ZIO ecosystem modules like Core, Streams, Test, STM, Config, Schema, JSON, Kafka, and more</li>
              <li>Fetches current related docs from zio.dev on ZIO related development questions</li>
            </ul>
          </div>
          <div className={styles.codeContainer}>
            <div className={styles.codeWrapper}>
              <CodeBlock language="bash">
                {installCommand}
              </CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
