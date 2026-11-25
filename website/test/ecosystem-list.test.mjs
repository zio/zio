import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { libraryLine, apiHrefFor } = require('../scripts/lib/ecosystem-list.js');

const apiDocs = { 'zio-json': {}, 'zio-prelude': { modules: ['zio-prelude'] } };

test('adds the API link for any library in zioApiDocs, whatever its sidebar', () => {
  assert.equal(apiHrefFor('official', 'zio-json', apiDocs), '/zio-json/api/');
  assert.equal(apiHrefFor('official', 'zio-prelude', apiDocs), '/zio-prelude/api/');
});

test('no API link for a library outside zioApiDocs, an external site, or a community library', () => {
  assert.equal(apiHrefFor('official', 'zio-redis', apiDocs), undefined);
  assert.equal(apiHrefFor('official', 'zio-http', apiDocs), undefined);
  assert.equal(apiHrefFor('community', 'zio-json', apiDocs), undefined);
});

test('a line with an API link puts it between the docs link and the description', () => {
  assert.equal(
    libraryLine({ label: 'ZIO JSON', href: '/zio-json/', description: 'A fast JSON library.', api: '/zio-json/api/' }),
    '- [ZIO JSON](/zio-json/) · [API](pathname:///zio-json/api/) — A fast JSON library.'
  );
});

test('a line without an API link or a description is unchanged from before', () => {
  assert.equal(
    libraryLine({ label: 'ZIO Redis', href: '/zio-redis/', description: 'A Redis client.' }),
    '- [ZIO Redis](/zio-redis/) — A Redis client.'
  );
  assert.equal(libraryLine({ label: 'Tapir', href: '/ecosystem/community/tapir' }), '- [Tapir](/ecosystem/community/tapir)');
});
