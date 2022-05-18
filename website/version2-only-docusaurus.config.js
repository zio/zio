module.exports = {
  title: 'ZIO',
  tagline: 'Type-safe, composable asynchronous and concurrent programming for Scala',
  url: 'https://zio.dev',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.png',
  organizationName: 'zio',
  projectName: 'zio',
  themeConfig: {
    prism: {
      // In case we want to use one of the json packaged themes, we can simply require those 
      //theme: require('prism-react-renderer/themes/vsDark'),

      // if we want to use any of the styles in '/static/css/prism' we have to 
      // use an empty theme config. The stylesheet must then be included in the stylesheets 
      // section below.
      // The CSS stylesheets are included from  https://github.com/PrismJS/prism-themes.git 
      theme: { plain: [], styles: [] },
      additionalLanguages: ['json', 'java', 'scala'],
    },
    navbar: {
      style: 'dark',
      logo: {
        alt: 'ZIO',
        src: '/img/navbar_brand.png',
      },
      items: [
        { to: 'overview/index', label: 'Overview', position: 'right' },
        { to: 'datatypes/index', label: 'Data Types', position: 'right' },
        { to: 'guides/index', label: 'Guides', position: 'right' },
        { to: 'resources/index', label: 'Resources', position: 'right' },
        { to: 'about/index', label: 'About', position: 'right' },
        {
          type: 'docsVersionDropdown',
          position: 'right',
          dropdownActiveClassDisabled: true,
        },
      ],
    },
    algolia: {
      apiKey: '0c94b59071da7001757d08ab43d9e033',
      indexName: 'zio'
    },
    footer: {
      style: 'dark',
      links: [
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
  stylesheets: [
    // see https://atelierbram.github.io/syntax-highlighting/prism/ for examples / customizing
    //'/css/prism/prism-atom-dark.css'
    '/css/prism/prism-material-dark.css'
  ],
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
          lastVersion: "current",
          versions: {
            current: { label: "ZIO 2.x" }
          },
          remarkPlugins: [
            [require('blended-include-code-plugin'), { marker: 'CODE_INCLUDE' }],
            [require('remark-kroki-plugin'), { krokiBase: 'https://kroki.io', lang: "kroki", imgRefDir: "/img/kroki", imgDir: "static/img/kroki" }]
          ],
          editUrl: 'https://github.com/zio/zio/edit/series/2.x',
        },
        theme: {
          customCss: [require.resolve('./src/css/custom.css')],
        },
      },
    ],
  ],
};