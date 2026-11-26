import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// docs/ecosystem/index.mdx is committed text, written by scripts/generate-ecosystem-libraries.js; zioApiDocs in
// package.json decides which libraries get an API reference. A library added to or removed from zioApiDocs without
// regenerating the page would leave the list without its API link, or with a link to an API that is no longer built.
const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const page = readFileSync(new URL('../../docs/ecosystem/index.mdx', import.meta.url), 'utf8');

test('the ecosystem page links the API reference of exactly the libraries in zioApiDocs', () => {
  const linked = [...page.matchAll(/<a href="\/([^/"]+)\/api\/">API<\/a>/g)].map(([, lib]) => lib).sort();
  assert.deepEqual(linked, Object.keys(pkg.zioApiDocs).sort());
});

test('the ecosystem page has no API link that would open in a new tab', () => {
  assert.doesNotMatch(page, /\(pathname:\/\/\/[^)]*\/api\/\)/);
});
