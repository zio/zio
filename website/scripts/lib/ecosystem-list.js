// The pure parts of scripts/generate-ecosystem-libraries.js, which writes the library lists of docs/ecosystem/index.mdx.
// Kept apart so test/ecosystem-list.test.mjs can check them without the generated ecosystem-sidebar.js.

/**
 * The API reference of an official library whose Scaladoc the site builds (`zioApiDocs` in package.json), as
 * /<lib>/api/; undefined for other libraries, external sites and community libraries.
 */
function apiHrefFor(kind, key, zioApiDocs) {
  return kind === 'official' && Object.prototype.hasOwnProperty.call(zioApiDocs || {}, key) ? `/${key}/api/` : undefined
}

/**
 * One line of a library list: its docs link, then its API reference when it has one, then its description.
 * The API link is an explicit <a>, which MDX leaves as it is: the browser loads the static Scaladoc page in the same
 * tab. A markdown link would go through Docusaurus' Link, which routes it as a docs page or, with pathname://, opens
 * it in a new tab.
 */
function libraryLine({ label, href, description, api }) {
  const link = `[${label}](${href})` + (api ? ` · <a href="${api}">API</a>` : '')
  return description ? `- ${link} — ${description}` : `- ${link}`
}

module.exports = { apiHrefFor, libraryLine }
