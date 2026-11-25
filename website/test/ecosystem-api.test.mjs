import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  CORE_ARTIFACTS,
  apiLibraries,
  caseClashes,
  externalMappings,
  githubRepo,
  linkTarget,
  scaladocArgs,
  scaladocVersion,
  stdlibMapping,
} from '../scripts/lib/ecosystem-api.mjs';
import { UX_JS_PATCHES, patchUxJs } from '../scripts/lib/scaladoc-patches.mjs';
import {
  breadcrumbsHtml,
  checkSideMenu,
  folderIndexRedirect,
  librarySite,
  navbarHtml,
  siteLinks,
  themeApiPage,
  vendoredScripts,
} from '../scripts/lib/api-theme.mjs';

const pkg = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

test('every library in zioApiDocs has its docs pinned in package.json, at an exact version', () => {
  const libs = apiLibraries(pkg);
  assert.ok(libs.length > 0);
  for (const lib of libs) assert.equal(lib.version, pkg.dependencies[`@zio.dev/${lib.name}`]);
});

test('a library is built at its docs pin, from its own module unless it lists its modules', () => {
  const config = {
    dependencies: { '@zio.dev/zio-query': '0.7.9', '@zio.dev/zio-schema': '1.9.2' },
    zioApiDocs: { 'zio-query': {}, 'zio-schema': { modules: ['zio-schema', 'zio-schema-json'] } },
  };
  assert.deepEqual(apiLibraries(config), [
    { name: 'zio-query', version: '0.7.9', modules: ['zio-query'] },
    { name: 'zio-schema', version: '1.9.2', modules: ['zio-schema', 'zio-schema-json'] },
  ]);
  assert.throws(() => apiLibraries({ dependencies: {}, zioApiDocs: { 'zio-query': {} } }), /no @zio\.dev\/zio-query/);
  assert.throws(
    () => apiLibraries({ dependencies: { '@zio.dev/zio-query': '^0.7.9' }, zioApiDocs: { 'zio-query': {} } }),
    /exact version/,
  );
  assert.throws(
    () => apiLibraries({ dependencies: { '@zio.dev/zio-query': '0.7.9' }, zioApiDocs: { 'zio-query': { modules: [] } } }),
    /non-empty/,
  );
});

test('the GitHub repository comes from the npm package', () => {
  assert.equal(githubRepo('https://github.com/zio/zio-json'), 'zio/zio-json');
  assert.equal(githubRepo('git+https://github.com/zio/zio-schema.git'), 'zio/zio-schema');
  assert.equal(githubRepo('https://gitlab.com/zio/zio-json'), null);
  assert.equal(githubRepo(undefined), null);
});

test('Scaladoc runs at the newest Scala 3 the classpath was built with', () => {
  const cp = [
    '/c/org/scala-lang/scala3-library_3/3.3.6/scala3-library_3-3.3.6.jar',
    '/c/org/scala-lang/scala3-library_3/3.10.1/scala3-library_3-3.10.1.jar',
    '/c/org/scala-lang/scala3-library_3/3.9.0/scala3-library_3-3.9.0.jar',
    '/c/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar',
  ];
  assert.equal(scaladocVersion(cp), '3.10.1');
  assert.throws(() => scaladocVersion(cp.slice(3)), /no scala3-library_3/);
});

test('types of zio link to /api, of other libraries built here to theirs, of anything else nowhere', () => {
  const libs = [
    { name: 'zio-json', modules: ['zio-json'] },
    { name: 'zio-schema', modules: ['zio-schema', 'zio-schema-json'] },
  ];
  const jar = (a, v = '1.0.0') => `/c/https/repo1.maven.org/maven2/dev/zio/${a}_3/${v}/${a}_3-${v}.jar`;
  assert.ok(CORE_ARTIFACTS.includes('zio-streams'));
  assert.equal(linkTarget(jar('zio-streams', '2.1.27'), libs, 'zio-json'), 'https://zio.dev/api/');
  assert.equal(linkTarget(jar('zio-json'), libs, 'zio-schema'), 'https://zio.dev/zio-json/api/');
  assert.equal(linkTarget(jar('zio-schema-json'), libs, 'zio-json'), 'https://zio.dev/zio-schema/api/');
  assert.equal(linkTarget(jar('zio-json'), libs, 'zio-json'), null, 'not the library itself');
  assert.equal(linkTarget(jar('zio-prelude'), libs, 'zio-json'), null, 'not a library without an API here');
  assert.equal(linkTarget('/c/maven2/org/typelevel/cats-core_3/2.13.0/cats-core_3-2.13.0.jar', libs, 'zio-json'), null);
});

// Scaladoc matches a mapping against all of a path (Java's Pattern.matches).
const target = (mappings, path) => {
  for (const m of mappings.split(',')) {
    const [regex, kind, url] = m.split('::');
    assert.equal(kind, 'scaladoc3');
    if (new RegExp(`^(?:${regex})$`).test(path)) return url;
  }
  return null;
};

test('external mappings send each type to the site of the jar it comes from', () => {
  const core = 'https://zio.dev/api/';
  const json = 'https://zio.dev/zio-json/api/';
  const mappings = externalMappings([
    { url: core, files: ['zio/ZIO.tasty', 'zio/ZIO.class', 'zio/Chunk.tasty', 'zio/stream/ZStream.tasty', 'META-INF/MANIFEST.MF'] },
    // zio-json has one trait in package zio, beside its own package
    { url: json, files: ['zio/JsonPackagePlatformSpecific.tasty', 'zio/json/JsonCodec.tasty', 'zio/json/ast/Json.tasty'] },
  ]);
  // Scaladoc 3.9 matches the TASTy file's path in the jar, 3.3 the class file with the jar's path.
  for (const prefix of ['', '/home/u/.cache/coursier/v1/https/repo1.maven.org/maven2/dev/zio/zio_3/2.1.27/zio_3-2.1.27.jar/']) {
    for (const ext of ['tasty', 'class']) {
      assert.equal(target(mappings, `${prefix}zio/ZIO.${ext}`), core);
      assert.equal(target(mappings, `${prefix}zio/stream/ZStream.${ext}`), core);
      assert.equal(target(mappings, `${prefix}zio/JsonPackagePlatformSpecific.${ext}`), json);
      assert.equal(target(mappings, `${prefix}zio/JsonPackagePlatformSpecific$.${ext}`), json);
      assert.equal(target(mappings, `${prefix}zio/json/ast/Json.${ext}`), json);
    }
  }
  // A package none of the jars has (zio-prelude's), and the library's own types, read from a folder.
  assert.equal(target(mappings, 'zio/prelude/Validation.tasty'), null);
  assert.equal(target(mappings, '/tmp/ecosystem-api-x/zio-json/tasty/zio/json/JsonCodec.tasty'), null);
  assert.equal(target(mappings, '/tmp/ecosystem-api-x/zio-json/tasty/zio/ZIO.tasty'), null);
  // The single types come before the folders, which would take them otherwise.
  assert.match(mappings.split(',')[0], /JsonPackagePlatformSpecific/);
});

test('the Scala standard library links to scala-lang.org at the Scaladoc version', () => {
  const m = stdlibMapping('3.9.0');
  assert.equal(target(m, 'scala/collection/immutable/List.tasty'), 'https://www.scala-lang.org/api/3.9.0/');
  assert.equal(target(m, '/c/scala-library-2.13.16.jar/scala/Option.class'), 'https://www.scala-lang.org/api/3.9.0/');
  assert.equal(target(m, 'zio/ZIO.tasty'), null);
});

test('Scaladoc gets the project, version, source links and mappings', () => {
  const args = scaladocArgs({
    lib: { name: 'zio-json', version: '1.1.3' },
    title: 'ZIO JSON',
    out: '/o',
    classpath: ['/a.jar', '/b.jar'],
    repo: 'zio/zio-json',
    mappings: 'm',
    input: '/in',
  });
  assert.deepEqual(args, [
    'dotty.tools.scaladoc.Main',
    '-d',
    '/o',
    '-classpath',
    '/a.jar:/b.jar',
    '-project',
    'ZIO JSON',
    '-project-version',
    '1.1.3',
    '-dynamic-side-menu',
    '-source-links:github://zio/zio-json/v1.1.3',
    '-social-links:github::https://github.com/zio/zio-json',
    '-external-mappings:m',
    '/in',
  ]);
});

test('pages whose URLs differ only in case are found', () => {
  assert.deepEqual(caseClashes(['zio/json/Foo', 'zio/json/foo', 'zio/json/Bar', 'zio/Json']), [['zio/json/Foo', 'zio/json/foo']]);
  assert.deepEqual(caseClashes(['a/B', 'a/C']), []);
});

test('the ux.js patches apply, and fail loudly once their target is gone', () => {
  const js = UX_JS_PATCHES.map((p) => p.broken).join('\n// …\n');
  const patched = patchUxJs(js);
  for (const { broken, fixed } of UX_JS_PATCHES) {
    assert.ok(patched.includes(fixed));
    if (!fixed.includes(broken)) assert.ok(!patched.includes(broken));
  }
  assert.throws(() => patchUxJs(js.replace('$.get(href', '$.ajax(href')), /no longer contains `\$\.get/);
});

// A library's API: the trail Scaladoc writes for zio-json's zio/json/JsonCodec.html.
const SITE = librarySite({ lib: 'zio-json', title: 'ZIO JSON' });
const PAGE =
  '<!DOCTYPE html><html data-pathToRoot="../../" data-rawLocation="zio/json/JsonCodec" data-dynamicSideMenu="true"><head><meta charset="utf-8"></meta>' +
  '<link rel="shortcut icon" type="image/x-icon" href="../../favicon.ico"></link>' +
  '<script type="text/javascript" src="https://d3js.org/d3.v6.min.js" defer="true"></script>' +
  '<script type="text/javascript" src="https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.2/dist/graphlib-dot.min.js" defer="true"></script>' +
  '<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/dagre-d3/0.6.1/dagre-d3.min.js" defer="true"></script>' +
  '<script type="text/javascript" src="https://scastie.scala-lang.org/embedded.js" defer="true"></script></head><body><div id=""><div id="header" class="body-small">' +
  '<span id="search-toggle"></span></div><div id="mobile-menu"><div class="mobile-menu-header body-small"><span class="mobile-menu-logo">' +
  '<span class="project-name h300">ZIO JSON</span></span><button id="mobile-menu-close" class="icon-button close"></button></div>' +
  '<span id="mobile-theme-toggle" class="mobile-menu-item mode"></span></div>' +
  '<div id="footer" class="body-small"><div class="left-container">Generated with</div><div class="text-mobile"></div></div>' +
  '<span id="mobile-sidebar-toggle" class="floating-button"></span><div id="leftColumn" class="body-small"></div>' +
  '<div id="main"><div class="breadcrumbs container"><a href="../../index.html">ZIO JSON</a>/<a href="../../zio.html">zio</a>/' +
  '<a href="../json.html">zio.json</a>/<a href="JsonCodec.html">JsonCodec</a></div><div id="content" class="body-medium"><div>' +
  '<a href="https://zio.dev/api/zio/Cause$$Fail.html#m">Fail</a><a href="https://zio.dev/zio-schema/api/zio/schema/Schema.html">Schema</a>' +
  '<a href="https://github.com/zio/zio-json/blob/v1.1.3/x.scala">source</a></div></div></div></body></html>';

test("a library's API pages sit under Ecosystem in the navbar, with its own stylesheet", () => {
  assert.match(navbarHtml({ prompt: 'p', site: SITE }), /za-navbar__link--active" href="\/ecosystem\/" aria-current="page">Ecosystem</);
  assert.ok(!navbarHtml({ prompt: 'p', site: SITE }).includes('za-navbar__link--active" href="/api/"'));
  const html = themeApiPage(PAGE, { prompt: 'p', site: SITE });
  assert.ok(html.includes('<link rel="stylesheet" href="/zio-json/api/styles/zio-api.css"></head>'));
  assert.match(html, /za-mobile-link za-mobile-link--active" href="\/ecosystem\/"/);
});

test("a library's API pages load the diagram scripts from its own copy, and nothing from another host", () => {
  const html = themeApiPage(PAGE, { prompt: 'p', site: SITE });
  assert.doesNotMatch(html, /<script[^>]+src="https?:/);
  assert.doesNotMatch(html, /scastie/);
  for (const local of Object.values(vendoredScripts(SITE))) {
    assert.match(local, /^\/zio-json\/api\/scripts\/vendor\//);
    assert.equal(html.split(`src="${local}" defer="true"`).length - 1, 1);
  }
});

test("a library's breadcrumbs lead back to its docs: Home > library > API > packages > page", () => {
  const html = themeApiPage(PAGE, { prompt: 'p', site: SITE });
  const nav = /<nav class="za-breadcrumbs"[^]*?<\/nav>/.exec(html)[0];
  const crumbs = [...nav.matchAll(/href="([^"]*)"><span>([^<]*)<|<span class="za-breadcrumbs__link">([^<]*)</g)].map(
    ([, href, label, current]) => (current ? `[${current}]` : `${label} ${href}`),
  );
  assert.deepEqual(crumbs, ['ZIO JSON /zio-json/', 'API /zio-json/api/', 'zio ../../zio', 'json ../json', '[JsonCodec]']);
  assert.ok(breadcrumbsHtml([{ href: 'index.html', label: 'ZIO JSON' }], SITE).includes('href="/zio-json/"><span>ZIO JSON<'));
});

test('links to other API sites stay on the site, extensionless, and follow their moved pages', () => {
  const moves = new Map([['api/zio/Cause$$Fail', 'api/zio/Cause/Fail']]);
  const html = themeApiPage(PAGE, { prompt: 'p', site: SITE, moves });
  assert.ok(html.includes('<a href="/api/zio/Cause/Fail#m">Fail</a>'));
  assert.ok(html.includes('<a href="/zio-schema/api/zio/schema/Schema">Schema</a>'));
  assert.ok(html.includes('href="https://github.com/zio/zio-json/blob/v1.1.3/x.scala"'), 'other sites keep their links');
  assert.equal(siteLinks('<a href="https://zio.dev/zio-json/api/index.html">'), '<a href="/zio-json/api/">');
  assert.equal(siteLinks('<a href="https://zio.dev/reference/">'), '<a href="https://zio.dev/reference/">');
});

test("a library's API index sends /<lib>/api to /<lib>/api/, and its menu may have any packages at its root", () => {
  assert.ok(folderIndexRedirect('<html><head></head></html>', SITE).includes('<base href="/zio-json/api/">'));
  const pkg = (name) => ({ name, location: name.split('.'), kind: 'package', children: [] });
  const menu = { api: { children: [pkg('zio.schema')] } };
  assert.equal(checkSideMenu(menu, SITE), menu);
  assert.equal(checkSideMenu({ api: { children: [pkg('zio'), pkg('izumi')] } }, SITE).api.children.length, 2);
  assert.throws(() => checkSideMenu({ api: { children: [] } }, SITE), /no packages/);
});
