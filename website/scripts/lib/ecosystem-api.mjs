// The pure parts of scripts/build-ecosystem-api.mjs, which builds each ecosystem library's API reference (Scaladoc) at
// /<lib>/api from the library's published Scala 3 jars. Kept apart so test/ecosystem-api.test.mjs can check them.

/** Where the site serves pages; the external mappings use it, and theme-api.mjs makes such links root-relative. */
export const SITE = 'https://zio.dev';

// The zio/zio modules documented at /api, as the `unidocProjectFilter` of `apiReference` in build.sbt lists them, by
// their published artifact name. A type of these modules links to /api.
export const CORE_ARTIFACTS = [
  'zio',
  'zio-streams',
  'zio-test',
  'zio-test-sbt',
  'zio-test-junit',
  'zio-test-junit-engine',
  'zio-test-magnolia',
  'zio-test-refined',
  'zio-test-scalacheck',
  'zio-concurrent',
  'zio-managed',
  'zio-macros',
  'zio-stacktracer',
  'zio-internal-macros',
];

/**
 * The libraries to build, from the `zioApiDocs` section of website/package.json: each key names a library whose docs
 * the site already takes from npm (`@zio.dev/<lib>` in `dependencies`), and that pin is the version built. A value may
 * list the library's Maven modules (artifact names without the Scala suffix); by default it is the one module named
 * after the library.
 */
export function apiLibraries(pkg) {
  const config = pkg.zioApiDocs ?? {};
  return Object.entries(config).map(([name, options]) => {
    const version = pkg.dependencies?.[`@zio.dev/${name}`];
    if (!version)
      throw new Error(`zioApiDocs lists ${name}, but dependencies has no @zio.dev/${name} to take its version from`);
    if (!/^\d[\w.+-]*$/.test(version))
      throw new Error(`@zio.dev/${name} is pinned to "${version}"; zioApiDocs needs the exact version Maven has`);
    const modules = options?.modules ?? [name];
    if (!Array.isArray(modules) || modules.length === 0 || !modules.every((m) => typeof m === 'string' && m))
      throw new Error(`zioApiDocs.${name}.modules must be a non-empty list of artifact names`);
    return { name, version, modules };
  });
}

/** `https://github.com/zio/zio-json(.git)`, as npm's `repository.url` has it -> `zio/zio-json`; null for other hosts. */
export function githubRepo(url) {
  const m = /github\.com[/:]([^/]+)\/([^/#?]+?)(?:\.git)?\/?$/.exec(url ?? '');
  return m ? `${m[1]}/${m[2]}` : null;
}

/**
 * A jar of a Coursier classpath, by file name: `<artifact>_3-<version>.jar`. Null for jars that are not Scala 3
 * builds (Java libraries, Scala 2.13 ones).
 */
export function scala3Jar(path) {
  const m = /(?:^|\/)([^/]+?)_3-([^/]+)\.jar$/.exec(path);
  return m ? { artifact: m[1], version: m[2] } : null;
}

const numbers = (v) => v.split(/[.-]/).map((p) => (/^\d+$/.test(p) ? Number(p) : p));

/** Compares two versions such as 3.9.0 and 3.10.1 by their numeric parts. */
export function compareVersions(a, b) {
  const [x, y] = [numbers(a), numbers(b)];
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const [p, q] = [x[i] ?? 0, y[i] ?? 0];
    if (p === q) continue;
    if (typeof p === 'number' && typeof q === 'number') return p - q;
    return String(p) < String(q) ? -1 : 1;
  }
  return 0;
}

/**
 * The Scaladoc to run on a library: the version of the Scala 3 standard library on its classpath, which is the newest
 * Scala the library or its dependencies were built with. An older Scaladoc cannot read newer TASTy, and a newer one
 * than needed can crash (Scaladoc 3.10.1 fails on zio-schema 1.9.2, built with 3.9.0, where 3.9.0 does not).
 */
export function scaladocVersion(classpath) {
  const versions = classpath
    .map((p) => /(?:^|\/)scala3-library_3-([^/]+)\.jar$/.exec(p)?.[1])
    .filter(Boolean)
    .sort(compareVersions);
  if (!versions.length) throw new Error('no scala3-library_3 on the classpath; is this a Scala 3 library?');
  return versions[versions.length - 1];
}

// What Scaladoc matches a mapping against differs by version: Scaladoc 3.3 takes the class file with its jar
// (`/…/zio_3-2.1.27.jar/zio/ZIO.class`), 3.9 the TASTy file's path inside the jar alone (`zio/ZIO.tasty`). Both are
// matched whole, and the library's own types (read from a folder, `/tmp/…/zio/json/Foo.tasty`) by neither, which
// matters because Scaladoc 3.9 links a type of the library itself to the mapping's site when the path matches.
const JAR = '(?:.*\\.jar/)?';
const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Scaladoc's `-external-mappings`, which turn references to types of other libraries into links. Scaladoc matches each
 * regex against the path of the type's file in its jar (see JAR) and links the first mapping that matches, so the
 * mappings are built from the jars themselves: `owners` gives, for each site the links go to, the entries of its jars.
 * A package folder belongs to the site with the most top-level types in it; the types another site has in that folder
 * (zio-json puts one trait in package `zio`) are listed one by one, first.
 */
export function externalMappings(owners) {
  const byDir = new Map(); // folder -> url -> top-level types (TASTy file names)
  for (const { url, files } of owners)
    for (const file of files) {
      if (!file.endsWith('.tasty')) continue;
      const at = file.lastIndexOf('/');
      const type = file.slice(at + 1, -'.tasty'.length);
      const dir = at < 0 ? '' : file.slice(0, at);
      if (!byDir.has(dir)) byDir.set(dir, new Map());
      const urls = byDir.get(dir);
      if (!urls.has(url)) urls.set(url, new Set());
      urls.get(url).add(type);
    }
  const dirs = new Map(); // url -> folders it owns
  const single = []; // [regex, url] for types outside their site's folders
  for (const [dir, urls] of [...byDir].sort(([a], [b]) => a.localeCompare(b))) {
    const [main, ...others] = [...urls].sort(([u1, a], [u2, b]) => b.size - a.size || u1.localeCompare(u2));
    if (!dirs.has(main[0])) dirs.set(main[0], []);
    dirs.get(main[0]).push(dir);
    for (const [url, names] of others)
      for (const name of [...names].sort())
        single.push([`${JAR}${escapeRegex(dir ? `${dir}/${name}` : name)}\\$?\\.(?:class|tasty)$`, url]);
  }
  const folders = [...dirs].map(([url, ds]) => {
    const alternatives = ds.map((d) => (d ? `${escapeRegex(d)}/` : ''));
    return [`${JAR}(?:${alternatives.join('|')})[^/]*$`, url];
  });
  const mappings = [...single, ...folders].map(([regex, url]) => {
    if (regex.includes(',')) throw new Error(`external mapping ${regex} has a comma, which separates mappings`);
    return `${regex}::scaladoc3::${url}`;
  });
  return mappings.join(',');
}

/**
 * The mapping for the Scala standard library, to its API on scala-lang.org at the Scaladoc's version (sbt's
 * apiMappings give zio's /api the same links). Last: no other mapping covers package scala.
 */
export const stdlibMapping = (scaladoc) =>
  `${JAR}scala/.*::scaladoc3::https://www.scala-lang.org/api/${scaladoc}/`;

/**
 * The site a jar's types link to, or null: /api for the zio/zio modules, /<lib>/api for a module of a library in
 * `libraries` other than `self`.
 */
export function linkTarget(jar, libraries, self) {
  const art = scala3Jar(jar);
  if (!art || !/\/dev\/zio\//.test(jar)) return null;
  if (CORE_ARTIFACTS.includes(art.artifact)) return `${SITE}/api/`;
  const lib = libraries.find((l) => l.name !== self && l.modules.includes(art.artifact));
  return lib ? `${SITE}/${lib.name}/api/` : null;
}

/** The Scaladoc command line (after `java -cp <scaladoc classpath>`) for one library. */
export function scaladocArgs({ lib, title, out, classpath, repo, mappings, input }) {
  const args = ['dotty.tools.scaladoc.Main', '-d', out, '-classpath', classpath.join(':')];
  args.push('-project', title, '-project-version', lib.version, '-dynamic-side-menu');
  if (repo)
    args.push(`-source-links:github://${repo}/v${lib.version}`, `-social-links:github::https://github.com/${repo}`);
  if (mappings) args.push(`-external-mappings:${mappings}`);
  args.push(input);
  return args;
}

/**
 * Pages whose URLs differ only in letter case (`Foo` and `foo`). zio.dev's host matches paths without regard to case,
 * so it serves only one of them. Returns each such group, as lists of the paths given.
 */
export function caseClashes(paths) {
  const byLower = new Map();
  for (const p of paths) byLower.set(p.toLowerCase(), [...(byLower.get(p.toLowerCase()) ?? []), p]);
  return [...byLower.values()].filter((group) => group.length > 1);
}

// Display names that can't be derived from the package name by title-casing (as plugins/zio-ecosystem-docusaurus
// names the projects in the sidebars).
const PROJECT_NAMES = {
  'zio-amqp': 'ZIO AMQP',
  'zio-aws': 'ZIO AWS',
  'zio-bson': 'ZIO BSON',
  'zio-json': 'ZIO JSON',
  'zio-rocksdb': 'ZIO Rocksdb',
  'zio-s3': 'ZIO S3',
  'zio-sbt': 'ZIO SBT',
  'zio-sqs': 'ZIO SQS',
};

/** "zio-redis" -> "ZIO Redis". */
export function projectName(project) {
  return (
    PROJECT_NAMES[project] ??
    project
      .split('-')
      .map((word) => (word === 'zio' ? 'ZIO' : word.charAt(0).toUpperCase() + word.slice(1)))
      .join(' ')
  );
}
