#!/usr/bin/env node
// Regenerates the lists of libraries in docs/ecosystem/index.mdx, and the data of the library cards above them
// (src/components/EcosystemCards/ecosystem.json).
//
// The libraries and their categories come from ecosystem-sidebar.js, which `docusaurus generate-sidebar` writes from
// ecosystem-categories.json; the one-line descriptions come from ecosystem-descriptions.json. Official libraries are
// keyed by their URL segment (e.g. "zio-cache"), or by their kebab-case label for external links (e.g. "zio-http");
// community libraries by the name of their page (e.g. "tapir"). The ZIO Blocks blocks come from ecosystem-blocks.json
// (scripts/sync-zio-blocks.mjs), in the same categories, and the compatible libraries from ecosystem-compatible.json.
// Official libraries listed under zioApiDocs in package.json also get an "API" link to their /<lib>/api/ reference.
//
// Run `yarn generate-sidebar` first, then `node scripts/generate-ecosystem-libraries.js`.
const fs = require('fs')
const path = require('path')

const website = path.join(__dirname, '..')
const page = path.join(website, '..', 'docs', 'ecosystem', 'index.mdx')
const cardsData = path.join(website, 'src', 'components', 'EcosystemCards', 'ecosystem.json')
const read = (file) => JSON.parse(fs.readFileSync(path.join(website, file), 'utf8'))

const sidebar = require(path.join(website, 'ecosystem-sidebar.js'))
const descriptions = read('ecosystem-descriptions.json')
const { categories } = read('ecosystem-categories.json')
const { blocks } = read('ecosystem-blocks.json')
const { compatible } = read('ecosystem-compatible.json')
const { apiHrefFor, libraryLine } = require(path.join(__dirname, 'lib', 'ecosystem-list.js'))
// The libraries whose API reference the site builds, at /<lib>/api/ (scripts/build-ecosystem-api.mjs).
const zioApiDocs = read('package.json').zioApiDocs || {}

const categoryId = (label) => (categories.find((c) => c.label === label) || { id: 'other' }).id
const isBlocksEntry = (item) => (item.className || '').includes('eco-blocks')

// Official libraries are `link` items, except those with a single page, which are `doc` items.
const officialHref = (item) => item.href || `/${item.id.replace(/index$/, '')}`

const kinds = {
  official: {
    href: (item) => officialHref(item),
    key: (item) => {
      const href = officialHref(item)
      return href.startsWith('/') ? href.replace(/^\/+|\/+$/g, '') : item.label.toLowerCase().replace(/\s+/g, '-')
    },
  },
  community: {
    href: (item) => `/${item.id}`,
    key: (item) => item.id.split('/').pop(),
  },
}

const marker = (edge, kind) => `{/* ${edge} GENERATED: ${kind} libraries */}`

function replaceBetweenMarkers(source, kind, body) {
  const begin = marker('BEGIN', kind)
  const start = source.indexOf(begin)
  const stop = source.indexOf(marker('END', kind))
  if (start < 0 || stop < start) {
    console.error(`Markers for ${kind} libraries not found in ${page}`)
    process.exit(1)
  }
  return `${source.slice(0, start + begin.length)}\n\n${body}\n\n${source.slice(stop)}`
}

// The libraries of one kind, by category, as in the ecosystem sidebar.
function librariesOf(kind) {
  return sidebar
    .map((category) => ({
      label: category.label,
      libraries: category.items
        .filter((item) => (item.className || '').includes(`eco-tag-${kind}`) && !isBlocksEntry(item))
        .sort((a, b) => a.label.localeCompare(b.label)),
    }))
}

const cards = []
const missing = []
let source = fs.readFileSync(page, 'utf8')

for (const [kind, { href, key }] of Object.entries(kinds)) {
  const sections = librariesOf(kind).map(({ label, libraries }) => {
    const lines = libraries.map((library) => {
      const description = (descriptions[kind] || {})[key(library)]
      if (!description) missing.push(`${kind}: ${library.label}`)
      const api = apiHrefFor(kind, key(library), zioApiDocs)
      cards.push({
        name: library.label,
        href: href(library),
        kind,
        category: categoryId(label),
        description: description || '',
        api: !!api,
      })
      return libraryLine({ label: library.label, href: href(library), description, api })
    })
    // The blocks of the category, on one line, after the official libraries.
    if (kind === 'official') {
      const inCategory = blocks.filter((block) => block.category === categoryId(label))
      if (inCategory.length > 0) {
        lines.push(`- ZIO Blocks: ${inCategory.map((block) => `[${block.name}](${block.href})`).join(', ')}`)
      }
    }
    return { label, lines }
  })
  const body = sections
    .filter(({ lines }) => lines.length > 0)
    .map(({ label, lines }) => `### ${label}\n\n${lines.join('\n')}`)
    .join('\n\n')
  source = replaceBetweenMarkers(source, kind, body)
}

source = replaceBetweenMarkers(
  source,
  'compatible',
  compatible.map((library) => `- [${library.name}](${library.href}) — ${library.description}`).join('\n')
)
fs.writeFileSync(page, source)

for (const block of blocks) {
  cards.push({
    name: block.name,
    href: block.href,
    kind: 'official',
    family: 'zio-blocks',
    category: block.category,
    description: block.description,
    artifact: block.artifact,
    platforms: block.platforms,
    scala: block.scala,
    api: false,
  })
}
for (const library of compatible) {
  cards.push({ name: library.name, href: library.href, kind: 'compatible', category: 'compatible', description: library.description, api: false })
}

const used = new Set(cards.map((card) => card.category))
const data = {
  categories: categories.filter((c) => used.has(c.id)).map(({ id, label }) => ({ id, label })),
  cards,
}
fs.writeFileSync(cardsData, JSON.stringify(data, null, 2) + '\n')

const count = (predicate) => cards.filter(predicate).length
console.log(
  `Wrote ${count((c) => c.kind === 'official' && !c.family)} official and ${count((c) => c.kind === 'community')} community libraries, ` +
    `${blocks.length} ZIO Blocks blocks and ${compatible.length} compatible libraries to ${path.relative(process.cwd(), page)} ` +
    `and ${path.relative(process.cwd(), cardsData)}`
)
if (missing.length > 0) console.warn(`Missing descriptions: ${missing.join(', ')}`)
