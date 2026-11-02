// Gives the Scaladoc pages in build/api the site's navbar, favicon, fonts, colour mode and side menu (see
// lib/api-theme.mjs). Runs after `docusaurus build`. Each file is read from static/api, as `sbt apiDocs` wrote it, and
// written over its copy in build/api, so running this again is safe. Without static/api there is nothing to do.
import { copyFile, mkdir, readdir, readFile, rm, rmdir, stat, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  checkSideMenu,
  extensionlessSearchData,
  extensionlessUxJs,
  folderIndexRedirect,
  nestedTypePaths,
  relocatePage,
  relocateSideMenu,
  themeApiPage,
} from './lib/api-theme.mjs';
import { PROMPT } from '../src/components/ui/OnboardAgentButton/prompt.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const sourceDir = join(root, 'static', 'api');
const apiDir = join(root, 'build', 'api');

const exists = (p) => stat(p).then(() => true, () => false);

async function* htmlFiles(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) yield* htmlFiles(path);
    else if (entry.name.endsWith('.html')) yield path;
  }
}

if (!(await exists(sourceDir)) || !(await exists(apiDir))) {
  console.log('theme-api: no static/api (run `sbt apiDocs` first to publish the API reference), skipped');
} else {
  const menu = checkSideMenu(JSON.parse(await readFile(join(sourceDir, 'dynamicSideMenu.json'), 'utf8')));
  // Nested types move into folders named after their parents (Cause$$Fail -> Cause/Fail).
  const moves = nestedTypePaths(menu);
  let pages = 0;
  for await (const file of htmlFiles(sourceDir)) {
    const from = relative(sourceDir, file).replace(/\.html$/, '');
    const to = moves.get(from) ?? from;
    const page = relocatePage(await readFile(file, 'utf8'), { from, to, moves });
    await mkdir(dirname(join(apiDir, `${to}.html`)), { recursive: true });
    const themed = themeApiPage(page, { prompt: PROMPT });
    await writeFile(join(apiDir, `${to}.html`), to === 'index' ? folderIndexRedirect(themed) : themed);
    if (to !== from) {
      // Docusaurus copied the page to its old place; only the new one is published. Drop folders left empty.
      await rm(join(apiDir, `${from}.html`), { force: true });
      await rmdir(dirname(join(apiDir, `${from}.html`))).catch(() => {});
    }
    pages++;
  }
  await writeFile(join(apiDir, 'dynamicSideMenu.json'), JSON.stringify(relocateSideMenu(menu, moves)));
  for (const [file, rewrite] of [
    ['scripts/ux.js', extensionlessUxJs],
    ['scripts/searchData.js', (js) => extensionlessSearchData(js, moves)],
  ])
    await writeFile(join(apiDir, file), rewrite(await readFile(join(sourceDir, file), 'utf8')));
  await copyFile(join(root, 'src', 'api-theme', 'api.css'), join(apiDir, 'styles', 'zio-api.css'));
  await copyFile(join(root, 'src', 'api-theme', 'theme.js'), join(apiDir, 'scripts', 'theme.js'));
  console.log(`theme-api: gave ${pages} API pages the site navbar, favicon and theme (${moves.size} nested types in folders)`);
}
