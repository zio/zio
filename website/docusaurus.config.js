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
        { to: 'overview/overview_index', label: 'Overview', position: 'right' },
        { to: 'datatypes/index', label: 'Data Types', position: 'right' },
        { to: 'usecases/usecases_index', label: 'Use Cases', position: 'right' },
        { to: 'howto/index', label: 'How to', position: 'right' },
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
      indexName: 'zio',
      // This keeps the search in the pages of the version the user is currently browsing 
      // see https://docusaurus.io/docs/search
      contextualSearch: true
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
              html: `<a href="/api/zio/" target="_blank">Latest Scaladoc of ZIO</a>`
            },
            {
              html: `<a href="/api-1.x/zio/" target="_blank">Scaladoc of ZIO-1.x</a>`
            },
            {
              label: 'Scaladoc on javadoc.io',
              href: 'https://javadoc.io/doc/dev.zio/'
            }
          ],
        },
        {
          items: [
            {
              title: 'Deployment support',
              html: `
                <a href="https://www.netlify.com">
                  <img src="https://www.netlify.com/img/global/badges/netlify-color-accent.svg" alt="Deploys by Netlify"/>
                </a>
              `
            }
          ]
        }
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
            current: { label: "ZIO 2.x" },
            '1.x': { label: "ZIO 1.x" }
          },
          remarkPlugins: [
            [require('blended-include-code-plugin'), { marker: 'CODE_INCLUDE' }],
            [require('remark-kroki-plugin'), { krokiBase: 'https://kroki.io', lang: "kroki", imgRefDir: "/img/kroki", imgDir: "static/img/kroki" }]
          ],
        },
        theme: {
          customCss: [require.resolve('./src/css/custom.css')],
        },
      },
    ],
  ],
};
