// Gives the Scaladoc pages in build/api the site's navbar, favicon, fonts, colour mode and side menu (see
// lib/api-theme.mjs). Runs after `docusaurus build`. Each file is read from static/api, as `sbt apiDocs` wrote it, and
// written over its copy in build/api, so running this again is safe. Without static/api there is nothing to do.
//
// The ecosystem libraries' API references, static/<lib>/api (scripts/build-ecosystem-api.mjs), get the same treatment
// in build/<lib>/api, under the Ecosystem navbar item and with a breadcrumb back to the library's docs. Links from one
// API site to another follow the nested types each site moves into folders.
import { cp, copyFile, mkdir, readdir, readFile, rm, rmdir, stat, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CORE_SITE,
  checkSideMenu,
  extensionlessSearchData,
  extensionlessUxJs,
  folderIndexRedirect,
  librarySite,
  nestedTypePaths,
  relocatePage,
  relocateSideMenu,
  themeApiPage,
} from './lib/api-theme.mjs';
import { PROMPT } from '../src/components/ui/OnboardAgentButton/prompt.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const exists = (p) => stat(p).then(() => true, () => false);

async function* htmlFiles(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) yield* htmlFiles(path);
    else if (entry.name.endsWith('.html')) yield path;
  }
}

/** The API sites to skin: zio's /api, then each ecosystem library's /<lib>/api, as they are in static/ and build/. */
async function apiSites() {
  const sites = [];
  const add = async (path, site) => {
    const sourceDir = join(root, 'static', path);
    const apiDir = join(root, 'build', path);
    if ((await exists(sourceDir)) && (await exists(apiDir))) sites.push({ site, path, sourceDir, apiDir });
  };
  if (await exists(join(root, 'static', 'api'))) await add('api', CORE_SITE);
  else console.log('theme-api: no static/api (run `sbt apiDocs` first to publish the API reference), skipped');
  for (const entry of await readdir(join(root, 'static'), { withFileTypes: true })) {
    const info = join(root, 'static', entry.name, 'api', 'zio-api.json');
    if (entry.isDirectory() && (await exists(info)))
      await add(`${entry.name}/api`, librarySite(JSON.parse(await readFile(info, 'utf8'))));
  }
  return sites;
}

const sites = await apiSites();
// Every site's menu and nested-type moves first, so a page can link to another site's moved pages.
const allMoves = new Map(); // `<site path>/<page>` -> `<site path>/<new page>`, for links between sites
for (const s of sites) {
  s.menu = checkSideMenu(JSON.parse(await readFile(join(s.sourceDir, 'dynamicSideMenu.json'), 'utf8')), s.site);
  // Nested types move into folders named after their parents (Cause$$Fail -> Cause/Fail).
  s.moves = nestedTypePaths(s.menu);
  for (const [from, to] of s.moves) allMoves.set(`${s.path}/${from}`, `${s.path}/${to}`);
}

for (const { site, path, sourceDir, apiDir, menu, moves } of sites) {
  let pages = 0;
  for await (const file of htmlFiles(sourceDir)) {
    const from = relative(sourceDir, file).replace(/\.html$/, '');
    const to = moves.get(from) ?? from;
    const page = relocatePage(await readFile(file, 'utf8'), { from, to, moves });
    await mkdir(dirname(join(apiDir, `${to}.html`)), { recursive: true });
    const themed = themeApiPage(page, { prompt: PROMPT, site, moves: allMoves });
    await writeFile(join(apiDir, `${to}.html`), to === 'index' ? folderIndexRedirect(themed, site) : themed);
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
  await cp(join(root, 'src', 'api-theme', 'vendor'), join(apiDir, 'scripts', 'vendor'), { recursive: true });
  await copyFile(join(root, 'src', 'api-theme', 'theme.js'), join(apiDir, 'scripts', 'theme.js'));
  console.log(
    `theme-api: gave ${pages} pages of /${path}/ the site navbar, favicon and theme (${moves.size} nested types in folders)`,
  );
}
