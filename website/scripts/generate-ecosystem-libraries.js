#!/usr/bin/env node
// Regenerates the lists of libraries in docs/ecosystem/officials/index.mdx and
// docs/ecosystem/community/index.mdx.
//
// The libraries and their categories come from ecosystem-sidebar.js, which
// `docusaurus generate-sidebar` writes from ecosystem-categories.json; the
// one-line descriptions come from ecosystem-descriptions.json. Official
// libraries are keyed by their URL segment (e.g. "zio-cache"), or by their
// kebab-case label for external links (e.g. "zio-http"); community libraries by
// the name of their page (e.g. "tapir"). Run `yarn generate-sidebar` first,
// then `node scripts/generate-ecosystem-libraries.js`.
const fs = require('fs')
const path = require('path')

const website = path.join(__dirname, '..')
const docs = path.join(website, '..', 'docs', 'ecosystem')
const sidebar = require(path.join(website, 'ecosystem-sidebar.js'))
const descriptions = require(path.join(website, 'ecosystem-descriptions.json'))

const kinds = {
  official: {
    page: path.join(docs, 'officials', 'index.mdx'),
    href: (item) => item.href,
    key: (item) =>
      item.href.startsWith('/')
        ? item.href.replace(/^\/+|\/+$/g, '')
        : item.label.toLowerCase().replace(/\s+/g, '-'),
  },
  community: {
    page: path.join(docs, 'community', 'index.mdx'),
    href: (item) => `/${item.id}`,
    key: (item) => item.id.split('/').pop(),
  },
}

const marker = (edge, kind) => `{/* ${edge} GENERATED: ${kind} libraries */}`

function generate(kind, { page, href, key }) {
  const sections = sidebar
    .map((category) => ({
      label: category.label,
      libraries: category.items
        .filter((item) => (item.className || '').includes(`eco-tag-${kind}`))
        .sort((a, b) => a.label.localeCompare(b.label)),
    }))
    .filter((section) => section.libraries.length > 0)

  const missing = []
  const body = sections
    .map(({ label, libraries }) => {
      const lines = libraries.map((library) => {
        const description = (descriptions[kind] || {})[key(library)]
        if (!description) missing.push(library.label)
        const link = `[${library.label}](${href(library)})`
        return description ? `- ${link} — ${description}` : `- ${link}`
      })
      return `### ${label}\n\n${lines.join('\n')}`
    })
    .join('\n\n')

  const source = fs.readFileSync(page, 'utf8')
  const begin = marker('BEGIN', kind)
  const start = source.indexOf(begin)
  const stop = source.indexOf(marker('END', kind))
  if (start < 0 || stop < start) {
    console.error(`Markers not found in ${page}`)
    process.exit(1)
  }

  fs.writeFileSync(page, `${source.slice(0, start + begin.length)}\n\n${body}\n\n${source.slice(stop)}`)
  const count = sections.reduce((n, s) => n + s.libraries.length, 0)
  console.log(`Wrote ${count} ${kind} libraries to ${path.relative(process.cwd(), page)}`)
  if (missing.length > 0) console.warn(`Missing ${kind} descriptions: ${missing.join(', ')}`)
}

Object.entries(kinds).forEach(([kind, config]) => generate(kind, config))
