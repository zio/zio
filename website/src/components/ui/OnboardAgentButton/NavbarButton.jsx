import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FaCheck, FaCopy } from 'react-icons/fa6';

import { PROMPT, copyPrompt } from './index';
import styles from './NavbarButton.module.css';

// Compact navbar variant of the hero's "Onboard your agent to ZIO" button:
// copies the same setup prompt to the clipboard, shown as a small pill.
export default function OnboardAgentNavbarButton() {
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  useEffect(() => () => timer.current && clearTimeout(timer.current), []);

  const onClick = useCallback(async () => {
    const ok = await copyPrompt(PROMPT);
    if (!ok) {
      window.prompt('Copy this prompt for your coding agent:', PROMPT);
      return;
    }
    setCopied(true);
    timer.current && clearTimeout(timer.current);
    timer.current = setTimeout(() => setCopied(false), 2000);
  }, []);

  return (
    <button
      type="button"
      onClick={onClick}
      title="Copy the setup instruction for your coding agent"
      aria-label="Copy the ZIO agent onboarding prompt to the clipboard"
      className={styles.button}
    >
      {copied ? (
        <FaCheck aria-hidden="true" />
      ) : (
        <FaCopy aria-hidden="true" />
      )}
    </button>
  );
}
