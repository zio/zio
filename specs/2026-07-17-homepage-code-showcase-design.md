# Homepage Code Showcase — Design Spec

**Date:** 2026-07-17
**Scope:** Add a tabbed code-example section to the zio.dev homepage.
**Branch:** `home-page-harmony`

## Goal

The homepage currently shows no code at all. For an effect-system library the code is
the product; peer landing pages (Rust, Elixir, Cats Effect, Akka) all lead with a
snippet. Add a "Show me the code" section that proves the Features section's claims
with short, real ZIO snippets.

**Direction (user-validated via mockups):**

- Tabbed examples section, five tabs: Concurrency, Error handling, Resource safety,
  Streaming, Dependency Injection.
- Docusaurus-native rendering (`@theme/Tabs` + `@theme/CodeBlock`) — Infima underline
  tabs, standard docs-style code block. No custom tab UI.
- Placement: between Features and Ecosystem, wrapped in the existing `Reveal`
  scroll-entrance like every other section.

## Design

### 1. Component

New section folder following the established pattern:

```
website/src/components/sections/CodeShowcase/
  index.jsx
  data.js
```

`index.jsx`:

- `SectionWrapper` with `title="Show me the code"` and a one-line subtitle
  (e.g. "Five everyday problems, solved the ZIO way").
- Inside: `@theme/Tabs` with one `@theme/TabItem` per example
  (`value`/`label` from data).
- Each tab body: `@theme/CodeBlock` with `language="scala"` rendering the snippet,
  followed by a one-line takeaway paragraph styled like the Features card body text
  (`text-zinc-600 dark:text-zinc-400`).
- Content column capped (`max-w-3xl`, centered) so code lines stay readable.
- No component state: Docusaurus `Tabs` owns selection, keyboard navigation, and
  ARIA semantics.

### 2. Data

`data.js` exports:

```js
export const examples = [
  { value: 'concurrency', label: 'Concurrency', takeaway: '…', code: `…` },
  { value: 'errors',      label: 'Error handling', takeaway: '…', code: `…` },
  { value: 'resources',   label: 'Resource safety', takeaway: '…', code: `…` },
  { value: 'streaming',   label: 'Streaming', takeaway: '…', code: `…` },
  { value: 'di',          label: 'Dependency Injection', takeaway: '…', code: `…` },
];
```

- `code` is a template literal, 8–12 lines per snippet.
- Import lines omitted from snippets for signal density.
- Tab labels intentionally echo the Features card titles (Concurrent, Resilient,
  Resource-safe, …) so the section reads as proof of those claims.

### 3. Snippet correctness

- Snippets are written at implementation time against the `zio-knowledge` skill,
  not from model memory.
- Before commit, each snippet is compile-checked once via a `scala-cli` scratch file
  with the imports restored and a current ZIO 2.x dependency.
- No mdoc pipeline integration (considered and rejected: the homepage is JSX; wiring
  mdoc output into React is generated-file plumbing that isn't worth it for five
  snippets).

### 4. Placement

`website/src/pages/index.jsx`:

```jsx
<Reveal>
  <Features />
</Reveal>
<Reveal>
  <CodeShowcase />
</Reveal>
<Reveal>
  <Ecosystem … />
</Reveal>
```

### 5. Verification

- `npm run build` passes (SSR renders the section; check the built HTML contains the
  section title and first snippet).
- Browser check at `localhost:3000`, both themes — `CodeBlock` is theme-aware
  natively, no extra dark-mode work expected.
- Tab keyboard navigation (arrow keys) works — provided by Docusaurus `Tabs`.

## Out of scope

- Runnable/editable embeds (Scastie).
- Visual restyling of Infima tabs.
- Changes to any existing section.

## Files touched (expected)

| File | Change |
| --- | --- |
| `website/src/components/sections/CodeShowcase/index.jsx` | new section component |
| `website/src/components/sections/CodeShowcase/data.js` | five examples: label, takeaway, code |
| `website/src/pages/index.jsx` | mount `<CodeShowcase />` between Features and Ecosystem |
