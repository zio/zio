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
        { type: 'docsVersion', label: 'Overview', position: 'right' },
        { type: 'doc', docId: 'reference/index', label: 'Reference', position: 'right' },
        { type: 'doc', docId: 'guides/index', label: 'Guides', position: 'right' },
        { type: 'doc', docId: 'ecosystem/index', label: 'Ecosystem', position: 'right' },
        { type: 'doc', docId: 'resources/index', label: 'Resources', position: 'right' },
        { type: 'doc', docId: 'about/about_index', label: 'About', position: 'right' },
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
  stylesheets: [
    // see https://atelierbram.github.io/syntax-highlighting/prism/ for examples / customizing
    //'/css/prism/prism-atom-dark.css'
    '/css/prism/prism-material-dark.css'
  ],
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
        theme: {
          customCss: [require.resolve('./src/css/custom.css')],
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
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'caliban-deriving', 
          path: './node_modules/@zio.dev/caliban-deriving',
          routeBasePath: 'caliban-deriving',
          sidebarPath: require.resolve('./node_modules/@zio.dev/caliban-deriving/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'izumi-reflect', 
          path: './node_modules/@zio.dev/izumi-reflect',
          routeBasePath: 'izumi-reflect',
          sidebarPath: require.resolve('./node_modules/@zio.dev/izumi-reflect/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-actors', 
          path: './node_modules/@zio.dev/zio-actors',
          routeBasePath: 'zio-actors',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-actors/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-akka-cluster', 
          path: './node_modules/@zio.dev/zio-akka-cluster',
          routeBasePath: 'zio-akka-cluster',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-akka-cluster/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-aws', 
          path: './node_modules/@zio.dev/zio-aws',
          routeBasePath: 'zio-aws',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-aws/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-config', 
          path: './node_modules/@zio.dev/zio-config',
          routeBasePath: 'zio-config',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-config/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-constraintless', 
          path: './node_modules/@zio.dev/zio-constraintless',
          routeBasePath: 'zio-constraintless',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-constraintless/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-connect', 
          path: './node_modules/@zio.dev/zio-connect',
          routeBasePath: 'zio-connect',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-connect/sidebar.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-crypto', 
          path: './node_modules/@zio.dev/zio-crypto',
          routeBasePath: 'zio-crypto',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-crypto/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-deriving', 
          path: './node_modules/@zio.dev/zio-deriving',
          routeBasePath: 'zio-deriving',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-deriving/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-flow', 
          path: './node_modules/@zio.dev/zio-flow',
          routeBasePath: 'zio-flow',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-flow/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-ftp', 
          path: './node_modules/@zio.dev/zio-ftp',
          routeBasePath: 'zio-ftp',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-ftp/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-http', 
          path: './node_modules/@zio.dev/zio-http',
          routeBasePath: 'zio-http',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-http/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-interop-guava', 
          path: './node_modules/@zio.dev/zio-interop-guava',
          routeBasePath: 'zio-interop-guava',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-interop-guava/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-interop-scalaz', 
          path: './node_modules/@zio.dev/zio-interop-scalaz',
          routeBasePath: 'zio-interop-scalaz',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-interop-scalaz/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-interop-twitter', 
          path: './node_modules/@zio.dev/zio-interop-twitter',
          routeBasePath: 'zio-interop-twitter',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-interop-twitter/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-jdbc', 
          path: './node_modules/@zio.dev/zio-jdbc',
          routeBasePath: 'zio-jdbc',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-jdbc/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-json', 
          path: './node_modules/@zio.dev/zio-json',
          routeBasePath: 'zio-json',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-json/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-kafka', 
          path: './node_modules/@zio.dev/zio-kafka',
          routeBasePath: 'zio-kafka',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-kafka/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-lambda', 
          path: './node_modules/@zio.dev/zio-lambda',
          routeBasePath: 'zio-lambda',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-lambda/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-logging', 
          path: './node_modules/@zio.dev/zio-logging',
          routeBasePath: 'zio-logging',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-logging/sidebar.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-metrics-connectors', 
          path: './node_modules/@zio.dev/zio-metrics-connectors',
          routeBasePath: 'zio-metrics-connectors',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-metrics-connectors/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-parser', 
          path: './node_modules/@zio.dev/zio-parser',
          routeBasePath: 'zio-parser',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-parser/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-prelude', 
          path: './node_modules/@zio.dev/zio-prelude',
          routeBasePath: 'zio-prelude',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-prelude/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-process', 
          path: './node_modules/@zio.dev/zio-process',
          routeBasePath: 'zio-process',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-process/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-query', 
          path: './node_modules/@zio.dev/zio-query',
          routeBasePath: 'zio-query',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-query/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-quill', 
          path: './node_modules/@zio.dev/zio-quill',
          routeBasePath: 'zio-quill',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-quill/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-redis', 
          path: './node_modules/@zio.dev/zio-redis',
          routeBasePath: 'zio-redis',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-redis/sidebar.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-schema', 
          path: './node_modules/@zio.dev/zio-schema',
          routeBasePath: 'zio-schema',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-schema/sidebar.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-s3', 
          path: './node_modules/@zio.dev/zio-s3',
          routeBasePath: 'zio-s3',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-s3/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-sql', 
          path: './node_modules/@zio.dev/zio-sql',
          routeBasePath: 'zio-sql',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-sql/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-sqs', 
          path: './node_modules/@zio.dev/zio-sqs',
          routeBasePath: 'zio-sqs',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-sqs/sidebars.js'),
        },
    ],
    [
      '@docusaurus/plugin-content-docs',
        {
          id: 'zio-telemetry', 
          path: './node_modules/@zio.dev/zio-telemetry',
          routeBasePath: 'zio-telemetry',
          sidebarPath: require.resolve('./node_modules/@zio.dev/zio-telemetry/sidebars.js'),
        },
    ],
  ],
}

module.exports = config;

