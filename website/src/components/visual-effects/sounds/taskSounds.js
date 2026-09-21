import * as Tone from 'tone';

// Ported verbatim (TS types/casts stripped) from the source engine's
// src/sounds/TaskSounds.ts.

// Musical scale configuration
const PENTATONIC_SCALE = ['C', 'D', 'E', 'G', 'A'];
const BASE_OCTAVE = 3;

// Time (in ms) within which multiple notes are considered part of the same
// "chord" and will be voiced as adjacent scale degrees in the *same* octave.
const CHORD_WINDOW_MS = 100;

// Bespoke additions for the Streaming tab (no source equivalent).
//
// Three plain sounds, not a kit: a soft click when a worker picks an item
// up, a clear note when one is finished, and a low note when the pipeline
// stalls. Earlier passes voiced every stage with its own timbre, which was
// busier than the visual and harder to read, not easier.
const STREAM_STAGE_VOICES = {
  enriching: { voice: 'click', note: 'C5', duration: '32n', velocity: 0.12 },
  written: { voice: 'done', note: 'G5', duration: '8n', velocity: 0.25 },
  backpressure: { voice: 'stall', note: 'C3', duration: '4n', velocity: 0.22 },
};

// Minimum spacing between two pipeline notes, in seconds.
const STREAM_NOTE_GAP = 0.16;

class TaskSoundSystem {
  constructor() {
    // Poly synths for overlapping notes
    this.synthSuccess = null;
    this.synthRunning = null;
    this.synthBass = null;
    this.interruptSynth = null;
    this.resetSynth = null;
    this.synthDeath = null;
    this.synthRefUpdate = null;
    this.synthFinalizer = null;
    this.synthLinkHover = null;
    this.synthLinkCopied = null;
    this.synthNotification = null;
    this.synthConfig = null; // configuration change chime

    // FX / routing
    this.distortion = null;
    this.reverb = null;
    this.volume = null;

    // Lifecycle / state
    this.initialized = false;
    this.initializing = null;
    this.muted = false;
    this.currentNoteIndex = 0;
    this.transport = null;

    // Chord-scheduling helpers
    this.chordWindowStart = null;
    this.chordStep = 0;
    this.chordBaseIndex = 0;
    this.chordBaseOctave = BASE_OCTAVE;

    // Error sound management
    this.isPlayingFailure = false;
    this.isPlayingInterrupt = false;
  }

  // --- Small helpers -------------------------------------------------------

  /** Returns true if still inside the active chord window. */
  inChordWindow(now = Date.now()) {
    return (
      this.chordWindowStart !== null &&
      now - this.chordWindowStart <= CHORD_WINDOW_MS
    );
  }

  /** Guard used by most play* methods; ensures audio is ready unless muted. */
  async ready() {
    if (this.muted) return false;
    await this.initialize();
    return true;
  }

  /** Schedules a one-off callback on the (started) Transport. */
  scheduleOnce(cb, time) {
    // transport is set in initialize(); a fallback is included for safety.
    const t = this.transport ?? Tone.getTransport();
    t.scheduleOnce(cb, time);
  }

  // --- Initialization ------------------------------------------------------

  async initialize() {
    if (this.initialized) return;
    if (this.initializing) return this.initializing;

    // Kick off initialization once, and always clear the latch.
    this.initializing = (async () => {
      // Ensure audio context is running (await to avoid race with node creation)
      await Tone.start();

      // Create volume control
      this.volume = new Tone.Volume(-12).toDestination();

      // Create effects
      this.reverb = new Tone.Reverb({ decay: 2.5, wet: 0.3 }).connect(
        this.volume,
      );
      this.distortion = new Tone.Distortion({
        distortion: 0.8,
        wet: 1.0,
      }).connect(this.volume);

      // --- Synths (unchanged options/voicings) -----------------------------
      this.synthSuccess = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.02, decay: 0.3, sustain: 0.1, release: 1.2 },
      }).connect(this.reverb);

      this.synthRunning = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.002, decay: 0.08, sustain: 0, release: 0.1 },
      }).connect(this.reverb);

      this.synthBass = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sawtooth' },
        envelope: { attack: 0.02, decay: 0.4, sustain: 0.1, release: 0.8 },
      }).connect(this.reverb);

      this.interruptSynth = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.001, decay: 0.08, sustain: 0, release: 0.04 },
      }).connect(this.reverb);

      this.resetSynth = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.004, decay: 0.18, sustain: 0, release: 0.12 },
      }).connect(this.reverb);

      this.synthDeath = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'fatsawtooth10' },
        envelope: { attack: 0.01, decay: 0.5, sustain: 0.3, release: 1.5 },
      }).connect(this.distortion);

      this.synthConfig = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.001, decay: 0.05, sustain: 0, release: 0.05 },
      }).connect(this.reverb);

      this.synthRefUpdate = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.001, decay: 0.04, sustain: 0, release: 0.04 },
      }).connect(this.reverb);

      this.synthFinalizer = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'square4' },
        envelope: { attack: 0.005, decay: 0.12, sustain: 0.05, release: 0.25 },
      }).connect(this.reverb);

      this.synthLinkHover = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'sine' },
        envelope: { attack: 0.001, decay: 0.03, sustain: 0, release: 0.02 },
      }).connect(this.reverb);

      this.synthLinkCopied = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.002, decay: 0.15, sustain: 0.05, release: 0.3 },
      }).connect(this.reverb);

      this.synthNotification = new Tone.PolySynth(Tone.Synth, {
        oscillator: { type: 'triangle' },
        envelope: { attack: 0.005, decay: 0.25, sustain: 0.1, release: 0.4 },
      }).connect(this.reverb);

      // Bespoke, not part of the ported set: three plain voices for the
      // Streaming pipeline, deliberately simple.
      this.streamVoices = {
        click: new Tone.PolySynth(Tone.Synth, {
          oscillator: { type: 'sine' },
          envelope: { attack: 0.001, decay: 0.05, sustain: 0, release: 0.05 },
        }).connect(this.volume),

        done: new Tone.PolySynth(Tone.Synth, {
          oscillator: { type: 'triangle' },
          envelope: { attack: 0.003, decay: 0.25, sustain: 0, release: 0.4 },
        }).connect(this.reverb),

        stall: new Tone.PolySynth(Tone.Synth, {
          oscillator: { type: 'sine' },
          envelope: { attack: 0.02, decay: 0.35, sustain: 0, release: 0.4 },
        }).connect(this.reverb),
      };

      // Transport: ensure scheduleOnce works reliably
      this.transport = Tone.getTransport();
      if (this.transport.state !== 'started') {
        this.transport.start();
      }

      this.initialized = true;
    })();

    try {
      await this.initializing;
    } finally {
      // Ensure latch is always cleared, even if initialization fails
      this.initializing = null;
    }
  }

  // --- Note selection ------------------------------------------------------

  getNextNote(octaveOffset = 0) {
    const now = Date.now();
    const inChordWindow = this.inChordWindow(now);

    if (!inChordWindow) {
      // Start new chord window
      this.chordWindowStart = now;
      this.chordStep = 0;
      // Root of the chord based on rotating index
      this.chordBaseIndex = this.currentNoteIndex % PENTATONIC_SCALE.length;
      this.chordBaseOctave = BASE_OCTAVE + octaveOffset;
    }

    // Adjacent scale degrees within the same octave for tight harmony
    const scaleIndex =
      (this.chordBaseIndex + this.chordStep) % PENTATONIC_SCALE.length;
    const note = PENTATONIC_SCALE[scaleIndex];
    const octave = this.chordBaseOctave;

    // Advance counters for next call
    this.chordStep++;
    this.currentNoteIndex =
      (this.currentNoteIndex + 1) % (PENTATONIC_SCALE.length * 2);

    return `${note}${octave}`;
  }

  // --- Public API (unchanged behavior) ------------------------------------

  async playSuccess() {
    if (!(await this.ready())) return;

    // Triad cycling within a timing window to keep overlaps consonant
    const now = Date.now();
    const inWindow = this.inChordWindow(now);

    if (!inWindow) {
      this.chordWindowStart = now;
      this.chordStep = 0;
      this.chordBaseIndex = this.currentNoteIndex % PENTATONIC_SCALE.length;
      this.chordBaseOctave = BASE_OCTAVE + 1; // brightness
    }

    const rootNoteName = PENTATONIC_SCALE[this.chordBaseIndex];
    const rootNoteStr = `${rootNoteName}${this.chordBaseOctave}`;

    const TRIAD_SEMITONES = [0, 4, 7];
    const triadIndex = this.chordStep % TRIAD_SEMITONES.length;
    const semitoneOffset = TRIAD_SEMITONES[triadIndex] ?? 0;

    const note = Tone.Frequency(rootNoteStr).transpose(semitoneOffset).toNote();

    this.chordStep++;
    this.currentNoteIndex =
      (this.currentNoteIndex + 1) % (PENTATONIC_SCALE.length * 2);

    this.synthSuccess?.triggerAttackRelease(note, '4n');
  }

  async playFailure() {
    if (this.muted) return;
    if (this.isPlayingFailure) return;
    this.isPlayingFailure = true;

    await this.initialize();

    // Deep bass tone
    const note = `${PENTATONIC_SCALE[this.currentNoteIndex % PENTATONIC_SCALE.length]}${BASE_OCTAVE - 1}`;
    this.currentNoteIndex =
      (this.currentNoteIndex + 1) % PENTATONIC_SCALE.length;

    const now = Tone.now();
    this.synthBass?.triggerAttackRelease(note, '4n', now, 0.65);

    // Reset flag after the sound completes (~0.2 s)
    this.scheduleOnce(() => {
      this.isPlayingFailure = false;
    }, '+0.2');
  }

  async playInterrupted() {
    if (this.muted) return;
    if (this.isPlayingInterrupt) return;
    this.isPlayingInterrupt = true;

    await this.initialize();

    // Two rapid ascending beeps (Metal Gear-style alert)
    const note1 = 'C5';
    const note2 = 'E5';

    const now = Tone.now();
    this.interruptSynth?.triggerAttackRelease(note1, '32n', now, 0.6);
    this.interruptSynth?.triggerAttackRelease(note2, '32n', now + 0.07, 0.6);

    this.scheduleOnce(() => {
      this.isPlayingInterrupt = false;
    }, '+0.2');
  }

  async playRunning() {
    if (!(await this.ready())) return;
    const note = this.getNextNote(0.5); // half octave higher
    this.synthRunning?.triggerAttackRelease(note, '32n', undefined, 0.25);
  }

  async playReset() {
    if (!(await this.ready())) return;

    // Classic two-note descending cue (G → C)
    const note1 = `G${BASE_OCTAVE}`;
    const note2 = `C${BASE_OCTAVE}`;

    const now = Tone.now();
    this.resetSynth?.triggerAttackRelease(note1, '16n', now, 0.6);
    this.resetSynth?.triggerAttackRelease(note2, '16n', now + 0.1, 0.6);
  }

  async playDeath() {
    if (!(await this.ready())) return;

    const now = Tone.now();
    // Short distorted stab
    this.synthDeath?.triggerAttackRelease(`D#${BASE_OCTAVE}`, '32n', now, 0.45);
    // Long, low distorted rumble to finish (100 ms later)
    this.synthDeath?.triggerAttackRelease(
      `C${BASE_OCTAVE - 2}`,
      '1n',
      now + 0.1,
      0.55,
    );
  }

  /** Pleasant two-note ascending chime when users change configuration. */
  async playConfigurationChange() {
    if (!(await this.ready())) return;
    this.synthConfig?.triggerAttackRelease('G5', '16n', undefined, 0.6);
  }

  /** Subtle one-note blip when VisualRef values update. */
  async playRefUpdate() {
    if (!(await this.ready())) return;
    this.synthRefUpdate?.triggerAttackRelease('E6', '64n', undefined, 0.35);
  }

  /** Soft registration sound when a finalizer is created. */
  async playFinalizerCreated() {
    if (!(await this.ready())) return;
    const note = this.getNextNote(0); // Base octave
    this.synthFinalizer?.triggerAttackRelease(note, '32n', undefined, 0.25);
  }

  /** Mid-range sound when a finalizer starts running. */
  async playFinalizerRunning() {
    if (!(await this.ready())) return;
    const note = this.getNextNote(0.5); // Half octave up
    this.synthFinalizer?.triggerAttackRelease(note, '16n', undefined, 0.3);
  }

  /** Higher sound when a finalizer completes. */
  async playFinalizerCompleted() {
    if (!(await this.ready())) return;
    const note = this.getNextNote(1); // One octave up
    this.synthFinalizer?.triggerAttackRelease(note, '8n', undefined, 0.35);
  }

  // --- Streaming pipeline (bespoke, no source equivalent) ------------------

  /**
   * Voices one pipeline event (see STREAM_STAGE_VOICES). Only three events
   * make a sound; the rest are left silent on purpose.
   *
   * Note the ported helpers ask for `getNextNote(0.5)` intending half an
   * octave up; that builds a note string like "C3.5", which Tone parses as
   * plain C3, so those offsets are silently inert. These use explicit
   * integer octaves instead.
   *
   * Simultaneous events are spread rather than stacked: four enrichments
   * starting at once fired four sounds in the same millisecond, which read as
   * one blur. Each is scheduled at least STREAM_NOTE_GAP after the previous.
   */
  async playStreamStage(stage) {
    if (!(await this.ready())) return;

    const voice = STREAM_STAGE_VOICES[stage];
    if (!voice) return;

    const now = Tone.now();
    const at = Math.max(now, this.streamNextNoteAt ?? 0);
    this.streamNextNoteAt = at + STREAM_NOTE_GAP;

    this.streamVoices?.[voice.voice]?.triggerAttackRelease(
      voice.note,
      voice.duration,
      at,
      voice.velocity,
    );
  }

  /** Ultra-subtle sound when hovering over link option. */
  async playLinkHover() {
    if (!(await this.ready())) return;
    this.synthLinkHover?.triggerAttackRelease('G6', '64n', undefined, 0.2);
  }

  /** Pleasant chime when link is successfully copied. */
  async playLinkCopied() {
    if (!(await this.ready())) return;

    const note1 = 'E5';
    const note2 = 'G5';

    const now = Tone.now();
    this.synthLinkCopied?.triggerAttackRelease(note1, '16n', now, 0.5);
    this.synthLinkCopied?.triggerAttackRelease(note2, '16n', now + 0.08, 0.5);
  }

  /** Gentle chime when a notification appears. */
  async playNotificationChime() {
    if (!(await this.ready())) return;

    const note1 = 'C5';
    const note2 = 'E5';

    const now = Tone.now();
    this.synthNotification?.triggerAttackRelease(note1, '16n', now, 0.4);
    this.synthNotification?.triggerAttackRelease(note2, '16n', now + 0.12, 0.4);
  }

  setMuted(muted) {
    this.muted = muted;
  }

  setVolume(volume) {
    if (!this.volume) return;
    // Map 0..1 to -Infinity..0 dB (0 => hard mute)
    const db = volume === 0 ? -Infinity : -40 + volume * 40;
    this.volume.volume.value = db;
  }

  async dispose() {
    // Dispose & null all nodes; keep flags and counters unchanged.
    this.synthSuccess?.dispose();
    this.synthSuccess = null;
    this.synthRunning?.dispose();
    this.synthRunning = null;
    this.synthBass?.dispose();
    this.synthBass = null;
    this.interruptSynth?.dispose();
    this.interruptSynth = null;
    this.resetSynth?.dispose();
    this.resetSynth = null;
    this.synthDeath?.dispose();
    this.synthDeath = null;
    this.synthFinalizer?.dispose();
    this.synthFinalizer = null;
    this.synthLinkHover?.dispose();
    this.synthLinkHover = null;
    this.synthLinkCopied?.dispose();
    this.synthLinkCopied = null;
    this.synthNotification?.dispose();
    this.synthNotification = null;
    this.synthConfig?.dispose();
    this.synthConfig = null;
    this.synthRefUpdate?.dispose();
    this.synthRefUpdate = null;

    this.distortion?.dispose();
    this.distortion = null;
    this.reverb?.dispose();
    this.reverb = null;
    this.volume?.dispose();
    this.volume = null;

    this.initialized = false;
    // keep this.transport as-is; it's owned by Tone.js and reused globally
  }
}

// Singleton instance
export const taskSounds = new TaskSoundSystem();
