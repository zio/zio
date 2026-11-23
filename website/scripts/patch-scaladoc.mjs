// Usage: node scripts/patch-scaladoc.mjs <scaladoc-dir>
// Patches <scaladoc-dir>/scripts/ux.js in place with the fixes of lib/scaladoc-patches.mjs. `sbt apiDocs` runs it on
// website/static/api; scripts/build-ecosystem-api.mjs applies the same fixes to each ecosystem library's API.
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { patchUxJs } from './lib/scaladoc-patches.mjs';

const [dir] = process.argv.slice(2);
if (!dir) {
  console.error('usage: node scripts/patch-scaladoc.mjs <scaladoc-dir>');
  process.exit(2);
}
const uxJs = join(dir, 'scripts', 'ux.js');
await writeFile(uxJs, patchUxJs(await readFile(uxJs, 'utf8')));
console.log(`patch-scaladoc: patched ${uxJs}`);
