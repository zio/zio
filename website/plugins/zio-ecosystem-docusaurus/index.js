"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
Object.defineProperty(exports, "__esModule", { value: true });
const fs = __importStar(require("fs"));
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
function zioEcosystemPlugin(context, options) {
    return {
        name: 'zio-ecosystem-plugin',
        extendCli(cli) {
            cli.command('generate-sidebar')
                .description("This command generates a sidebar file for the ZIO ecosystem")
                .action(() => {
                (zioNpmProjects()).forEach(project => {
                    copy(`node_modules/@zio.dev/${project}`, `./docs/${project}`);
                });
                function categoryTemplate(name, items) {
                    return {
                        type: 'category',
                        label: name,
                        link: { type: "doc", id: "index" },
                        items: items
                    };
                }
                const projects = oldStyledZioProjects
                    // If the sidebar is simple and doesn't have label
                    .filter(e => (require(`@zio.dev/${e.routeBasePath}/${e.sidebarPath}`).sidebar[0] == "index"))
                    .map(project => ({
                    name: project.routeBasePath,
                    category: mapConfig(categoryTemplate(project.name, require(`@zio.dev/${project.routeBasePath}/${project.sidebarPath}`)
                        .sidebar
                        .filter(e => e != "index")), `${project.routeBasePath}/`)
                }))
                    .concat(zioNpmProjects()
                    // If the sidebar has all the metadata including the label
                    .filter(e => {
                    return require(`@zio.dev/${e}/sidebars.js`).sidebar[0].label !== undefined;
                })
                    .map(project => ({
                    name: project,
                    category: mapConfig(require(`@zio.dev/${project}/sidebars.js`).sidebar[0], `${project}/`)
                })))
                    .sort((a, b) => a.category.label < b.category.label ? -1 : a.category.label > b.category.label ? 1 : 0);
                // Plain links rather than `ref` items: a `ref` item takes its label
                // from the doc's `sidebar_label` ("Introduction", "Getting Started")
                // instead of the project name.
                const ecosystemSidebar = projects.map(project => ({
                    type: 'link',
                    label: project.category.label,
                    href: '/' + entryDocId(project.category).replace(/index$/, '')
                }));
                const perProjectSidebars = Object.fromEntries(projects.map(project => [
                    `${project.name}-sidebar`,
                    [
                        { type: 'link', label: '← ZIO Ecosystem', href: '/ecosystem/' },
                        { ...project.category, collapsible: false, collapsed: false }
                    ]
                ]));
                writeSidebarFile('ecosystem-sidebar.js', ecosystemSidebar);
                writeSidebarFile('per-project-sidebars.js', perProjectSidebars);
            });
        }
    };
}
function writeSidebarFile(path, sidebar) {
    const content = `const sidebar = ${JSON.stringify(sidebar, null, '  ')}\nmodule.exports = sidebar`;
    fs.writeFile(path, content, err => {
        if (err) {
            console.error(err);
        }
    });
}
// The doc a project's entry in the ecosystem sidebar should open: the
// category's own page if it has one, otherwise its first doc.
function entryDocId(config) {
    if (typeof (config) === "string") {
        return config;
    }
    else if (isSidebarItemCategoryConfig(config)) {
        if (config.link && config.link.type === "doc") {
            return config.link.id;
        }
        for (const item of config.items) {
            const id = entryDocId(item);
            if (id)
                return id;
        }
    }
    return undefined;
}
function zioNpmProjects() {
    return Object
        .entries((JSON.parse(fs.readFileSync('package.json', 'utf8'))).dependencies)
        .filter(([key, _]) => key.startsWith("@zio.dev"))
        .map(([key, _]) => key.split("/")[1])
        .sort();
}
function copy(srcDir, destDir) {
    const fs = require('fs-extra');
    try {
        // Drop files left over from a previous package version
        fs.removeSync(destDir);
        fs.copySync(srcDir, destDir, { overwrite: true || false });
    }
    catch (err) {
        console.error(err);
    }
}
const oldStyledZioProjects = [
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
];
function isSidebarItemCategoryConfig(item) {
    return item.items !== undefined;
}
function mapConfig(config, prefix) {
    if (typeof (config) === "string") {
        return prefix + config;
    }
    else if (isSidebarItemCategoryConfig(config)) {
        return {
            ...config,
            collapsed: true,
            link: config.link ? { ...config.link, 'id': prefix + config.link.id } : undefined,
            items: config.items
                .map(item => mapConfig(item, prefix))
        };
    }
}
exports.default = zioEcosystemPlugin;
