import { useEffect } from 'react';

// fullPage.js-style one-section-per-gesture scrolling. Intercepts wheel and
// keyboard paging: a single scroll gesture animates to the adjacent section
// instead of free-scrolling. Edge-aware — a section taller than the viewport
// still scrolls internally until its edge, then the next gesture advances.
const NAV_OFFSET = 72; // ~4.5rem sticky navbar
const LOCK_MS = 800;
const EPS = 4;

export default function useFullpageScroll(selector) {
  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const reduceMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches;

    const sections = () => Array.from(document.querySelectorAll(selector));

    let lockUntil = 0;

    const currentIndex = (list) => {
      let idx = 0;
      for (let i = 0; i < list.length; i += 1) {
        if (list[i].getBoundingClientRect().top <= NAV_OFFSET + 8) idx = i;
        else break;
      }
      return idx;
    };

    const goTo = (el) => {
      lockUntil = Date.now() + LOCK_MS;
      el.scrollIntoView({
        behavior: reduceMotion ? 'auto' : 'smooth',
        block: 'start',
      });
    };

    const onWheel = (e) => {
      if (e.ctrlKey) return; // pinch-zoom
      const now = Date.now();
      if (now < lockUntil) {
        e.preventDefault();
        return;
      }
      const dir = e.deltaY > EPS ? 1 : e.deltaY < -EPS ? -1 : 0;
      if (!dir) return;

      const list = sections();
      if (list.length < 2) return;
      const idx = currentIndex(list);
      const rect = list[idx].getBoundingClientRect();
      const vh = window.innerHeight;

      if (dir > 0) {
        // Still more of a tall section below the fold — let it scroll natively.
        if (rect.bottom > vh + EPS) return;
        if (idx >= list.length - 1) return;
        e.preventDefault();
        goTo(list[idx + 1]);
      } else {
        // More of a tall section above — let it scroll natively.
        if (rect.top < NAV_OFFSET - EPS) return;
        if (idx <= 0) return;
        e.preventDefault();
        goTo(list[idx - 1]);
      }
    };

    const onKeyDown = (e) => {
      const tag = (e.target.tagName || '').toLowerCase();
      if (tag === 'input' || tag === 'textarea' || e.target.isContentEditable) {
        return;
      }
      const now = Date.now();
      if (now < lockUntil) return;
      const list = sections();
      if (list.length < 2) return;
      const idx = currentIndex(list);
      if (['ArrowDown', 'PageDown'].includes(e.key) && idx < list.length - 1) {
        e.preventDefault();
        goTo(list[idx + 1]);
      } else if (['ArrowUp', 'PageUp'].includes(e.key) && idx > 0) {
        e.preventDefault();
        goTo(list[idx - 1]);
      }
    };

    window.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('wheel', onWheel);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [selector]);
}
