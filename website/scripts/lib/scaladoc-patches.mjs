// Fixes for Scaladoc bugs in its scripts/ux.js that break navigation on zio.dev. Applied to every Scaladoc site the
// website serves: zio's own /api (`sbt apiDocs` runs scripts/patch-scaladoc.mjs on it) and each ecosystem library's
// /<lib>/api (scripts/build-ecosystem-api.mjs). Each patch fails once its target text is gone, so a Scaladoc upgrade
// that fixes (or rewrites) the script gets the patch reviewed instead of silently skipped.
export const UX_JS_PATCHES = [
  {
    // The dynamic side menu interpolates `baseUrl`, an array of path segments, straight into its links, so JavaScript
    // joins it with commas: under /api every menu link becomes ",api/zio.html", which resolves against the current page
    // and 404s. (At a site root the array is [""] and the bug is invisible.) Join it with "/".
    broken: 'return `${baseUrl}/${loc.join("/")}.html`;',
    fixed: 'return `${baseUrl.join("/")}/${loc.join("/")}.html`;',
  },
  {
    // Same-origin link clicks are loaded in place with jQuery's `$.get`, and jQuery comes from code.jquery.com: when it
    // fails to load, the click handler calls preventDefault and then throws "$ is not defined", so no link navigates.
    // Use fetch, and fall back to a normal page load if the request fails (the never-settling promise keeps the
    // in-place load from running then).
    broken: '$.get(href, function (data) {',
    fixed:
      'fetch(href).then((r) => (r.ok ? r.text() : Promise.reject(r)))' +
      '.catch(() => { window.location.href = href; return new Promise(() => {}); })' +
      '.then(function (data) {',
  },
  {
    // Back and Forward restore the page content but not the dynamic side menu, which keeps highlighting the page you
    // left. Update it the way a link click does.
    broken: '  loadPageState(document, e.state);\n',
    fixed:
      '  const oldLoc = getRawLoc();\n' +
      '  loadPageState(document, e.state);\n' +
      '  if (dynamicSideMenu && updateMenu) updateMenu(oldLoc, getRawLoc());\n',
  },
  {
    // A URL naming a section (#attributes) rather than a member expands it with the member's toggle button, which a
    // section does not have, so the page setup throws there and skips the rest (code highlighting included). Toggle
    // the button only when there is one.
    broken: 'btn.classList.toggle("expand");\n        btn.classList.toggle("expanded");',
    fixed: 'btn?.classList.toggle("expand");\n        btn?.classList.toggle("expanded");',
  },
];

/** Applies every patch of `UX_JS_PATCHES` to the text of a Scaladoc scripts/ux.js; throws if a target is missing. */
export function patchUxJs(js) {
  return UX_JS_PATCHES.reduce((src, { broken, fixed }) => {
    if (!src.includes(broken))
      throw new Error(`scripts/ux.js no longer contains \`${broken}\`: check whether Scaladoc fixed it, drop the patch`);
    return src.replace(broken, fixed);
  }, js);
}
