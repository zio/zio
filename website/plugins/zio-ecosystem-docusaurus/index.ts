import { LoadContext, PluginOptions } from '@docusaurus/types'
import { SidebarItemConfig, SidebarItemCategoryConfig, SidebarItemCategoryLinkDoc } from '@docusaurus/plugin-content-docs/src/sidebars/types'
import * as fs from 'fs'

/**
 * Docusaurus plugin that generates sidebar configuration for the ZIO ecosystem.
 *
 * Registers a CLI command called `generate-sidebar` that:
 * 1. Copies project files from ZIO npm packages into `./docs/`
 * 2. Aggregates the sidebars of all projects, whether a project's sidebar is a
 *    single category or a flat list of items (see `projectCategory`)
 * 3. Generates an `ecosystem-sidebar.js` file that groups every official and
 *    community library into the purpose-based categories of
 *    `ecosystem-categories.json`
 * 4. Generates a `per-project-sidebars.js` file with one sidebar per project
 *
 * Each project's docs belong to exactly one sidebar (its own), so Docusaurus
 * shows that sidebar on the project's pages. Official libraries appear in the
 * ecosystem sidebar as `link` items, which point to a project without making
 * its docs part of the ecosystem sidebar. Community libraries are single pages
 * under `docs/ecosystem/community`, so they appear as `doc` items.
 *
 * @param context - Docusaurus plugin context
 * @param options - Plugin configuration options
 * @returns A Docusaurus plugin object with the command handler
 */
function zioEcosystemPlugin(context: LoadContext, options: PluginOptions) {
  return {
    name: 'zio-ecosystem-plugin',
    extendCli(cli) {
      cli.command('generate-sidebar')
        .description("This command generates a sidebar file for the ZIO ecosystem")
        .action(() => {

          (zioNpmProjects()).forEach(project => {
            copy(`node_modules/@zio.dev/${project}`, `./docs/${project}`)
          })

          const projects: EcosystemProject[] =
            zioNpmProjects()
              .map(project => ({
                name: project,
                category: projectCategory(project, require(`@zio.dev/${project}/sidebars.js`).sidebar)
              }))
              .sort((a, b) => a.category.label < b.category.label ? -1 : a.category.label > b.category.label ? 1 : 0)

          // Plain links rather than `ref` items: a `ref` item takes its label
          // from the doc's `sidebar_label` ("Introduction", "Getting Started")
          // instead of the project name. A project with a single page gets no
          // sidebar of its own, so it is a `doc` item, which makes the ecosystem
          // sidebar the one shown on that page.
          const officialLinks: Record<string, SidebarEntry> =
            Object.fromEntries(
              projects.map(project => [
                project.name,
                {
                  label: project.category.label,
                  item: isSinglePage(project.category)
                    ? {
                      type: 'doc',
                      id: entryDocId(project.category),
                      label: project.category.label,
                      className: 'eco-tag eco-tag-official'
                    }
                    : {
                      type: 'link',
                      label: project.category.label,
                      href: '/' + entryDocId(project.category).replace(/index$/, ''),
                      className: 'eco-tag eco-tag-official'
                    }
                }
              ])
            )

          const ecosystemSidebar = categorizedEcosystem(officialLinks)

          const perProjectSidebars =
            Object.fromEntries(
              projects.filter(project => !isSinglePage(project.category)).map(project => [
                `${project.name}-sidebar`,
                [
                  { type: 'link', label: '← ZIO Ecosystem', href: `/ecosystem/?eco-category=${categoryIdOf(project.name)}` },
                  { ...project.category, collapsible: false, collapsed: false, items: itemsWithoutOwnPage(project.category) }
                ]
              ])
            )

          writeSidebarFile('ecosystem-sidebar.js', ecosystemSidebar)
          writeSidebarFile('per-project-sidebars.js', perProjectSidebars)
        })
    }
  }
}

type EcosystemProject = {
  name: string
  category: SidebarItemCategoryConfig
}

type SidebarEntry = { label: string, item: unknown }

type CategoryConfig = {
  id: string
  label: string
  collapsed?: boolean
  official: string[]
  external?: { label: string, href: string }[]
  community: string[]
}

// The id of the category that lists a project in the ecosystem sidebar. The
// "← ZIO Ecosystem" link of the project passes it along, so the ecosystem
// sidebar opens with that category expanded (see
// src/clientModules/ecosystem-sidebar.js).
function categoryIdOf(project: string): string {
  const categories: CategoryConfig[] =
    JSON.parse(fs.readFileSync('ecosystem-categories.json', 'utf8')).categories
  const category = categories.find(c => c.official.includes(project))
  return category ? category.id : 'other'
}

// The title of a community library's page, used as its sidebar label so that
// the entries of a category can be sorted next to the official ones.
function communityLabel(docId: string): string {
  const source = fs.readFileSync(`../docs/ecosystem/community/${docId}.md`, 'utf8')
  const title = source.match(/^title:\s*"?([^"\n]+?)"?\s*$/m)
  return title ? title[1] : docId
}

// Groups the libraries by purpose, as listed in `ecosystem-categories.json`.
// A library that is not listed lands in "Other", so a new package or page
// never disappears from the sidebar.
function categorizedEcosystem(officialLinks: Record<string, SidebarEntry>) {
  const categories: CategoryConfig[] =
    JSON.parse(fs.readFileSync('ecosystem-categories.json', 'utf8')).categories

  const communityDocs =
    fs.readdirSync('../docs/ecosystem/community')
      .filter(file => file.endsWith('.md') && file !== 'index.md')
      .map(file => file.replace(/\.md$/, ''))

  const listed = new Set(categories.flatMap(c => c.official))
  const listedCommunity = new Set(categories.flatMap(c => c.community))

  const all: CategoryConfig[] = categories.concat({
    id: 'other',
    label: 'Other',
    official: Object.keys(officialLinks).filter(name => !listed.has(name)),
    community: communityDocs.filter(doc => !listedCommunity.has(doc))
  })

  return all
    .map(category => {
      const entries: SidebarEntry[] =
        category.official.map(name => officialLinks[name]).filter(entry => entry !== undefined)
          .concat((category.external || []).map(link => ({
            label: link.label,
            item: { type: 'link', label: link.label, href: link.href, className: 'eco-tag eco-tag-official' }
          })))
          .concat(category.community.map(doc => ({
            label: communityLabel(doc),
            item: { type: 'doc', id: `ecosystem/community/${doc}`, label: communityLabel(doc), className: 'eco-tag eco-tag-community' }
          })))
          .sort((a, b) => a.label.localeCompare(b.label))

      return { category, entries }
    })
    .filter(({ entries }) => entries.length > 0)
    .map(({ category, entries }) => ({
      type: 'category',
      label: category.label,
      className: `eco-category eco-category-${category.id}`,
      collapsed: category.collapsed !== undefined ? category.collapsed : true,
      items: entries.map(entry => entry.item)
    }))
}

function writeSidebarFile(path: string, sidebar: unknown) {
  const content =
    `const sidebar = ${JSON.stringify(sidebar, null, '  ')}\nmodule.exports = sidebar`

  fs.writeFile(path, content, err => {
    if (err) {
      console.error(err);
    }
  });
}

// The category of a project links to its own page, so listing that page again
// among its items would show the same entry twice.
function itemsWithoutOwnPage(category: SidebarItemCategoryConfig): SidebarItemConfig[] {
  const own = category.link && category.link.type === "doc" ? (category.link as SidebarItemCategoryLinkDoc).id : undefined
  return (category.items as SidebarItemConfig[]).filter(item =>
    item !== own && !(typeof item === "object" && item.type === "doc" && item.id === own))
}

// Whether a project documents itself on one page, in which case it doesn't
// need a sidebar of its own.
function isSinglePage(config: SidebarItemConfig): boolean {
  return leafDocCount(config) <= 1
}

function leafDocCount(config: SidebarItemConfig): number {
  if (typeof (config) === "string") {
    return 1
  } else if (isSidebarItemCategoryConfig(config)) {
    // The category's own landing page (its `link`) is a page too, even though
    // it is not listed in `items`.
    const ownPage = config.link && config.link.type === "doc" ? 1 : 0
    return ownPage + (config.items as SidebarItemConfig[]).reduce((count, item) => count + leafDocCount(item), 0)
  }
  return 1
}

// The doc a project's entry in the ecosystem sidebar should open: the
// category's own page if it has one, otherwise its first doc.
function entryDocId(config: SidebarItemConfig): string | undefined {
  if (typeof (config) === "string") {
    return config
  } else if (!isSidebarItemCategoryConfig(config) && config.type === "doc") {
    return config.id
  } else if (isSidebarItemCategoryConfig(config)) {
    if (config.link && config.link.type === "doc") {
      return (config.link as SidebarItemCategoryLinkDoc).id
    }
    for (const item of config.items as SidebarItemConfig[]) {
      const id = entryDocId(item)
      if (id) return id
    }
  }
  return undefined
}

function zioNpmProjects(): string[] {
  return Object
    .entries(
      (JSON.parse(
        fs.readFileSync('package.json', 'utf8'))
      ).dependencies as { [key: string]: string }
    )
    .filter(([key, _]) =>
      key.startsWith("@zio.dev"))
    .map(([key, _]) => key.split("/")[1])
    .sort()
}

function copy(srcDir: string, destDir: string) {
  const fs = require('fs-extra');
  try {
    // Drop files left over from a previous package version
    fs.removeSync(destDir)
    fs.copySync(srcDir, destDir, { overwrite: true || false })
  } catch (err) {
    console.error(err)
  }
}

// Display names that can't be derived from the package name by title-casing,
// because they contain an acronym or a non-standard capitalization.
const projectNames: Record<string, string> = {
  'zio-amqp': 'ZIO AMQP',
  'zio-aws': 'ZIO AWS',
  'zio-bson': 'ZIO BSON',
  'zio-json': 'ZIO JSON',
  'zio-rocksdb': 'ZIO Rocksdb',
  'zio-s3': 'ZIO S3',
  'zio-sbt': 'ZIO SBT',
  'zio-sqs': 'ZIO SQS'
}

// "zio-redis" -> "ZIO Redis"
function projectName(project: string): string {
  return projectNames[project] ||
    project
      .split('-')
      .map(word => word === 'zio' ? 'ZIO' : word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
}

// Labels that describe a section of a project rather than the project itself.
const genericLabels = ['Getting Started', 'Introduction', 'Overview']

// Builds the category that lists a project in the ecosystem sidebar. A project
// sidebar comes in one of these shapes:
//
// 1. a single category with a label, which is used as it is;
// 2. a flat array that starts with the plain string "index";
// 3. a flat array of strings, `doc` items (labeled or not), `link` items and
//    categories, whose first item is the index page.
//
// A flat array (2 and 3) is wrapped in a category labeled with the project's
// display name that links to the index page.
function projectCategory(project: string, sidebar: SidebarItemConfig[]): SidebarItemCategoryConfig {
  const prefix = `${project}/`
  const first = sidebar[0]

  if (sidebar.length === 1 && isSidebarItemCategoryConfig(first) && first.label !== undefined) {
    const category = mapConfig(first, prefix) as SidebarItemCategoryConfig
    // Some projects label their category "Getting Started"; the ecosystem
    // sidebar needs the project's name.
    return genericLabels.includes(category.label) ? { ...category, label: projectName(project) } : category
  }

  return mapConfig({
    type: 'category',
    label: projectName(project),
    link: { type: 'doc', id: 'index' },
    items: sidebar.filter(item => !isIndexPage(item))
  }, prefix) as SidebarItemCategoryConfig
}

function isIndexPage(item: SidebarItemConfig): boolean {
  return item === 'index' || (typeof item === 'object' && item.type === 'doc' && item.id === 'index')
}

function isSidebarItemCategoryConfig(item: SidebarItemConfig): item is SidebarItemCategoryConfig {
  return (item as SidebarItemCategoryConfig).items !== undefined
}

// Prefixes every doc id in a project's sidebar. A category keeps the project's
// own `collapsed` setting; when the project doesn't set one, it starts collapsed.
// Items that don't refer to a doc of the project, such as `link` items, are
// passed through.
function mapConfig(config: SidebarItemConfig, prefix: string): SidebarItemConfig {
  if (typeof (config) === "string") {
    return prefix + config
  } else if (isSidebarItemCategoryConfig(config)) {
    return {
      ...config,
      collapsed: config.collapsed ?? true,
      link: config.link && config.link.type === "doc"
        ? { ...config.link, 'id': prefix + (config.link as SidebarItemCategoryLinkDoc).id }
        : config.link,
      items:
        ((config as SidebarItemCategoryConfig).items as SidebarItemConfig[])
          .map(item => mapConfig(item, prefix))
    }
  } else if (config.type === "doc") {
    return { ...config, id: prefix + config.id }
  }
  return config
}

export default zioEcosystemPlugin
