import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ClaudeLogo, CodexLogo, CursorLogo, OpenCodeLogo } from './logos';

const PROMPT =
  'Fetch https://zio.dev/start.md and follow the instructions to set up my environment for ZIO development.';

async function copyPrompt(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (_) {
    // fall through to legacy path
  }
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch (_) {
    return false;
  }
}

export default function OnboardAgentButton() {
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
      title="Click to copy the setup instruction to your clipboard"
      aria-label="Copy the ZIO agent onboarding prompt to the clipboard"
      className="hover:border-primary hover:text-primary flex items-center gap-2 rounded-full border border-zinc-300 px-6 py-2.5 text-base font-semibold leading-normal text-zinc-800 transition-colors dark:border-zinc-700 dark:text-zinc-100"
    >
      <span>{copied ? 'Copied!' : 'Onboard your agent to ZIO'}</span>
      <span className="flex items-center gap-1" aria-hidden="true">
        <ClaudeLogo />
        <CodexLogo />
        <CursorLogo />
        <OpenCodeLogo />
      </span>
      <span className="sr-only">Works with Claude, Codex, Cursor, and OpenCode</span>
    </button>
  );
}
