import React, { useState } from 'react';
import clsx from 'clsx';
import { FaCheck, FaCopy } from 'react-icons/fa6';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageCodingAgent() {
  const installCommand = `npx skills add zio/zio-skills --skill zio-knowledge`;
  const [copied, setCopied] = useState(false);

  const handleCopyCommand = () => {
    navigator.clipboard.writeText(installCommand);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section className={styles.codingAgent}>
      <div className={styles.wideContainer}>
        <div className={styles.singleColumn}>
          <div className={styles.agentContent}>
            <h2 className={clsx("sectionHeader", "text-4xl")}>Teach Your AI Coding Agent ZIO</h2>
            <p className={styles.agentSubtitle}>
              Give your AI assistant always-current ZIO knowledge
            </p>
            <p>
              The <code>zio-knowledge</code> skill teaches Claude Code to fetch live documentation
              from zio.dev before answering any ZIO question — so you always get accurate, up-to-date
              answers, not guesses from stale training data.
            </p>
            <ul>
              <li>Covers ZIO core, Streams, Test, STM, Config, Schema, JSON, Kafka, and the full ecosystem</li>
              <li>Fetches current docs from zio.dev on every question</li>
              <li>Works seamlessly with Claude Code's slash commands</li>
            </ul>
          </div>
          <div className={styles.codeContainer}>
            <div className={styles.codeWrapper}>
              <button
                className={clsx(styles.copyButton, { [styles.copied]: copied })}
                onClick={handleCopyCommand}
                title="Copy installation command to clipboard"
              >
                {copied ? <FaCheck className={styles.icon} /> : <FaCopy className={styles.icon} />}
              </button>
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
