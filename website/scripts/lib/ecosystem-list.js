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
 * The API link uses pathname:// so Docusaurus loads the static Scaladoc page instead of routing to it, as the navbar's
 * API item does (docusaurus.config.js).
 */
function libraryLine({ label, href, description, api }) {
  const link = `[${label}](${href})` + (api ? ` · [API](pathname://${api})` : '')
  return description ? `- ${link} — ${description}` : `- ${link}`
}

module.exports = { apiHrefFor, libraryLine }
