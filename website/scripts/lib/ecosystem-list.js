// The pure parts of scripts/generate-ecosystem-libraries.js, which writes the data of the library cards of /ecosystem/.
// Kept apart so test/ecosystem-list.test.mjs can check them without the generated ecosystem-sidebar.js.

/**
 * The API reference of an official library whose Scaladoc the site builds (`zioApiDocs` in package.json), as
 * /<lib>/api/; undefined for other libraries, external sites and community libraries.
 */
function apiHrefFor(kind, key, zioApiDocs) {
  return kind === 'official' && Object.prototype.hasOwnProperty.call(zioApiDocs || {}, key) ? `/${key}/api/` : undefined
}

module.exports = { apiHrefFor }
