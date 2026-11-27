import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { blockUrl, parseBlocks, referenceAnchor } from '../scripts/lib/zio-blocks.mjs';

const json = (file) => JSON.parse(readFileSync(new URL(`../${file}`, import.meta.url), 'utf8'));
const { categories } = json('ecosystem-categories.json');
const ids = new Set(categories.map((c) => c.id));

const INDEX = `# ZIO Blocks

## All Blocks

### Web & APIs

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [JWT](./reference/jwt.md) | \`zio-blocks-jwt\` | JVM · JS | 3.x | Zero-dependency JWT signing |

### Types & Abstractions

| Block | Artifact | Platform | Scala | Description |
|-------|----------|----------|-------|-------------|
| [Chunk](./reference/chunk.md) | \`zio-blocks-chunk\` | JVM · JS | 2.13 (JVM) · 3.x | Immutable indexed sequences |
| [RingBuffer](./reference/ringbuffer/index.mdx) | \`zio-blocks-ringbuffer\` | JVM | 3.x | Lock-free bounded ring buffers |

---

## Schema
`;

test('the blocks of docs/index.md take the category of their ### heading', () => {
  const blocks = parseBlocks(INDEX, categories);
  assert.deepEqual(blocks.map((b) => [b.name, b.category]), [['JWT', 'web'], ['Chunk', 'types'], ['RingBuffer', 'types']]);
  assert.deepEqual(blocks[1], {
    name: 'Chunk',
    href: 'https://zioblocks.com/reference/chunk/',
    artifact: 'zio-blocks-chunk',
    platforms: ['JVM', 'JS'],
    scala: '2.13 (JVM) · 3.x',
    description: 'Immutable indexed sequences',
    category: 'types',
  });
});

test('a docs/index.md heading that is not a shared category fails', () => {
  assert.throws(() => parseBlocks(INDEX.replace('### Web & APIs', '### Web & HTTP'), categories), /"Web & HTTP" is not a category/);
});

test('block links become zioblocks.com pages', () => {
  assert.equal(blockUrl('./reference/schema/index.md'), 'https://zioblocks.com/reference/schema/');
  assert.equal(blockUrl('./reference/mux.mdx'), 'https://zioblocks.com/reference/mux/');
  assert.equal(blockUrl('./reference/schema/built-in-codecs/avro.md'), 'https://zioblocks.com/reference/schema/built-in-codecs/avro/');
});

test('category anchors follow groupId of zioblocks.com/reference', () => {
  assert.equal(referenceAnchor('Web & APIs'), 'ref-web-apis');
  assert.equal(referenceAnchor('Concurrency & Resilience'), 'ref-concurrency-resilience');
});

test('the sidebar plugin computes the same anchors as scripts/lib/zio-blocks.mjs', () => {
  for (const file of ['index.js', 'index.ts']) {
    const source = readFileSync(new URL(`../plugins/zio-ecosystem-docusaurus/${file}`, import.meta.url), 'utf8');
    const body = source.match(/function referenceAnchor\(label(?:: string)?\)(?:: string)? \{\n\s*return (.+?);?\n\}/)[1];
    const pluginAnchor = new Function('label', `return ${body}`);
    for (const { label } of categories) assert.equal(pluginAnchor(label), referenceAnchor(label), `${file}: ${label}`);
  }
});

test('category ids and labels are unique', () => {
  assert.equal(ids.size, categories.length);
  assert.equal(new Set(categories.map((c) => c.label)).size, categories.length);
});

test('every ZIO Blocks block is in a shared category', () => {
  const { blocks } = json('ecosystem-blocks.json');
  assert.ok(blocks.length > 0);
  for (const block of blocks) assert.ok(ids.has(block.category), `${block.name}: ${block.category}`);
});

test('the cards list every library, block and compatible library, in known categories', () => {
  const { categories: shown, cards } = json('src/components/EcosystemCards/ecosystem.json');
  const libraries = categories.reduce((n, c) => n + c.official.length + (c.external || []).length + c.community.length, 0);
  assert.equal(cards.filter((c) => !c.family && c.kind !== 'compatible').length, libraries);
  assert.equal(cards.filter((c) => c.family === 'zio-blocks').length, json('ecosystem-blocks.json').blocks.length);
  assert.equal(cards.filter((c) => c.kind === 'compatible').length, json('ecosystem-compatible.json').compatible.length);
  for (const card of cards) {
    assert.ok(card.category === 'compatible' || ids.has(card.category), `${card.name}: ${card.category}`);
    assert.ok(card.description, `${card.name} has no description`);
  }
  for (const { id, label } of shown) assert.equal(label, categories.find((c) => c.id === id).label);
});
