import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// The cards of /ecosystem/ read src/components/EcosystemCards/ecosystem.json, committed data written by
// scripts/generate-ecosystem-libraries.js; zioApiDocs in package.json decides which libraries get an API reference. A
// library added to or removed from zioApiDocs without regenerating the data would leave its card without its API link,
// or with a link to an API that is no longer built.
const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const { cards } = JSON.parse(readFileSync(new URL('../src/components/EcosystemCards/ecosystem.json', import.meta.url), 'utf8'));
const component = readFileSync(new URL('../src/components/EcosystemCards/index.jsx', import.meta.url), 'utf8');

test('the ecosystem cards link the API reference of exactly the libraries in zioApiDocs', () => {
  const linked = cards.filter((c) => c.api).map((c) => c.href.replace(/^\/|\/$/g, '')).sort();
  assert.deepEqual(linked, Object.keys(pkg.zioApiDocs).sort());
});

// The API pages are static Scaladoc outside the Docusaurus router: a plain <a> loads them in the same tab, as the docs
// link of the card does; a Docusaurus Link or a pathname:// link would route them or open a new tab.
test('the API link of a card is a plain <a> that opens in the same tab', () => {
  assert.match(component, /<a className=\{styles\.api\} href=\{`\$\{card\.href\}api\/`\}>/);
  assert.doesNotMatch(component, /pathname:|target=/);
});
