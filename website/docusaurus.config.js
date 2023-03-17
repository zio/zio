// @ts-check

const path = require("path")

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/vsDark');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO',
  tagline: 'Type-safe, composable asynchronous and concurrent programming for Scala',
  url: 'https://zio.dev',
  baseUrl: '/',
  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.png',
  organizationName: 'zio',
  projectName: 'zio',
  themeConfig: {
    algolia: {
      // The application ID provided by Algolia
      appId: 'IAX8GRSWEQ',

      // Public API key: it is safe to commit it
      apiKey: '6d38dc1ca6f0305c6e883ef79f82523d',

      indexName: 'zio',

      // Optional: see doc section below
      contextualSearch: true,

      // Optional: path for search page that enabled by default (`false` to disable it)
      searchPagePath: 'search',
    },
    docs: {
      sidebar: {
        autoCollapseCategories: true,
      },
    },
    prism: {
      theme: lightCodeTheme,
      darkTheme: darkCodeTheme,
      additionalLanguages: ['json', 'java', 'scala']
    },
    navbar: {
      style: 'dark',
      logo: {
        alt: 'ZIO',
        src: '/img/navbar_brand.png',
      },
      items: [
        { type: 'doc', docId: 'overview/getting-started', label: 'Overview', position: 'left' },
        { type: 'doc', docId: 'reference/index', label: 'Reference', position: 'left' },
        { type: 'doc', docId: 'guides/index', label: 'Guides', position: 'left' },
        { type: 'doc', docId: 'ecosystem/index', label: 'Ecosystem', position: 'left' },
        { type: 'doc', docId: 'resources/index', label: 'Resources', position: 'left' },
        { type: 'doc', docId: 'events/index', label: 'Events', position: 'left' },
        { type: 'doc', docId: 'about/index', label: 'About', position: 'right' },
        {
          type: 'docsVersionDropdown',
          position: 'right',
          dropdownActiveClassDisabled: true,
        },
      ],
    },
    footer: {
      style: 'dark',     links: [
        {
          items: [
            {
              html: `
                <img src="/img/navbar_brand.png" alt="zio" />
            `
            }
          ],
        },
        {
          title: 'Github',
          items: [
            {
              html: `
              <a href="https://github.com/zio/zio">
                <img src="https://img.shields.io/github/stars/zio/zio?style=social" alt="github" />
              </a>
            `
            }
          ],
        },
        {
          title: 'Chat with us on Discord',
          items: [
            {
              html: `
                <a href="https://discord.gg/2ccFBr4">
                  <img src="https://img.shields.io/discord/629491597070827530?logo=discord&style=social" alt="discord"/>
                </a>
              `
            }
          ],
        },
        {
          title: 'Follow us on Twitter',
          items: [
            {
              html: `
                <a href="https://twitter.com/zioscala">
                  <img src="https://img.shields.io/twitter/follow/zioscala?label=Follow&style=social" alt="twitter"/>
                </a>
              `
            }
          ],
        },
        {
          title: 'Additional resources',
          items: [
            {
              label: 'Scaladoc of ZIO',
              href: 'https://javadoc.io/doc/dev.zio/zio_2.12/'
            }
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} ZIO Maintainers - Built with <a href="https://v2.docusaurus.io/">Docusaurus v2</a>`,
    },
  },
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        debug: true,
        docs: {
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
          lastVersion: 'current',
          versions: {
            'current': {
              label: 'ZIO 2.x'
            },
            '1.x': {
              label: 'ZIO 1.x',
              path: 'version-1.x'
            }
          },
          remarkPlugins: [
            [require('blended-include-code-plugin'), { marker: 'CODE_INCLUDE' }],
            [require('remark-kroki-plugin'), { krokiBase: 'https://kroki.io', lang: "kroki", imgRefDir: "/img/kroki", imgDir: "static/img/kroki" }]
          ],
          editUrl: 'https://github.com/zio/zio/edit/series/2.x',
        },
        googleAnalytics: {
          trackingID: 'UA-237088290-2',
          anonymizeIP: true,
        },
        gtag: {
          trackingID: 'G-SH0HNKLNRT',
          anonymizeIP: true,
        },
      },
    ],
  ],
  plugins: [
    [path.join(__dirname, './plugins/zio-ecosystem-docusaurus'), {}],
  ],
//  markdown: {
//    mermaid: true,
//  },
//  themes: ['@docusaurus/theme-mermaid'],
}

module.exports = config;

