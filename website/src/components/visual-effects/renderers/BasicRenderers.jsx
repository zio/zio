// Ported verbatim (TS types stripped) from the source engine's
// src/components/renderers/BasicRenderers.tsx.

// Simple number renderer
export class NumberResult {
  constructor(value) {
    this.value = value;
  }

  render() {
    return <div className="font-mono text-xl text-white">{this.value}</div>;
  }
}

// Simple string renderer
export class StringResult {
  constructor(value) {
    this.value = value;
  }

  render() {
    return <div className="font-mono text-xl text-white">{this.value}</div>;
  }
}

// Boolean renderer
export class BooleanResult {
  constructor(value) {
    this.value = value;
  }

  render() {
    return (
      <div className="font-mono text-white">
        {this.value ? 'true' : 'false'}
      </div>
    );
  }
}

// Object/JSON renderer
export class ObjectResult {
  constructor(value) {
    this.value = value;
  }

  render() {
    return (
      <div className="font-mono text-xs text-white">
        {JSON.stringify(this.value, null, 2)}
      </div>
    );
  }
}
