import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CORE_SITE, addBackLink, librarySite } from '../scripts/lib/api-theme.mjs';

// The part of a Scaladoc page the back link goes into: Scaladoc's ux.js appends its side menu to #leftColumn.
const PAGE = '<div id="mobile-sidebar-toggle"></div><div id="leftColumn" class="body-small"></div><div id="main"></div>';
const ZIO_JSON = librarySite({ lib: 'zio-json', title: 'ZIO JSON' });

test("a library's API page opens its left menu with a link back to the library's docs", () => {
  assert.equal(
    addBackLink(PAGE, ZIO_JSON),
    '<div id="mobile-sidebar-toggle"></div><div id="leftColumn" class="body-small">' +
      '<a class="za-back-link" href="/zio-json/">← ZIO JSON docs</a></div><div id="main"></div>',
  );
});

test("zio's own API pages get no back link", () => {
  assert.equal(addBackLink(PAGE, CORE_SITE), PAGE);
});

test("the library's name is written as text, not markup", () => {
  const html = addBackLink(PAGE, librarySite({ lib: 'zio-x', title: 'ZIO <X> & Y' }));
  assert.match(html, />← ZIO &lt;X&gt; &amp; Y docs<\/a>/);
});

test('a page without the left column fails the build rather than losing the link', () => {
  assert.throws(() => addBackLink('<div id="main"></div>', ZIO_JSON), /left column/);
});
