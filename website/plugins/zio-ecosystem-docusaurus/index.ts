import { LoadContext, PluginOptions } from '@docusaurus/types'
import { SidebarItemConfig, SidebarItemCategoryConfig, SidebarItemCategoryLinkDoc } from '@docusaurus/plugin-content-docs/src/sidebars/types'
import * as fs from 'fs'

/**
 * Docusaurus plugin that generates sidebar configuration for the ZIO ecosystem.
 *
 * Registers a CLI command called `generate-sidebar` that:
 * 1. Copies project files from ZIO npm packages into `./docs/`
 * 2. Aggregates sidebars from old-style and new-style projects
 * 3. Generates an `ecosystem-sidebar.js` file listing every project as a link
 * 4. Generates a `per-project-sidebars.js` file with one sidebar per project
 *
 * Each project's docs belong to exactly one sidebar (its own), so Docusaurus
 * shows that sidebar on the project's pages. The ecosystem sidebar only holds
 * `link` items, which point to a project without making its docs part of the
 * ecosystem sidebar.
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

          function categoryTemplate(name: string, items: SidebarItemConfig[]): SidebarItemCategoryConfig {
            return {
              type: 'category',
              label: name,
              link: { type: "doc", id: "index" },
              items: items
            }
          }

          const projects: EcosystemProject[] =
            oldStyledZioProjects
              // If the sidebar is simple and doesn't have label
              .filter(e => (require(`@zio.dev/${e.routeBasePath}/${e.sidebarPath}`).sidebar[0] == "index"))
              .map(project => ({
                name: project.routeBasePath,
                category: mapConfig(
                  categoryTemplate(
                    project.name,
                    require(`@zio.dev/${project.routeBasePath}/${project.sidebarPath}`)
                      .sidebar
                      .filter(e => e != "index")),
                  `${project.routeBasePath}/`
                ) as SidebarItemCategoryConfig
              }))
              .concat(
                zioNpmProjects()
                  // If the sidebar has all the metadata including the label
                  .filter(e => {
                    return require(`@zio.dev/${e}/sidebars.js`).sidebar[0].label !== undefined;
                  })
                  .map(project => ({
                    name: project,
                    category: mapConfig(require(`@zio.dev/${project}/sidebars.js`).sidebar[0], `${project}/`) as SidebarItemCategoryConfig
                  }))
              )
              .sort((a, b) => a.category.label < b.category.label ? -1 : a.category.label > b.category.label ? 1 : 0)

          // Plain links rather than `ref` items: a `ref` item takes its label
          // from the doc's `sidebar_label` ("Introduction", "Getting Started")
          // instead of the project name.
          const ecosystemSidebar =
            projects.map(project => ({
              type: 'link',
              label: project.category.label,
              href: '/' + entryDocId(project.category).replace(/index$/, '')
            }))

          const perProjectSidebars =
            Object.fromEntries(
              projects.map(project => [
                `${project.name}-sidebar`,
                [
                  { type: 'link', label: '← ZIO Ecosystem', href: '/ecosystem/' },
                  { ...project.category, collapsible: false, collapsed: false }
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

function writeSidebarFile(path: string, sidebar: unknown) {
  const content =
    `const sidebar = ${JSON.stringify(sidebar, null, '  ')}\nmodule.exports = sidebar`

  fs.writeFile(path, content, err => {
    if (err) {
      console.error(err);
    }
  });
}

// The doc a project's entry in the ecosystem sidebar should open: the
// category's own page if it has one, otherwise its first doc.
function entryDocId(config: SidebarItemConfig): string | undefined {
  if (typeof (config) === "string") {
    return config
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

const oldStyledZioProjects =
  [
    {
      name: 'ZIO AWS',
      routeBasePath: 'zio-aws',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Metrics Connectors',
      routeBasePath: 'zio-metrics-connectors',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Rocksdb',
      routeBasePath: 'zio-rocksdb',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO S3',
      routeBasePath: 'zio-s3',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO SQS',
      routeBasePath: 'zio-sqs',
      sidebarPath: 'sidebars.js',
    }
  ]

function isSidebarItemCategoryConfig(item: SidebarItemConfig): item is SidebarItemCategoryConfig {
  return (item as SidebarItemCategoryConfig).items !== undefined
}

function mapConfig(config: SidebarItemConfig, prefix: string) {
  if (typeof (config) === "string") {
    return prefix + config
  } else if (isSidebarItemCategoryConfig(config)) {
    return {
      ...config,
      collapsed: true,
      link: config.link ? { ...config.link, 'id': prefix + (config.link as SidebarItemCategoryLinkDoc).id } : undefined,
      items:
        ((config as SidebarItemCategoryConfig).items as SidebarItemConfig[])
          .map(item => mapConfig(item, prefix))
    }
  }
}

export default zioEcosystemPlugin
