// Sound is out of scope for round 1 (see the design spec's "left out"
// list). This is a silent stand-in so the ported engine/feedback
// components' calls into taskSounds don't need to be edited out of their
// otherwise-exact source — every call here is a harmless no-op.
export const taskSounds = {
  playRunning: () => Promise.resolve(),
  playSuccess: () => Promise.resolve(),
  playFailure: () => Promise.resolve(),
  playInterrupted: () => Promise.resolve(),
  playDeath: () => Promise.resolve(),
  playNotificationChime: () => Promise.resolve(),
  playReset: () => {},
  playLinkCopied: () => {},
  playLinkHover: () => {},
  setMuted: () => {},
};
