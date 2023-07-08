import { LoadContext, PluginOptions } from '@docusaurus/types'
import { SidebarItemConfig, SidebarItemCategoryConfig, SidebarItemCategoryLinkDoc } from '@docusaurus/plugin-content-docs/src/sidebars/types'
import * as fs from 'fs'

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

          const result =
            zioProjects
              // If the sidebar is simple and doesn't have label
              .filter(e => (require(`@zio.dev/${e.routeBasePath}/${e.sidebarPath}`).sidebar[0] == "index"))
              .map(project => {
                return mapConfig(
                  categoryTemplate(
                    project.name,
                    require(`@zio.dev/${project.routeBasePath}/${project.sidebarPath}`)
                      .sidebar
                      .filter(e => e != "index")),
                  `${project.routeBasePath}/`
                )
              })
              .concat(
                zioProjects
                  // If the sidebar has all the metadata including the label
                  .filter(e => {
                    return require(`@zio.dev/${e.routeBasePath}/${e.sidebarPath}`).sidebar[0].label !== undefined
                  })
                  .map(p => p.routeBasePath)
                  .map(project =>
                    mapConfig(require(`@zio.dev/${project}/sidebars.js`).sidebar[0], `${project}/`)
                  )
              )
              .sort((a, b) => a.label < b.label ? -1 : a.label > b.label ? 1 : 0)

          const content =
            `const sidebar = ${JSON.stringify(result, null, '  ')}\nmodule.exports = sidebar`

          fs.writeFile('ecosystem-sidebar.js', content, err => {
            if (err) {
              console.error(err);
            }
          });
        })
    }
  }
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
    fs.copySync(srcDir, destDir, { overwrite: true || false })
  } catch (err) {
    console.error(err)
  }
}

const zioProjects =
  [
    {
      name: 'Caliban Deriving',
      routeBasePath: 'caliban-deriving',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Interop Monix',
      routeBasePath: 'interop-monix',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Izumi Reflect',
      routeBasePath: 'izumi-reflect',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Actors',
      routeBasePath: 'zio-actors',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Akka Cluster",
      routeBasePath: 'zio-akka-cluster',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO AWS',
      routeBasePath: 'zio-aws',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Cache",
      routeBasePath: 'zio-cache',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO CLI",
      routeBasePath: 'zio-cli',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Config",
      routeBasePath: 'zio-config',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Constraintless",
      routeBasePath: 'zio-constraintless',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Connect",
      routeBasePath: 'zio-connect',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Crypto",
      routeBasePath: 'zio-crypto',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO Deriving",
      routeBasePath: 'zio-deriving',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO DynamoDB",
      routeBasePath: 'zio-dynamodb',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Flow',
      routeBasePath: 'zio-flow',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO FTP',
      routeBasePath: 'zio-ftp',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Insight',
      routeBasePath: 'zio-insight',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Interop Guava',
      routeBasePath: 'zio-interop-guava',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Interop Scalaz',
      routeBasePath: 'zio-interop-scalaz',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Interop Reactivestreams',
      routeBasePath: 'zio-interop-reactivestreams',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Interop Twitter',
      routeBasePath: 'zio-interop-twitter',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO JDBC",
      routeBasePath: 'zio-jdbc',
      sidebarPath: 'sidebars.js',
    },
    {
      name: "ZIO JSON",
      routeBasePath: 'zio-json',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Kafka',
      routeBasePath: 'zio-kafka',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Keeper',
      routeBasePath: 'zio-keeper',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Memberlist',
      routeBasePath: 'zio-memberlist',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Meta',
      routeBasePath: 'zio-meta',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Mock',
      routeBasePath: 'zio-mock',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO NIO',
      routeBasePath: 'zio-nio',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Optics',
      routeBasePath: 'zio-optics',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Profiling',
      routeBasePath: 'zio-profiling',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Lambda',
      routeBasePath: 'zio-lambda',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Metrics Connectors',
      routeBasePath: 'zio-metrics-connectors',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Parser',
      routeBasePath: 'zio-parser',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Prelude',
      routeBasePath: 'zio-prelude',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Process',
      routeBasePath: 'zio-process',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Query',
      routeBasePath: 'zio-query',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Quill',
      routeBasePath: 'zio-quill',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Redis',
      routeBasePath: 'zio-redis',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Rocksdb',
      routeBasePath: 'zio-rocksdb',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Schema',
      routeBasePath: 'zio-schema',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO S3',
      routeBasePath: 'zio-s3',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO SQL',
      routeBasePath: 'zio-sql',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO SQS',
      routeBasePath: 'zio-sqs',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Telemetry',
      routeBasePath: 'zio-telemetry',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Webhooks',
      routeBasePath: 'zio-webhooks',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO SBT',
      routeBasePath: 'zio-sbt',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Direct Style',
      routeBasePath: 'zio-direct',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO Logging',
      routeBasePath: 'zio-logging',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO 2.x Interop Cats 3.x',
      routeBasePath: 'zio2-interop-cats3',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO 2.x Interop Cats 2.x',
      routeBasePath: 'zio2-interop-cats2',
      sidebarPath: 'sidebars.js',
    },
    {
      name: 'ZIO HTTP',
      routeBasePath: 'zio-http',
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