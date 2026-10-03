import { useEffect, useState } from 'react';

// Ported verbatim (TS types stripped) from the source engine's
// src/hooks/useOptionKey.ts.
let globalOptionPressed = false;
const subscribers = new Set();
let eventListenersAttached = false;

const handleKeyDown = (e) => {
  if (e.altKey && !globalOptionPressed) {
    globalOptionPressed = true;
    subscribers.forEach((callback) => {
      callback(true);
    });
  }
};

const handleKeyUp = (e) => {
  if (!e.altKey && globalOptionPressed) {
    globalOptionPressed = false;
    subscribers.forEach((callback) => {
      callback(false);
    });
  }
};

const attachEventListeners = () => {
  if (!eventListenersAttached) {
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    eventListenersAttached = true;
  }
};

const detachEventListeners = () => {
  if (eventListenersAttached && subscribers.size === 0) {
    window.removeEventListener('keydown', handleKeyDown);
    window.removeEventListener('keyup', handleKeyUp);
    eventListenersAttached = false;
  }
};

/**
 * Hook to track the Option/Alt key state globally.
 * This ensures consistent state across all components that need to know about Option key presses.
 */
export function useOptionKey() {
  const [isOptionPressed, setIsOptionPressed] = useState(globalOptionPressed);

  useEffect(() => {
    subscribers.add(setIsOptionPressed);
    attachEventListeners();
    setIsOptionPressed(globalOptionPressed);

    return () => {
      subscribers.delete(setIsOptionPressed);
      detachEventListeners();
    };
  }, []);

  return isOptionPressed;
}
