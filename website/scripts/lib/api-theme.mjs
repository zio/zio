import { posix } from 'node:path';

// Re-skins the Scaladoc pages under /api so they share the site's navbar, fonts and colour mode. Scaladoc is a separate
// generator: `sbt apiDocs` copies its HTML into static/api, and Docusaurus publishes those files untouched, outside its
// layout. scripts/theme-api.mjs applies `themeApiPage` to every page of build/api after `docusaurus build`.

// Icons, copied from the Docusaurus navbar so both bars draw the same marks.
const ICONS = {
  menu: '<svg width="30" height="30" viewBox="0 0 30 30" aria-hidden="true"><path stroke="currentColor" stroke-linecap="round" stroke-miterlimit="10" stroke-width="2" d="M4 7h22M4 15h22M4 23h22"/></svg>',
  // The GitHub mark src/css/custom.css draws for .header-github-link
  github:
    '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/></svg>',
  // DocSearch's search icon, as the docs navbar's search button draws it
  search:
    '<svg width="20" height="20" class="za-search__icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="8" stroke="currentColor" fill="none" stroke-width="1.4"/><path d="m21 21-4.3-4.3" stroke="currentColor" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  // react-icons' FaTerminal and FaCheck (Font Awesome 6), as src/components/ui/OnboardAgentButton/NavbarButton.jsx draws them
  terminal:
    '<svg class="za-onboard__idle" viewBox="0 0 576 512" width="1em" height="1em" aria-hidden="true"><path fill="currentColor" d="M9.4 86.6C-3.1 74.1-3.1 53.9 9.4 41.4s32.8-12.5 45.3 0l192 192c12.5 12.5 12.5 32.8 0 45.3l-192 192c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3L178.7 256 9.4 86.6zM256 416l288 0c17.7 0 32 14.3 32 32s-14.3 32-32 32l-288 0c-17.7 0-32-14.3-32-32s14.3-32 32-32z"/></svg>',
  check:
    '<svg class="za-onboard__done" viewBox="0 0 448 512" width="1em" height="1em" aria-hidden="true"><path fill="currentColor" d="M438.6 105.4c12.5 12.5 12.5 32.8 0 45.3l-256 256c-12.5 12.5-32.8 12.5-45.3 0l-128-128c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0L160 338.7 393.4 105.4c12.5-12.5 32.8-12.5 45.3 0z"/></svg>',
  light:
    '<svg class="za-mode__light" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M12,9c1.65,0,3,1.35,3,3s-1.35,3-3,3s-3-1.35-3-3S10.35,9,12,9 M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5s5-2.24,5-5 S14.76,7,12,7L12,7z M2,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S1.45,13,2,13z M20,13l2,0c0.55,0,1-0.45,1-1 s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S19.45,13,20,13z M11,2v2c0,0.55,0.45,1,1,1s1-0.45,1-1V2c0-0.55-0.45-1-1-1S11,1.45,11,2z M11,20v2c0,0.55,0.45,1,1,1s1-0.45,1-1v-2c0-0.55-0.45-1-1-1C11.45,19,11,19.45,11,20z M5.99,4.58c-0.39-0.39-1.03-0.39-1.41,0 c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0s0.39-1.03,0-1.41L5.99,4.58z M18.36,16.95 c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0c0.39-0.39,0.39-1.03,0-1.41 L18.36,16.95z M19.42,5.99c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41 s1.03,0.39,1.41,0L19.42,5.99z M7.05,18.36c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06 c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L7.05,18.36z"/></svg>',
  dark: '<svg class="za-mode__dark" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="M9.37,5.51C9.19,6.15,9.1,6.82,9.1,7.5c0,4.08,3.32,7.4,7.4,7.4c0.68,0,1.35-0.09,1.99-0.27C17.45,17.19,14.93,19,12,19 c-3.86,0-7-3.14-7-7C5,9.07,6.81,6.55,9.37,5.51z M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9s9-4.03,9-9c0-0.46-0.04-0.92-0.1-1.36 c-0.98,1.37-2.58,2.26-4.4,2.26c-2.98,0-5.4-2.42-5.4-5.4c0-1.81,0.89-3.42,2.26-4.4C12.92,3.04,12.46,3,12,3L12,3z"/></svg>',
};

// The navbar items of docusaurus.config.js, in the same order and on the same side, with API as the current page. The
// version dropdown is left out: the API reference is built from the current sources, so it has one version only.
export const NAV_LINKS = [
  { label: 'Overview', href: '/overview/getting-started', position: 'left' },
  { label: 'Reference', href: '/reference/', position: 'left' },
  { label: 'API', href: '/api/', position: 'left', current: true },
  { label: 'Guides', href: '/guides/', position: 'left' },
  { label: 'Ecosystem', href: '/ecosystem/', position: 'left' },
  { label: 'Resources', href: '/resources/', position: 'left' },
  { label: 'Events', href: '/events/', position: 'left' },
  { label: 'Chat Bot', href: 'http://chat.zio.dev', position: 'right', newTab: true },
  { label: 'Blog', href: '/blog', position: 'right' },
  // An icon only (src/css/custom.css draws .header-github-link as the GitHub mark), named by its aria-label.
  { label: 'GitHub repository', href: 'https://github.com/zio/zio', position: 'right', newTab: true, icon: 'github' },
];

// The navbar logo of docusaurus.config.js: one image for both colour modes, and no title beside it.
export const LOGO = { alt: 'ZIO', src: '/img/navbar_brand.png' };

const escapeAttr = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');

function linkHtml({ label, href, current, newTab, icon }, cls) {
  const attrs = [`class="${cls}${current ? ` ${cls}--active` : ''}${icon ? ` ${cls}--${icon}` : ''}"`, `href="${href}"`];
  if (current) attrs.push('aria-current="page"');
  if (newTab) attrs.push('target="_blank" rel="noopener noreferrer"');
  if (icon) attrs.push(`aria-label="${label}"`);
  return `<a ${attrs.join(' ')}>${icon ? ICONS[icon] : label}</a>`;
}

const brandHtml = (cls) => `<a class="${cls}" href="/"><img class="za-logo" src="${LOGO.src}" alt="${LOGO.alt}" height="32"></a>`;

/**
 * The site navbar, built from Scaladoc's own hooks so its scripts keep working: `#search-toggle` opens the search bar,
 * `#mobile-menu-toggle` opens the mobile menu, and the version dropdown it wires at startup stays in the page, hidden
 * (its script throws without it).
 */
export function navbarHtml({ prompt }) {
  const links = (position) =>
    NAV_LINKS.filter((l) => l.position === position)
      .map((l) => linkHtml(l, 'za-navbar__link'))
      .join('');
  return (
    '<div id="header" class="za-navbar">' +
    '<div class="za-navbar__left">' +
    `<span id="mobile-menu-toggle" class="za-navbar__toggle" role="button" tabindex="0" aria-label="Toggle navigation bar">${ICONS.menu}</span>` +
    brandHtml('za-navbar__brand') +
    '<span id="dropdown-trigger" hidden></span><div id="version-dropdown" hidden></div>' +
    links('left') +
    '</div>' +
    '<div class="za-navbar__right">' +
    links('right') +
    `<button type="button" class="za-navbar__icon za-onboard" data-onboard-prompt="${escapeAttr(prompt)}" title="Copy the prompt to onboard your coding agent to ZIO" aria-label="Copy the ZIO agent onboarding prompt to the clipboard">${ICONS.terminal}${ICONS.check}</button>` +
    `<button type="button" class="za-navbar__icon za-mode" data-color-mode-toggle title="light mode" aria-label="Switch between dark and light mode (currently light mode)">${ICONS.light}${ICONS.dark}</button>` +
    // Drawn as the docs' search pill (DocSearch's button); it opens Scaladoc's search, which only searches the API.
    `<button type="button" id="search-toggle" class="za-search" title="Search the API" aria-label="Search the API">${ICONS.search}<span class="za-search__label">Search</span></button>` +
    '</div>' +
    '</div>'
  );
}

const HEADER = /<div id="header"[\s\S]*?(?=<div id="mobile-menu">)/;
const MOBILE_LOGO = /<span class="mobile-menu-logo">[\s\S]*?(?=<button id="mobile-menu-close")/;
const MOBILE_THEME = '<span id="mobile-theme-toggle"';
export const STYLESHEET = '/api/styles/zio-api.css';
// The site font, Inter, as plugins/google-fonts adds it to every docs page.
export const FONTS = 'https://fonts.googleapis.com/css2?family=Inter:wght@400..900&display=fallback';
const FONT_LINKS =
  '<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>' +
  `<link rel="stylesheet" href="${FONTS}">`;
// docusaurus.config.js `favicon`, which Docusaurus serves from static/.
export const FAVICON = '/img/favicon.png';
const SCALADOC_FAVICON = /<link rel="shortcut icon"[^>]*>(?:<\/link>)?/;

function replaceOnce(html, pattern, replacement, what) {
  if (!(typeof pattern === 'string' ? html.includes(pattern) : pattern.test(html)))
    throw new Error(`Scaladoc page has no ${what}; did a Scaladoc upgrade change its markup?`);
  return html.replace(pattern, replacement);
}

const HOME_ICON =
  '<svg viewBox="0 0 24 24" class="za-breadcrumbs__home"><path d="M10 19v-5h4v5c0 .55.45 1 1 1h3c.55 0 1-.45 1-1v-7h1.7c.46 0 .68-.57.33-.87L12.67 3.6c-.38-.34-.96-.34-1.34 0l-8.36 7.53c-.34.3-.13.87.33.87H5v7c0 .55.45 1 1 1h3c.55 0 1-.45 1-1z" fill="currentColor"></path></svg>';
const CRUMBS = /<div class="breadcrumbs container">([\s\S]*?)<\/div>/;
const CONTENT_START = /<div id="content"[^>]*><div>/;
const FOOTER = /<div id="footer"[^>]*>[\s\S]*?<div class="text-mobile">[\s\S]*?<\/div><\/div>/g;

/**
 * The docs breadcrumbs (Docusaurus' theme-doc-breadcrumbs markup, with za- classes because Scaladoc styles
 * .breadcrumbs) for a Scaladoc trail, given as its links `[{ href, label }]`: the home icon, then API for Scaladoc's
 * index entry, then the packages as the side menu shows them, and the current page last, not linked.
 */
export function breadcrumbsHtml(trail) {
  const [, ...rest] = trail; // the first entry is Scaladoc's index page, shown as API
  const packages = rest.slice(0, -1);
  const items = [
    `<li class="za-breadcrumbs__item"><a aria-label="Home page" class="za-breadcrumbs__link" href="/">${HOME_ICON}</a></li>`,
  ];
  const current = rest.length ? rest[rest.length - 1] : null;
  items.push(
    current
      ? '<li class="za-breadcrumbs__item"><a class="za-breadcrumbs__link" href="/api/"><span>API</span></a></li>'
      : '<li class="za-breadcrumbs__item za-breadcrumbs__item--active"><span class="za-breadcrumbs__link">API</span></li>',
  );
  // As the side menu labels them: the top-level package in full (zio), the ones inside it by their last segment.
  packages.forEach(({ href, label }, i) =>
    items.push(
      `<li class="za-breadcrumbs__item"><a class="za-breadcrumbs__link" href="${href}"><span>${i === 0 ? label : label.split('.').pop()}</span></a></li>`,
    ),
  );
  if (current)
    items.push(
      `<li class="za-breadcrumbs__item za-breadcrumbs__item--active"><span class="za-breadcrumbs__link">${current.label.split('.').pop()}</span></li>`,
    );
  return `<nav class="za-breadcrumbs" aria-label="Breadcrumbs"><ul class="za-breadcrumbs__list">${items.join('')}</ul></nav>`;
}

/**
 * Puts the site navbar, breadcrumbs, favicon, font, stylesheet and mobile-menu links into one Scaladoc page, without its
 * footer.
 */
export function themeApiPage(html, { prompt }) {
  let out = replaceOnce(html, HEADER, navbarHtml({ prompt }), 'header (#header before #mobile-menu)');
  // The breadcrumbs move from above the columns to the top of the text column, where a docs page has them.
  const crumbs = CRUMBS.exec(out);
  if (!crumbs) throw new Error('Scaladoc page has no breadcrumbs; did a Scaladoc upgrade change its markup?');
  const trail = [...crumbs[1].matchAll(/<a href="([^"]*)">([^<]*)<\/a>/g)].map(([, href, label]) => ({ href, label }));
  out = out.replace(CRUMBS, '');
  out = replaceOnce(out, CONTENT_START, (open) => open + breadcrumbsHtml(trail), 'content column');
  out = replaceOnce(out, MOBILE_LOGO, brandHtml('mobile-menu-logo'), 'mobile menu logo');
  out = replaceOnce(
    out,
    MOBILE_THEME,
    `<nav class="za-mobile-links">${NAV_LINKS.map((l) => linkHtml(l, 'za-mobile-link')).join('')}</nav>${MOBILE_THEME}`,
    'mobile theme toggle',
  );
  out = extensionlessLinks(out);
  // No "Generated with Scaladoc" footer (Scaladoc writes one for wide screens and one at the end of the text for
  // narrow ones); the docs pages have none like it.
  const footers = out.match(FOOTER)?.length ?? 0;
  if (!footers) throw new Error('Scaladoc page has no footer; did a Scaladoc upgrade change its markup?');
  out = out.replace(FOOTER, '');
  // The site favicon instead of Scaladoc's own favicon.ico, with the markup the docs pages use.
  out = replaceOnce(out, SCALADOC_FAVICON, `<link rel="icon" href="${FAVICON}">`, 'favicon link');
  return replaceOnce(out, '</head>', `${FONT_LINKS}<link rel="stylesheet" href="${STYLESHEET}"></head>`, '</head>');
}

/**
 * Checks the side menu still has the one root the theme expects: the zio package, which Scaladoc labels in full
 * and which theme.js keeps unfolded, as a docs sidebar shows its categories. A new top-level package (a module outside
 * zio) would appear beside it; fail so the breadcrumbs and the stylesheet are reviewed for it.
 */
export function checkSideMenu(menu) {
  const roots = menu?.api?.children;
  if (!roots || roots.length !== 1 || roots[0].name !== 'zio' || roots[0].kind !== 'package')
    throw new Error('dynamicSideMenu.json no longer has zio as its only API root; update checkSideMenu');
  return menu;
}

// Extensionless URLs: the site serves pages as /api/zio/stream/ZStream, as the docs are /reference/core/zio, rather
// than Scaladoc's ZStream.html. The hosts serve `ZStream.html` for `/…/ZStream` (Netlify, and `docusaurus serve`, which also
// redirects the .html form), so only the links change; the files keep their names. An index page is its folder.

/** `Chunk.html` -> `Chunk`, `Chunk.html#m` -> `Chunk#m`, `../index.html` -> `../`; `index.html` alone -> `./`. */
export function extensionless(url) {
  // Only the path: an anchor can end in .html too (Foo$.html#text.html-0).
  const at = url.indexOf('#');
  const [path, hash] = at < 0 ? [url, ''] : [url.slice(0, at), url.slice(at)];
  if (!path.endsWith('.html')) return url;
  const page = path.slice(0, -'.html'.length);
  const dir = /^(.*\/)?index$/.exec(page);
  return (dir ? dir[1] || './' : page) + hash;
}

// A relative link: no scheme, not protocol- or root-relative (the site's own /… links and other sites keep theirs).
const isRelative = (url) => !/^(?:[a-z][a-z0-9+.-]*:|\/|#)/i.test(url);

/** Drops `.html` from the relative links of a Scaladoc page. */
export function extensionlessLinks(html) {
  return html.replace(/href="([^"]*)"/g, (all, url) => (isRelative(url) ? `href="${extensionless(url)}"` : all));
}

/**
 * Drops `.html` from the page locations in Scaladoc's search index (scripts/searchData.js, `"l":"zio/stream.html#"`),
 * and the empty `#` Scaladoc ends a page's own entry with; entries for a page in `moves` point at its new place.
 */
export function extensionlessSearchData(js, moves = new Map()) {
  return js.replace(/"l":"([^"]*)"/g, (all, url) => {
    // A moved page's entries point at its new place first.
    const at = url.indexOf('#');
    const [path, hash] = at < 0 ? [url, ''] : [url.slice(0, at), url.slice(at)];
    const moved = path.endsWith('.html') && moves.get(path.slice(0, -'.html'.length));
    return `"l":"${extensionless(moved ? `${moved}.html${hash}` : url).replace(/#$/, '')}"`;
  });
}

/**
 * The side menu builds its links in scripts/ux.js (as `docs/generateApiDocs` patched it); make them extensionless too,
 * with the index entry as the API root. Fails if the line changed.
 */
export function extensionlessUxJs(js) {
  const link = 'return `${baseUrl.join("/")}/${loc.join("/")}.html`;';
  if (!js.includes(link)) throw new Error('scripts/ux.js no longer builds side-menu links with `' + link + '`');
  return js.replace(link, 'return `${baseUrl.join("/")}/${loc.join("/") === "index" ? "" : loc.join("/")}`;');
}

// Nested types get folders: Scaladoc files `Cause.Fail` as `Cause$$Fail.html` beside its parent; the site serves it as
// `Cause/Fail`, as a docs page sits under its category. Objects keep Scaladoc's trailing `$` (`Chunk$` is the companion
// of `Chunk`), so `Schedule.Decision` (an object) is `Schedule/Decision$`.

/**
 * Old page path -> new page path (no `.html`, relative to /api) for every nested type, read off the side menu, where
 * each one is a child of the type it is declared in. A child's file name is its parent's name (less the parent's
 * object `$`), `$$`, then its own name, which can itself start with `$` (`Foo$$$bar$` is an object `|` in `Foo`), so
 * the parent's name is taken off the front rather than split on `$$`.
 */
export function nestedTypePaths(menu) {
  const moves = new Map();
  const visit = (item, parent) => {
    const from = item.location.join('/');
    if (parent && parent.kind !== 'package' && parent.kind !== null) {
      const parentFrom = parent.location.join('/');
      const parentTo = moves.get(parentFrom) ?? parentFrom;
      const base = (p) => p.replace(/\$$/, '');
      // The parent's folder and name less its object `$`, then `$$`. What follows can hold a `/`: Scaladoc files a
      // name with a `.` in folders (an object `HTTP/1.0` in `Version` would be `Version$$HTTP$div1/0$$`).
      const prefix = base(parentFrom) + '$$';
      if (from.startsWith(prefix) && from.length > prefix.length) moves.set(from, `${base(parentTo)}/${from.slice(prefix.length)}`);
    }
    for (const child of item.children) visit(child, item);
  };
  for (const root of menu.api.children) visit(root, null);
  return moves;
}

/**
 * Rewrites one page for its new place: the page at `from` (a path under /api without `.html`) is published at `to`,
 * and the pages in `moves` (old path -> new path) at theirs. Every relative `href`/`src` is resolved against the old
 * place, mapped, and written relative to the new one; the root path Scaladoc's scripts use (`pathToRoot`) and the
 * page's own location (`data-rawLocation`) follow. Links that neither start nor end at a moved page are left as they
 * are.
 */
export function relocatePage(html, { from, to, moves }) {
  const base = new URL(`https://api.invalid/${from}.html`);
  const newDir = posix.dirname(`/${to}.html`);
  const out = html.replace(/\b(href|src)="([^"]*)"/g, (all, attr, url) => {
    if (!isRelative(url) || url === '') return all;
    const target = new URL(url, base);
    let path = decodeURIComponent(target.pathname).slice(1);
    const moved = path.endsWith('.html') && moves.get(path.slice(0, -'.html'.length));
    if (moved) path = `${moved}.html`;
    else if (from === to) return all;
    const relative = posix.relative(newDir, `/${path}`) || posix.basename(path);
    return `${attr}="${relative}${target.hash}"`;
  });
  if (from === to) return out;
  const root = '../'.repeat(to.split('/').length - 1);
  return out
    .replace(/data-pathToRoot="[^"]*"/, `data-pathToRoot="${root}"`)
    .replace(/pathToRoot = "[^"]*";/g, `pathToRoot = "${root}";`)
    .replace(`data-rawLocation="${from}"`, `data-rawLocation="${to}"`);
}

/** The side menu with every moved page at its new location (the names, which the menu shows, stay). */
export function relocateSideMenu(menu, moves) {
  const move = (item) => {
    const to = moves.get(item.location.join('/'));
    return { ...item, location: to ? to.split('/') : item.location, children: item.children.map(move) };
  };
  return { ...menu, api: { ...menu.api, children: menu.api.children.map(move) } };
}

/**
 * For the API index: send `/api` to `/api/` before anything loads. Without the slash the browser resolves the page's
 * relative paths (scripts/, styles/, dynamicSideMenu.json) against the site root, and the page comes up unstyled. The
 * hosts answer both paths with the same index.html, and a redirect rule cannot do this on Netlify (a rule is skipped
 * when a file answers the path, and its matching ignores the trailing slash), so the page does it, first thing in
 * <head>.
 */
export function folderIndexRedirect(html) {
  const script =
    "<script>if(!location.pathname.endsWith('/'))location.replace(location.pathname+'/'+location.search+location.hash)</script>";
  if (!html.includes('<head>')) throw new Error('Scaladoc index page has no <head>');
  // The base makes the requests the browser starts while parsing, before the script has run, resolve against /api/ too.
  return html.replace('<head>', `<head>${script}<base href="/api/">`);
}
