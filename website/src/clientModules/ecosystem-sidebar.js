// Docusaurus doesn't keep the expanded state of the ecosystem sidebar when the
// reader moves to a project's own sidebar and back. So this module remembers
// which categories were open the last time the ecosystem sidebar was used, and
// restores them when the "← ZIO Ecosystem" link of a project sidebar, which
// points to /ecosystem/?eco-category=<id>, brings the reader back.
//
// If nothing was remembered, the category from the link is expanded instead.
// The library the reader last opened is marked, with the data-eco-last
// attribute, so it is easy to find again.
const PARAM = 'eco-category';
const STORAGE_KEY = 'eco-sidebar-open-categories';
const LAST_KEY = 'eco-sidebar-last-library';
const MARK = 'data-eco-last';
const CATEGORY_PREFIX = 'eco-category-';
const ATTEMPTS = 40;

const categories = () => Array.from(document.querySelectorAll('.eco-category'));
const idOf = (category) =>
  Array.from(category.classList)
    .find((name) => name.startsWith(CATEGORY_PREFIX))
    .slice(CATEGORY_PREFIX.length);
const isOpen = (category) => !category.classList.contains('menu__list-item--collapsed');
const toggle = (category) =>
  category.querySelector(':scope > .menu__list-item-collapsible > .menu__link').click();

function read() {
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY));
    return Array.isArray(stored) ? stored : null;
  } catch (e) {
    return null;
  }
}

function remember(library) {
  try {
    const open = categories().filter(isOpen).map(idOf);
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(open));
    if (library) window.sessionStorage.setItem(LAST_KEY, library);
  } catch (e) {
    // The browser blocks storage: the sidebar just isn't restored.
  }
}

function lastLibrary() {
  try {
    return window.sessionStorage.getItem(LAST_KEY);
  } catch (e) {
    return null;
  }
}

let markTimer = null;

function unmark() {
  clearInterval(markTimer);
  document.querySelectorAll(`[${MARK}]`).forEach((link) => link.removeAttribute(MARK));
}

// React may re-render the sidebar right after it is restored, which drops
// attributes it doesn't know about, so keep applying the mark for a moment.
function mark(library) {
  unmark();
  let scrolled = false;
  const apply = () => {
    const link = Array.from(document.querySelectorAll('.eco-category a.menu__link')).find(
      (candidate) => candidate.getAttribute('href') === library
    );
    if (!link) return;
    if (!link.hasAttribute(MARK)) link.setAttribute(MARK, 'true');
    if (!scrolled) {
      scrolled = true;
      link.scrollIntoView({ block: 'nearest' });
    }
  };
  apply();
  const stop = Date.now() + 3000;
  markTimer = setInterval(() => (Date.now() > stop ? clearInterval(markTimer) : apply()), 100);
}

function restore(wanted) {
  // Collapse first: with auto-collapse, opening a category closes the others.
  categories()
    .filter((category) => isOpen(category) && !wanted.includes(idOf(category)))
    .forEach(toggle);
  categories()
    .filter((category) => !isOpen(category) && wanted.includes(idOf(category)))
    .forEach(toggle);
  const library = lastLibrary();
  if (library) mark(library);
  else {
    const open = categories().find(isOpen);
    if (open) open.scrollIntoView({ block: 'nearest' });
  }
}

function whenSidebarIsRendered(callback, attempt = 0) {
  if (categories().length > 0) callback();
  // The sidebar renders after the route changes.
  else if (attempt < ATTEMPTS) setTimeout(() => whenSidebarIsRendered(callback, attempt + 1), 50);
}

export function onRouteDidUpdate({ location }) {
  const params = new URLSearchParams(location.search);
  const requested = params.get(PARAM);
  if (!requested) return;

  whenSidebarIsRendered(() => restore(read() || [requested]));

  // Keep the address bar clean.
  params.delete(PARAM);
  const search = params.toString();
  window.history.replaceState(
    window.history.state,
    '',
    location.pathname + (search ? `?${search}` : '') + location.hash
  );
}

if (typeof document !== 'undefined') {
  // A click in the ecosystem sidebar either toggles a category or leaves for
  // another page: in both cases the open categories are what to bring back.
  // The listener captures the click, before React handles it, because React
  // may replace the sidebar with the one of the next page right away.
  document.addEventListener(
    'click',
    (event) => {
      if (!event.target.closest('.theme-doc-sidebar-container') || categories().length === 0) return;
      unmark();
      // A library is a link inside a category, to a page of this site.
      const link = event.target.closest('.eco-category a.menu__link');
      const href = link ? link.getAttribute('href') : null;
      if (href && href.startsWith('/')) remember(href);
      // Toggling a category changes what is open, so wait for the new state.
      else setTimeout(() => remember(null), 0);
    },
    true
  );
}
