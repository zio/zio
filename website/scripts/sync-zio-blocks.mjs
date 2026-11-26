#!/usr/bin/env node
// Refreshes ecosystem-blocks.json, the ZIO Blocks blocks that zio.dev/ecosystem lists with the libraries, from the
// "All Blocks" tables of zio-blocks' docs/index.md.
//
//   node scripts/sync-zio-blocks.mjs                       # from zio/zio-blocks on GitHub (main)
//   node scripts/sync-zio-blocks.mjs ../../zio-blocks/docs/index.md
//
// Then run `yarn generate-sidebar` and `node scripts/generate-ecosystem-libraries.js`, which read the snapshot.
import { readFileSync, writeFileSync } from 'node:fs';
import { parseBlocks } from './lib/zio-blocks.mjs';

const DEFAULT_SOURCE = 'https://raw.githubusercontent.com/zio/zio-blocks/main/docs/index.md';
const source = process.argv[2] || DEFAULT_SOURCE;
const website = new URL('..', import.meta.url);

const markdown = /^https?:/.test(source)
  ? await (await fetch(source)).text()
  : readFileSync(source, 'utf8');
const { categories } = JSON.parse(readFileSync(new URL('ecosystem-categories.json', website), 'utf8'));
const blocks = parseBlocks(markdown, categories);

writeFileSync(new URL('ecosystem-blocks.json', website), JSON.stringify({ blocks }, null, 2) + '\n');
console.log(`Wrote ${blocks.length} blocks to ecosystem-blocks.json`);
