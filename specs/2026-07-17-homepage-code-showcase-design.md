# Homepage Code Showcase — Design Spec

**Date:** 2026-07-17
**Scope:** Add a tabbed code-example section to the zio.dev homepage.
**Branch:** `home-page-harmony`
**Revision 2 (2026-07-17):** After the tabbed v1 shipped, the user judged it
insufficiently modern and picked a split "feature tour" layout from mockups.
Sections marked *(superseded by Revision 2)* below describe v1; the current
target is defined in the "Revision 2 — Split Feature Tour" section at the end.
Also note: the user later moved the section above Features (commit f80eb30f9),
superseding the original placement decision.

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

### 1. Component *(superseded by Revision 2)*

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

---

## Revision 2 — Split Feature Tour

**Direction (user-validated via mockups):** vertical topic rail on the left with
per-example descriptions; code panel on the right. Replaces the v1 tabbed layout.

### R2.1 Component

`CodeShowcase/index.jsx` becomes a custom interactive component (drops
`@theme/Tabs`/`@theme/TabItem`; keeps `SectionWrapper` and `@theme/CodeBlock`):

- `useState` holds the active example's `value`; first example active on mount.
- **Desktop (≥997px):** two-column grid, roughly 38% rail / 62% code panel, inside
  the same `container` width as other sections (the `max-w-3xl` cap from v1 is
  dropped — the split layout uses the full container).
- **Left rail:** one `<button>` per example.
  - Inactive: title only, muted text, transparent left border.
  - Active: card surface (white in light mode, the dark-mode card surface in dark),
    3px solid red (`--ifm-color-primary`) left accent border, containing: title
    (bold), `takeaway` (small, red), and `description` (2–3 sentences, body-muted
    color `text-zinc-600 dark:text-zinc-400`).
  - Hover on inactive items: subtle surface tint, matching existing card hover
    restraint (no translate).
- **Right panel:** `@theme/CodeBlock language="scala"` (theme-aware, standard
  Prism highlighting), wrapped in a rounded frame (border radius consistent with
  `card-modern`'s 14px, 1px `--ifm-color-emphasis-200` border, soft shadow).
- **Mobile (<997px):** single column — horizontally scrollable chip row (example
  titles) on top, active example's description paragraph beneath the chips, code
  panel below.
- Styling via a colocated `styles.module.css` (grid, rail, accent, chips) plus
  Tailwind utilities where they suffice; CSS module needed because the rail styles
  are stateful and structural.

### R2.2 Accessibility (replaces what Docusaurus Tabs provided)

- Rail wrapper: `role="tablist"` with `aria-orientation="vertical"`.
- Rail items: `role="tab"`, `aria-selected`, `id`; only the active tab is in the
  tab order (`tabIndex={active ? 0 : -1}`).
- Code panel: `role="tabpanel"` with `aria-labelledby` pointing at the active tab.
- Keyboard: ArrowUp/ArrowDown move selection between rail items (wrapping),
  Home/End jump to first/last. Selection follows focus.
- The mobile chip row reuses the same tablist semantics (orientation horizontal).

### R2.3 Data

Each `examples` entry in `data.js` gains a `description` field: 2–3 sentences,
written at implementation time, factually consistent with what its snippet shows
(e.g. concurrency: fibers are lightweight and the loser of a failed `zipPar` is
interrupted automatically). `value`, `label`, `takeaway`, `code` are unchanged;
snippets and the `specs/snippet-check/showcase.scala` compile-check file are
untouched by this revision.

### R2.4 Verification

- `npm run build` passes; built `index.html` contains the section title, `zipPar`,
  and at least one description sentence.
- Browser check, both themes: rail selection switches code, active card styling
  correct in dark mode, keyboard navigation per R2.2, mobile viewport shows the
  chip-row fallback.

### R2 files touched (expected)

| File | Change |
| --- | --- |
| `website/src/components/sections/CodeShowcase/index.jsx` | rewrite: custom rail/panel layout, tablist a11y |
| `website/src/components/sections/CodeShowcase/styles.module.css` | new: grid, rail, accent, chip-row styles |
| `website/src/components/sections/CodeShowcase/data.js` | add `description` per example |
