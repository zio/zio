import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { addAnnouncementBar } from '../scripts/lib/api-theme.mjs';

// The site's announcement bar as docusaurus.config.js declares it (themeConfig.announcementBar).
const BAR = {
  id: 'announcementBar-zioblocks',
  content: '<b>ZIO Blocks</b> has a new website! Visit <a href="https://zioblocks.com" target="_blank">zioblocks.com</a>',
  backgroundColor: '#ececec',
  textColor: '#1a1a1a',
  isCloseable: true,
};
const PAGE = '<!DOCTYPE html><html data-pathToRoot="../"><head></head><body><div id=""><div id="header"></div></div></body></html>';

test('the bar goes first in <body>, with the docs content and colours, and is measured as soon as it is drawn', () => {
  const html = addAnnouncementBar(PAGE, BAR);
  const body = html.slice(html.indexOf('<body>') + '<body>'.length);
  assert.ok(body.startsWith('<div class="za-announcement theme-announcement-bar" role="banner" style="background-color:#ececec;color:#1a1a1a">'));
  assert.ok(body.includes(`<div class="za-announcement__content">${BAR.content}</div>`));
  assert.ok(body.includes('<button type="button" aria-label="Close" class="za-announcement__close">'));
  assert.ok(body.includes('</div><script>window.zaPlaceAnnouncement&&zaPlaceAnnouncement()</script><div id="">'));
});

test('the page carries the bar id, for the dismissal rules theme.js shares with the docs pages', () => {
  assert.match(addAnnouncementBar(PAGE, BAR), /^<!DOCTYPE html><html data-za-announcement-id="announcementBar-zioblocks" data-pathToRoot/);
});

test('a bar that cannot be closed has no close button', () => {
  assert.doesNotMatch(addAnnouncementBar(PAGE, { ...BAR, isCloseable: false }), /za-announcement__close/);
});

test('no announcement bar in the config: the page is unchanged', () => {
  assert.equal(addAnnouncementBar(PAGE, undefined), PAGE);
});

test('a page without <body> fails the build rather than losing the bar', () => {
  assert.throws(() => addAnnouncementBar('<html><head></head></html>', BAR), /<body>/);
});

// theme.js in a small fake DOM: the page as addAnnouncementBar leaves it, a bar of `height` px, the window at `scrollY`.
function loadThemeScript({ stored = {}, height = 51, scrollY = 0 } = {}) {
  const store = new Map(Object.entries(stored));
  const attrs = { 'data-za-announcement-id': BAR.id };
  const props = {};
  const listeners = {};
  const close = { listeners: {}, addEventListener(type, f) { this.listeners[type] = f; }, click() { this.listeners.click(); } };
  const bar = { offsetHeight: height };
  const document = {
    documentElement: {
      classList: { toggle() {} },
      getAttribute: (k) => (k in attrs ? attrs[k] : null),
      setAttribute: (k, v) => (attrs[k] = String(v)),
      style: { setProperty: (k, v) => (props[k] = v) },
    },
    querySelector: (s) => (s === '.za-announcement' ? bar : s === '.za-announcement__close' ? close : null),
    querySelectorAll: () => [],
    getElementById: () => null,
    addEventListener() {},
  };
  const window = { scrollY, addEventListener: (type, f) => (listeners[type] = f) };
  const localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
  };
  vm.runInNewContext(readFileSync(new URL('../src/api-theme/theme.js', import.meta.url), 'utf8'), {
    window, document, localStorage, Element: class {},
  });
  window.zaPlaceAnnouncement(); // the inline call right after the bar
  listeners.DOMContentLoaded();
  const scrollTo = (y) => {
    window.scrollY = y;
    listeners.scroll();
  };
  return { store, attrs, props, close, scrollTo };
}

const DISMISSED = 'data-announcement-bar-initially-dismissed';

test('a first visit shows the bar, and records its id as the docs pages do', () => {
  const t = loadThemeScript();
  assert.equal(t.attrs[DISMISSED], 'false');
  assert.equal(t.store.get('docusaurus.announcement.id'), BAR.id);
  assert.equal(t.store.get('docusaurus.announcement.dismiss'), 'false');
});

test('a bar closed on any page of the site (docs or API) stays closed', () => {
  const t = loadThemeScript({ stored: { 'docusaurus.announcement.id': BAR.id, 'docusaurus.announcement.dismiss': 'true' } });
  assert.equal(t.attrs[DISMISSED], 'true');
  assert.equal(t.props['--za-announcement-offset'], '0px');
});

test('a new bar (another id) shows again, even after an older one was closed', () => {
  const t = loadThemeScript({ stored: { 'docusaurus.announcement.id': 'old-bar', 'docusaurus.announcement.dismiss': 'true' } });
  assert.equal(t.attrs[DISMISSED], 'false');
  assert.equal(t.store.get('docusaurus.announcement.id'), BAR.id);
  assert.equal(t.store.get('docusaurus.announcement.dismiss'), 'false');
});

test('closing the bar hides it, remembers it for the whole site, and moves the navbar back to the top', () => {
  const t = loadThemeScript();
  t.close.click();
  assert.equal(t.attrs[DISMISSED], 'true');
  assert.equal(t.store.get('docusaurus.announcement.dismiss'), 'true');
  assert.equal(t.props['--za-announcement-offset'], '0px');
});

test('the bar scrolls away with the page: the navbar sits under the part still in view, then at the top', () => {
  const t = loadThemeScript({ height: 51 });
  assert.equal(t.props['--za-announcement-offset'], '51px');
  t.scrollTo(20);
  assert.equal(t.props['--za-announcement-offset'], '31px');
  t.scrollTo(400);
  assert.equal(t.props['--za-announcement-offset'], '0px');
});
