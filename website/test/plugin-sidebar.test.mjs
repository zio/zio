import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { projectSidebar } = require('../plugins/zio-ecosystem-docusaurus/index.js');

const category = {
  type: 'category',
  label: 'ZIO Prelude',
  link: { type: 'doc', id: 'zio-prelude/index' },
  items: ['zio-prelude/index', 'zio-prelude/zpure'],
};

test('the API Reference entry comes right after the back link, above the library pages', () => {
  const sidebar = projectSidebar('zio-prelude', category, 'fp', true);
  assert.equal(sidebar.length, 3);
  assert.deepEqual(sidebar[0], { type: 'link', label: '← ZIO Ecosystem', href: '/ecosystem/?eco-category=fp' });
  assert.equal(sidebar[1].type, 'html');
  assert.equal(sidebar[1].className, 'menu__list-item eco-api-reference');
  assert.equal(sidebar[1].value, '<a class="menu__link" href="/zio-prelude/api/">API Reference</a>');
  assert.equal(sidebar[2].label, 'ZIO Prelude');
});

test('the API Reference link ends with a slash', () => {
  const [, api] = projectSidebar('zio-json', category, 'serialization', true);
  assert.match(api.value, /href="\/zio-json\/api\/"/);
});

test('no API entry and no divider when the API was not built', () => {
  const sidebar = projectSidebar('zio-prelude', category, 'fp', false);
  assert.equal(sidebar.length, 2);
  assert.ok(sidebar.every((item) => item.type !== 'html'));
});

test('the library category stays open and drops its own page from its items', () => {
  const [, , cat] = projectSidebar('zio-prelude', category, 'fp', true);
  assert.equal(cat.collapsible, false);
  assert.equal(cat.collapsed, false);
  assert.deepEqual(cat.items, ['zio-prelude/zpure']);
});
