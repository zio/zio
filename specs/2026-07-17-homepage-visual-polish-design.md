# Homepage Visual Polish — Design Spec

**Status:** Abandoned — will not be implemented (user decision, 2026-07-17). Kept for reference only; do not execute.
**Date:** 2026-07-17
**Scope:** Visual polish of the zio.dev homepage only. No content, copy, section, or layout changes.
**Branch:** `home-page-harmony`

## Goal

Make the homepage look distinctly modern while keeping the existing section structure
(Hero, Features, Ecosystem, Zionomicon, Sponsors) and the established red/amber brand
token system intact.

**Direction (user-validated via mockups):**

- **Light mode — "Refined minimal"** (Vercel/shadcn school): flat surfaces, crisp 1px
  borders, faint shadows, restrained use of the brand red. Closest to current look,
  tightened.
- **Dark mode — "Glow + glass"** (Linear/Raycast school): deep near-black background,
  page-wide ambient red/amber radial glows, dot-grid texture, glass (translucent +
  blurred) cards, gradient CTA with soft halo.
- **Motion — subtle micro-motion:** existing Reveal-on-scroll and hover lifts stay;
  add slow orb drift (dark mode) and a gentle CTA glow on hover. Everything animated
  is gated behind `prefers-reduced-motion: no-preference`.

## Approach

CSS-first on the existing primitive system (approach 1 of 3 considered). All styling
lands in `website/src/css/custom.css` as extensions of the existing token +
`.card-modern` primitive system. The only JSX changes are one ambient-background
element on the homepage and the removal of the now-superseded hero glow div.

## Design

### 1. Tokens (`website/src/css/custom.css`)

Add to the existing token blocks:

```css
[data-theme='dark'] {
  --glow-red: rgba(220, 38, 38, 0.25);
  --glow-amber: rgba(245, 158, 11, 0.12);
  --surface-glass: rgba(255, 255, 255, 0.05);
  --surface-glass-border: rgba(255, 255, 255, 0.12);
  --ifm-background-color: #0a0a0c !important; /* was #121212 */
}
```

Light mode gets no new tokens; the orb/texture layer does not render there.

### 2. Ambient background layer (dark mode, homepage only)

- `website/src/pages/index.jsx` renders `<div className="ambient-bg" aria-hidden="true" />`
  as the first child inside `Layout`.
- CSS: `position: fixed; inset: 0; z-index: -10; pointer-events: none;`.
- Content: three radial-gradient orbs — top-center red (`--glow-red`), mid-right amber
  (`--glow-amber`), bottom-left red at reduced alpha — plus a dot grid
  (`radial-gradient(rgba(255,255,255,.05) 1px, transparent 1px)`, `background-size: 14px 14px`).
- Hidden entirely outside `[data-theme='dark']` (`display: none`).
- Drift: one `@keyframes ambient-drift` animating `background-position` over ~40s,
  applied only inside `@media (prefers-reduced-motion: no-preference)`.
- The existing hero-local glow div in `website/src/components/sections/Hero/index.jsx`
  is removed (superseded by the page-wide layer). Its light-mode loss is acceptable:
  light mode is the flat-minimal direction.

### 3. Dark glass surfaces

`[data-theme='dark']` overrides:

- `.card-modern`: `background: var(--surface-glass);
  border-color: var(--surface-glass-border); backdrop-filter: blur(8px);` with a
  solid-color fallback background declared before `backdrop-filter` for engines
  without support.
- `.ecosystemCard` lives in a CSS module (hashed class name), so global CSS cannot
  target it: the same glass override is added to
  `Ecosystem/styles.module.css`'s existing `[data-theme='dark']` block, using the
  same tokens.
- Hover behavior unchanged (translateY(-4px) + `0 14px 30px var(--brand-shadow)`,
  border warms toward red).
- Zionomicon book image frame gets the same glass border treatment.
- Navbar/footer: already translucent dark; only the bottom border tint is adjusted to
  `--surface-glass-border`.

### 4. Light mode refinement + CTA treatment

- Light mode: `.card-modern` keeps 1px zinc borders; shadow stays at `0 1px 2px
  rgba(0,0,0,.05)`. No texture, no orbs, solid red primary CTA (current behavior).
- Dark mode CTA ("Get Started" pill in Hero): `background: var(--gradient-brand)`
  with `box-shadow: 0 0 18px var(--glow-red)`; hover brightens
  (`filter: brightness(1.1)`). Implemented as a `[data-theme='dark']` override on a
  small `.cta-primary` class added to the Hero/Zionomicon primary pills so the
  Tailwind classes stay the light-mode source of truth.
- GitHub / secondary pills in dark mode: glass background (`--surface-glass`) instead
  of transparent.
- `.eyebrow` in dark mode gains `border: 1px solid rgba(252, 165, 165, 0.25)`.

### 5. Accessibility, performance, verification

- All new animation sits inside `@media (prefers-reduced-motion: no-preference)`.
- Contrast: body text on glass surfaces is zinc-100/zinc-400 over effectively
  `#0a0a0c` — passes WCAG AA; verify spot values after implementation.
- `backdrop-filter` fallback: opaque dark surface color declared first.
- The fixed ambient layer is a single element with plain CSS gradients — no images,
  no JS, no scroll listeners.
- Verification: `npm run build` passes; manual browser check of both themes at
  `localhost:3000` (hero, all four sections, hovers, theme toggle round-trip).

## Out of scope

- Any copy, content, or section reordering.
- Docs pages, navbar structure, footer content.
- The ByteBrain chat widget (already brand-styled).

## Files touched (expected)

| File | Change |
| --- | --- |
| `website/src/css/custom.css` | tokens, ambient layer, glass overrides, CTA/eyebrow dark styles, drift keyframes |
| `website/src/pages/index.jsx` | add `.ambient-bg` element |
| `website/src/components/sections/Hero/index.jsx` | remove hero glow div; add `.cta-primary` to primary pill |
| `website/src/components/sections/Zionomicon/index.js` | add `.cta-primary` to book CTA pill |
| `website/src/components/sections/Ecosystem/styles.module.css` | dark glass override (CSS module classes are hashed; global CSS cannot reach them) |
