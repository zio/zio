#!/usr/bin/env node
// Regenerates the data of the library cards of /ecosystem/ (src/components/EcosystemCards/ecosystem.json).
//
// The libraries and their categories come from ecosystem-sidebar.js, which `docusaurus generate-sidebar` writes from
// ecosystem-categories.json; the one-line descriptions come from ecosystem-descriptions.json. Official libraries are
// keyed by their URL segment (e.g. "zio-cache"), or by their kebab-case label for external links (e.g. "zio-http");
// community libraries by the name of their page (e.g. "tapir"). The ZIO Blocks blocks come from ecosystem-blocks.json
// (scripts/sync-zio-blocks.mjs), in the same categories, and the compatible libraries from ecosystem-compatible.json.
// Official libraries listed under zioApiDocs in package.json also get a link to their /<lib>/api/ reference.
//
// Run `yarn generate-sidebar` first, then `node scripts/generate-ecosystem-libraries.js`.
const fs = require('fs')
const path = require('path')

const website = path.join(__dirname, '..')
const cardsData = path.join(website, 'src', 'components', 'EcosystemCards', 'ecosystem.json')
const read = (file) => JSON.parse(fs.readFileSync(path.join(website, file), 'utf8'))

const sidebar = require(path.join(website, 'ecosystem-sidebar.js'))
const descriptions = read('ecosystem-descriptions.json')
const { categories } = read('ecosystem-categories.json')
const { blocks } = read('ecosystem-blocks.json')
const { compatible } = read('ecosystem-compatible.json')
const { apiHrefFor } = require(path.join(__dirname, 'lib', 'ecosystem-list.js'))
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

const cards = []
const missing = []

// The libraries of each kind, by category, as in the ecosystem sidebar.
for (const [kind, { href, key }] of Object.entries(kinds)) {
  for (const category of sidebar) {
    const libraries = category.items
      .filter((item) => (item.className || '').includes(`eco-tag-${kind}`) && !isBlocksEntry(item))
      .sort((a, b) => a.label.localeCompare(b.label))
    for (const library of libraries) {
      const description = (descriptions[kind] || {})[key(library)]
      if (!description) missing.push(`${kind}: ${library.label}`)
      cards.push({
        name: library.label,
        href: href(library),
        kind,
        category: categoryId(category.label),
        description: description || '',
        api: !!apiHrefFor(kind, key(library), zioApiDocs),
      })
    }
  }
}

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
    `${blocks.length} ZIO Blocks blocks and ${compatible.length} compatible libraries to ${path.relative(process.cwd(), cardsData)}`
)
if (missing.length > 0) console.warn(`Missing descriptions: ${missing.join(', ')}`)
