// Ported verbatim (TS types stripped) from the source engine's
// src/components/renderers/RenderableResult.ts.

// Type guard to check if a result is renderable
export function isRenderableResult(value) {
  return (
    value !== null &&
    typeof value === 'object' &&
    typeof value.render === 'function'
  );
}

// Helper function to render any result
export function renderResult(result) {
  if (isRenderableResult(result)) {
    return result.render();
  }

  // Default string rendering for non-renderable results
  return String(result);
}
