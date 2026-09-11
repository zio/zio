import { Effect } from 'effect';
import { EmojiResult } from '../renderers';

// Ported from the source engine's src/examples/helpers.ts — the pieces
// effect-race.jsx and effect-retry-exponential.jsx actually use (getDelay,
// Emoji, loadEmoji, createCounter). getWeather is still omitted, unused by
// either ported example.

/**
 * Generates a random delay with jitter for realistic network simulation.
 * @param min minimum delay in milliseconds
 * @param max maximum delay in milliseconds
 * @returns a delay between min and max
 */
export function getDelay(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export const Emoji = {
  Achilles: '🏃‍♂️',
  Tortoise: '🐢',
  Dog: '🐶',
  Cat: '🐱',
  Mouse: '🐭',
  Rabbit: '🐰',
  Fox: '🦊',
  Bear: '🐻',
  Panda: '🐼',
  Koala: '🐨',
  Lion: '🦁',
  Tiger: '🐯',
  Elephant: '🐮',
};

/**
 * Simulates fetching data from a CDN, returns a random animal emoji.
 */
export function loadEmoji(emoji) {
  return Effect.gen(function* () {
    const delay = getDelay(500, 900);
    yield* Effect.sleep(delay);

    return new EmojiResult(emoji);
  });
}

/**
 * Creates a stateful counter with reset functionality.
 * @param initialValue Initial counter value (default 0)
 * @returns Object with `current` (getter), `increment()`, and a `reset` Effect
 */
export function createCounter(initialValue = 0) {
  let value = initialValue;

  return {
    get current() {
      return value;
    },
    increment() {
      value++;
    },
    reset: Effect.sync(() => {
      value = initialValue;
    }),
  };
}
