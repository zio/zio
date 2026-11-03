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
                // instead of the project name. A project with a single page gets no
                // sidebar of its own, so it is a `doc` item, which makes the ecosystem
                // sidebar the one shown on that page.
                const officialLinks = Object.fromEntries(projects.map(project => [
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
                ]));
                const ecosystemSidebar = categorizedEcosystem(officialLinks);
                const perProjectSidebars = Object.fromEntries(projects.filter(project => !isSinglePage(project.category)).map(project => [
                    `${project.name}-sidebar`,
                    [
                        { type: 'link', label: '← ZIO Ecosystem', href: `/ecosystem/?eco-category=${categoryIdOf(project.name)}` },
                        { ...project.category, collapsible: false, collapsed: false, items: itemsWithoutOwnPage(project.category) }
                    ]
                ]));
                writeSidebarFile('ecosystem-sidebar.js', ecosystemSidebar);
                writeSidebarFile('per-project-sidebars.js', perProjectSidebars);
            });
        }
    };
}
// The id of the category that lists a project in the ecosystem sidebar. The
// "← ZIO Ecosystem" link of the project passes it along, so the ecosystem
// sidebar opens with that category expanded (see
// src/clientModules/ecosystem-sidebar.js).
function categoryIdOf(project) {
    const categories = JSON.parse(fs.readFileSync('ecosystem-categories.json', 'utf8')).categories;
    const category = categories.find(c => c.official.includes(project));
    return category ? category.id : 'other';
}
// The title of a community library's page, used as its sidebar label so that
// the entries of a category can be sorted next to the official ones.
function communityLabel(docId) {
    const source = fs.readFileSync(`../docs/ecosystem/community/${docId}.md`, 'utf8');
    const title = source.match(/^title:\s*"?([^"\n]+?)"?\s*$/m);
    return title ? title[1] : docId;
}
// Groups the libraries by purpose, as listed in `ecosystem-categories.json`.
// A library that is not listed lands in "Other", so a new package or page
// never disappears from the sidebar.
function categorizedEcosystem(officialLinks) {
    const categories = JSON.parse(fs.readFileSync('ecosystem-categories.json', 'utf8')).categories;
    const communityDocs = fs.readdirSync('../docs/ecosystem/community')
        .filter(file => file.endsWith('.md') && file !== 'index.md')
        .map(file => file.replace(/\.md$/, ''));
    const listed = new Set(categories.flatMap(c => c.official));
    const listedCommunity = new Set(categories.flatMap(c => c.community));
    const all = categories.concat({
        id: 'other',
        label: 'Other',
        official: Object.keys(officialLinks).filter(name => !listed.has(name)),
        community: communityDocs.filter(doc => !listedCommunity.has(doc))
    });
    return all
        .map(category => {
        const entries = category.official.map(name => officialLinks[name]).filter(entry => entry !== undefined)
            .concat((category.external || []).map(link => ({
            label: link.label,
            item: { type: 'link', label: link.label, href: link.href, className: 'eco-tag eco-tag-official' }
        })))
            .concat(category.community.map(doc => ({
            label: communityLabel(doc),
            item: { type: 'doc', id: `ecosystem/community/${doc}`, label: communityLabel(doc), className: 'eco-tag eco-tag-community' }
        })))
            .sort((a, b) => a.label.localeCompare(b.label));
        return { category, entries };
    })
        .filter(({ entries }) => entries.length > 0)
        .map(({ category, entries }) => ({
        type: 'category',
        label: category.label,
        className: `eco-category eco-category-${category.id}`,
        collapsed: category.collapsed !== undefined ? category.collapsed : true,
        items: entries.map(entry => entry.item)
    }));
}
function writeSidebarFile(path, sidebar) {
    const content = `const sidebar = ${JSON.stringify(sidebar, null, '  ')}\nmodule.exports = sidebar`;
    fs.writeFile(path, content, err => {
        if (err) {
            console.error(err);
        }
    });
}
// The category of a project links to its own page, so listing that page again
// among its items would show the same entry twice.
function itemsWithoutOwnPage(category) {
    const own = category.link && category.link.type === "doc" ? category.link.id : undefined;
    return category.items.filter(item => item !== own);
}
// Whether a project documents itself on one page, in which case it doesn't
// need a sidebar of its own.
function isSinglePage(config) {
    return leafDocCount(config) <= 1;
}
function leafDocCount(config) {
    if (typeof (config) === "string") {
        return 1;
    }
    else if (isSidebarItemCategoryConfig(config)) {
        return config.items.reduce((count, item) => count + leafDocCount(item), 0);
    }
    return 1;
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
