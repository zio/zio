// Builds the API reference (Scaladoc) of the ecosystem libraries listed under `zioApiDocs` in package.json into
// static/<lib>/api, which `yarn build` publishes at zio.dev/<lib>/api/ and scripts/theme-api.mjs gives the site's look.
//
// Usage: node scripts/build-ecosystem-api.mjs [<lib> ...]     (default: every library in zioApiDocs)
//
// Each library is built from its published Scala 3 jars, at the version package.json pins for its docs
// (`@zio.dev/<lib>`): Coursier (`cs`) fetches the jars, Scaladoc reads the TASTy files in them (they carry the doc
// comments) and writes the pages. Types of zio (/api) and of the other libraries built here become links to their
// pages. Needs `cs`, `java` and `unzip` on the PATH.
//
// A built library is kept in the cache folder (ECOSYSTEM_API_CACHE, default .cache/ecosystem-api) under its version,
// the Scaladoc version and a hash of its options; a pinned version never changes, so later runs copy it from there.
//
// A library that fails is reported and skipped, so one broken library never stops the site from deploying; the
// sidebar links only the APIs that exist. Set ECOSYSTEM_API_STRICT=1 to fail instead (exit code 1).
import { execFile as execFileCb } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cp, mkdir, mkdtemp, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { nestedTypePaths } from './lib/api-theme.mjs';
import {
  apiLibraries,
  caseClashes,
  externalMappings,
  githubRepo,
  linkTarget,
  projectName,
  scala3Jar,
  scaladocArgs,
  scaladocVersion,
  stdlibMapping,
} from './lib/ecosystem-api.mjs';
import { patchUxJs } from './lib/scaladoc-patches.mjs';

const execFile = promisify(execFileCb);
const root = fileURLToPath(new URL('..', import.meta.url));
const cacheDir = process.env.ECOSYSTEM_API_CACHE ?? join(root, '.cache', 'ecosystem-api');
const strict = process.env.ECOSYSTEM_API_STRICT === '1';
const javaOpts = (process.env.SCALADOC_JAVA_OPTS ?? '-Xmx3g').split(/\s+/).filter(Boolean);
const exists = (p) => stat(p).then(() => true, () => false);
const run = async (cmd, args) => (await execFile(cmd, args, { maxBuffer: 64 * 1024 * 1024 })).stdout;

/** The jars of the given artifacts and their dependencies, as Coursier fetches them (it retries flaky downloads). */
async function fetchClasspath(coordinates) {
  for (let attempt = 1; ; attempt++) {
    try {
      const jars = (await run('cs', ['fetch', ...coordinates])).split('\n').filter((l) => l.endsWith('.jar'));
      if (jars.length) return jars;
      throw new Error(`cs fetch ${coordinates.join(' ')} returned no jars`);
    } catch (e) {
      if (attempt >= 3) throw e;
      console.log(`  cs fetch failed (${e.message.split('\n')[0]}), retrying`);
    }
  }
}

const jarEntries = async (jar) => (await run('unzip', ['-Z1', jar])).split('\n').filter(Boolean);

/** The pages of a Scaladoc site, as paths relative to it without `.html`. */
async function htmlPages(dir) {
  return (await readdir(dir, { withFileTypes: true, recursive: true }))
    .filter((e) => e.isFile() && e.name.endsWith('.html'))
    .map((e) => relative(dir, join(e.parentPath, e.name)).replace(/\.html$/, ''));
}
const countPages = async (dir) => (await htmlPages(dir)).length;

const pkg = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'));
const all = apiLibraries(pkg);
const only = process.argv.slice(2);
const unknown = only.filter((name) => !all.some((l) => l.name === name));
if (unknown.length) throw new Error(`not in zioApiDocs of package.json: ${unknown.join(', ')}`);
const libraries = only.length ? all.filter((l) => only.includes(l.name)) : all;

const work = await mkdtemp(join(tmpdir(), 'ecosystem-api-'));
const failed = [];
const used = new Set(); // the cache entries of this run
try {
  for (const lib of libraries) {
    const started = Date.now();
    const target = join(root, 'static', lib.name, 'api');
    console.log(`${lib.name} ${lib.version}: ${lib.modules.join(', ')}`);
    try {
      const coordinates = lib.modules.map((m) => `dev.zio:${m}_3:${lib.version}`);
      const classpath = await fetchClasspath(coordinates);
      const own = lib.modules.map((m) => {
        const jar = classpath.find((p) => {
          const art = scala3Jar(p);
          return art && art.artifact === m && art.version === lib.version;
        });
        if (!jar) throw new Error(`no ${m}_3-${lib.version}.jar among the jars Coursier fetched`);
        return jar;
      });

      // Links to the types of zio and of the other libraries built here, from the files of their jars.
      const owners = new Map();
      for (const jar of classpath) {
        const url = !own.includes(jar) && linkTarget(jar, all, lib.name);
        if (!url) continue;
        owners.set(url, [...(owners.get(url) ?? []), ...(await jarEntries(jar))]);
      }

      const repoUrl = await readFile(join(root, 'node_modules', '@zio.dev', lib.name, 'package.json'), 'utf8')
        .then((s) => JSON.parse(s).repository?.url)
        .catch(() => undefined);
      const repo = githubRepo(repoUrl) ?? `zio/${lib.name}`;
      const scaladoc = scaladocVersion(classpath);
      const ecosystem = externalMappings([...owners].map(([url, files]) => ({ url, files })));
      const mappings = [ecosystem, stdlibMapping(scaladoc)].filter(Boolean).join(',');
      const title = projectName(lib.name);

      const options = { lib, title, repo, mappings, scaladoc, script: 3 };
      const key = createHash('sha256').update(JSON.stringify(options)).digest('hex').slice(0, 12);
      const cached = join(cacheDir, `${lib.name}@${lib.version}@scaladoc-${scaladoc}@${key}`);
      used.add(relative(cacheDir, cached));
      if (await exists(join(cached, 'index.html'))) {
        console.log(`  cached in ${relative(root, cached)}`);
      } else {
        // Scaladoc takes the TASTy files from folders: given a jar, it silently writes no pages.
        const input = join(work, lib.name, 'tasty');
        await mkdir(input, { recursive: true });
        for (const jar of own) await run('unzip', ['-q', '-o', jar, '-x', 'META-INF/*', '-d', input]);
        const out = join(work, lib.name, 'out');
        await mkdir(out, { recursive: true });
        const scaladocCp = await fetchClasspath([`org.scala-lang:scaladoc_3:${scaladoc}`]);
        console.log(`  Scaladoc ${scaladoc}`);
        const args = scaladocArgs({ lib, title, out, classpath, repo, mappings, input });
        const { stdout, stderr } = await execFile('java', [...javaOpts, '-cp', scaladocCp.join(':'), ...args], {
          maxBuffer: 256 * 1024 * 1024,
        });
        const log = stdout + stderr;
        const errors = (log.match(/^\[error\]|unhandled exception/gm) ?? []).length;
        if (errors) console.log(`  Scaladoc reported ${errors} errors:\n${log.split('\n').slice(0, 20).join('\n')}`);
        const pages = await countPages(out);
        if (pages < 2) throw new Error(`Scaladoc wrote ${pages} pages:\n${log}`);
        // The pages as the site serves them, nested types in folders (theme-api.mjs moves them there).
        const moves = nestedTypePaths(JSON.parse(await readFile(join(out, 'dynamicSideMenu.json'), 'utf8')));
        const served = (await htmlPages(out)).map((p) => moves.get(p) ?? p);
        const clashes = caseClashes(served).map((c) => c.join(' / '));
        if (clashes.length)
          throw new Error(`pages whose URLs differ only in case, of which zio.dev serves one: ${clashes.join(', ')}`);
        const uxJs = join(out, 'scripts', 'ux.js');
        await writeFile(uxJs, patchUxJs(await readFile(uxJs, 'utf8')));
        // What theme-api.mjs needs to know about the library: its name and where its docs are.
        await writeFile(
          join(out, 'zio-api.json'),
          JSON.stringify({ lib: lib.name, title, version: lib.version, docs: `/${lib.name}/` }),
        );
        await rm(cached, { recursive: true, force: true });
        await mkdir(cacheDir, { recursive: true });
        await cp(out, cached, { recursive: true });
      }
      await rm(target, { recursive: true, force: true });
      await cp(cached, target, { recursive: true });
      const seconds = Math.round((Date.now() - started) / 1000);
      console.log(`  ${await countPages(target)} pages in ${relative(root, target)} (${seconds}s)`);
    } catch (e) {
      failed.push(lib.name);
      // A failed build leaves no pages behind, so the sidebar does not link a stale or partial API.
      await rm(target, { recursive: true, force: true });
      const message = `API reference of ${lib.name} ${lib.version} not built: ${e.message}`;
      console.log(process.env.GITHUB_ACTIONS ? `::warning::${message.split('\n')[0]}` : message);
      if (process.env.GITHUB_ACTIONS) console.log(e.message);
    }
  }
} finally {
  await rm(work, { recursive: true, force: true });
}
// A run over every library drops the cache entries it did not use (older versions), so the cache does not grow.
if (!only.length && (await exists(cacheDir)))
  for (const entry of await readdir(cacheDir))
    if (!used.has(entry)) {
      console.log(`  dropped ${entry} from the cache`);
      await rm(join(cacheDir, entry), { recursive: true, force: true });
    }
console.log(`build-ecosystem-api: built ${libraries.length - failed.length} of ${libraries.length} libraries`);
if (failed.length && strict) process.exit(1);
