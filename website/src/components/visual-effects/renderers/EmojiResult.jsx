// Ported verbatim (TS types stripped) from the source engine's
// src/components/renderers/EmojiResult.tsx.
export class EmojiResult {
  constructor(emoji) {
    this.emoji = emoji;
  }

  render() {
    return (
      <span key="emoji" className="text-4xl">
        {this.emoji}
      </span>
    );
  }
}
