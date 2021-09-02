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
        {
          href: 'https://github.com/zio/zio',
          label: 'GitHub',
          position: 'right',
        },
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
          title: 'About',
          items: [
            {
              label: 'Andreas Gies',
              to: '#',
            },
            {
              label: 'Legal',
              to: '#',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Discord',
              href: '#',
            },
            {
              label: 'Discussions',
              href: '#',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'Atom',
              to: '#',
            },
            {
              label: 'RSS',
              to: '#',
            },
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
