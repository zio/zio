import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { apiHrefFor } = require('../scripts/lib/ecosystem-list.js');

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
