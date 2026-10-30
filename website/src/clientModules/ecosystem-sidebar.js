// The "← ZIO Ecosystem" link of a project sidebar points to
// /ecosystem/?eco-category=<id>. Docusaurus doesn't keep the expanded state of
// a sidebar category when the sidebar changes, so expand that category here to
// bring the reader back to the section they came from.
const PARAM = 'eco-category';
const ATTEMPTS = 40;

function expand(id, attempt) {
  const category = document.querySelector(`.eco-category-${CSS.escape(id)}`);
  if (!category) {
    // The sidebar renders after the route changes.
    if (attempt < ATTEMPTS) setTimeout(() => expand(id, attempt + 1), 50);
    return;
  }
  if (category.classList.contains('menu__list-item--collapsed')) {
    category.querySelector(':scope > .menu__list-item-collapsible > .menu__link').click();
  }
  category.scrollIntoView({ block: 'nearest' });
}

export function onRouteDidUpdate({ location }) {
  const id = new URLSearchParams(location.search).get(PARAM);
  if (!id) return;
  expand(id, 0);
  // Keep the address bar clean.
  const params = new URLSearchParams(location.search);
  params.delete(PARAM);
  const search = params.toString();
  window.history.replaceState(window.history.state, '', location.pathname + (search ? `?${search}` : '') + location.hash);
}
