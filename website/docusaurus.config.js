module.exports = {
  title: 'ZIO',
  tagline: 'Type-safe, composable asynchronous and concurrent programming for Scala',
  url: 'https://zio.dev',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.ico',
  organizationName: 'zio', // Usually your GitHub org/user name.
  projectName: 'zio', // Usually your repo name.
  themeConfig: {
    prism: {
      theme: require('prism-react-renderer/themes/vsDark'),
      additionalLanguages: ['java', 'scala'],
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
        { to: 'services/index', label: 'Services', position: 'right' },
        { to: 'usecases/usecases_index', label: 'Use Cases', position: 'right' },
        { to: 'howto/index', label: 'How to', position: 'right' },
        { to: 'resources/index', label: 'Resources', position: 'right' },
        { to: 'about/about_index', label: 'About', position: 'right' },
      ],
    },
    algolia: {
      apiKey: '0c94b59071da7001757d08ab43d9e033',
      indexName: 'zio',
      contextualSearch: true,
      searchParameters: {},
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
  // doctag<configure>
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: require.resolve('./sidebars.js'),
          remarkPlugins: [
            [require('blended-include-code-plugin'), { marker: 'CODE_INCLUDE' }],
            [require('remark-kroki-plugin'), { krokiBase: 'https://kroki.io', lang: "kroki", imgRefDir: "/img/kroki", imgDir: "static/img/kroki" }]
          ],
        },
        theme: {
          customCss: [
            require.resolve('./src/css/custom.css'),
            //require.resolve('./node_modules/prism-themes/themes/prism-cb.css')
          ],
        },
      },
    ],
  ],
  // end:doctag<configure>
};
