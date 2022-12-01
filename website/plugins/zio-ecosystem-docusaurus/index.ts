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

          zioProjects().forEach(project => {
            copy(`node_modules/@zio.dev/${project}`, `./docs/${project}`)
          })

          const result: SidebarItemConfig[] =
            zioProjects()
              .map(project => {
                console.log(project)
                var r = require(`@zio.dev/${project}/sidebars`)
                  .sidebar
                  .map(c => mapConfig(c, `${project}/`))
                return r
              })

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

function zioProjects(): string[] {
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
    console.log('success!')
  } catch (err) {
    console.error(err)
  }
}


function isSidebarItemCategoryConfig(item: SidebarItemConfig): item is SidebarItemCategoryConfig {
  return (item as SidebarItemCategoryConfig).items !== undefined
}

function mapConfig(config: SidebarItemConfig, prefix: string) {
  if (typeof (config) === "string") {
    return prefix + config
  } else if (isSidebarItemCategoryConfig(config)) {
    return {
      ...config,
      link: config.link ? { ...config.link, 'id': prefix + (config.link as SidebarItemCategoryLinkDoc).id } : undefined,
      items:
        ((config as SidebarItemCategoryConfig).items as SidebarItemConfig[])
          .map(item => mapConfig(item, prefix))
    }
  }
}

export default zioEcosystemPlugin