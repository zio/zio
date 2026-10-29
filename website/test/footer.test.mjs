import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const config = require('../docusaurus.config.js');

const credit = config.themeConfig.footer.copyright;

test('the footer carries the copyright, the Apache 2.0 licence link and the build year', () => {
  assert.equal(
    credit.replace(/<[^>]+>/g, ''),
    `Copyright © ${new Date().getFullYear()} ZIO Maintainers · Licensed under the Apache License 2.0 · Built with Docusaurus`,
  );
  assert.ok(credit.includes('<a href="https://github.com/zio/zio/blob/series/2.x/LICENSE"'));
  assert.ok(credit.includes('<a href="https://docusaurus.io/"'));
});
