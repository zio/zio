const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

const config = {
  "title": "ZIO",
  "tagline": "Type-safe, composable asynchronous and concurrent programming for Scala",
  "url": "https://zio.dev",
  "baseUrl": "/",
  "organizationName": "zio",
  "projectName": "zio",
  "scripts": [
    "https://buttons.github.io/buttons.js"
  ],
  "favicon": "img/favicon.png",
  "customFields": {
    "separateCss": [
      "api"
    ],
    "users": []
  },
  "onBrokenLinks": "log",
  "onBrokenMarkdownLinks": "log",
  "presets": [
    [
      "@docusaurus/preset-classic",
      {
        "docs": {
          "path": "./docs",
           routeBasePath: '/',
          "showLastUpdateAuthor": true,
          "showLastUpdateTime": true,
          "editUrl": "https://github.com/zio/zio/edit/master/docs/",
          sidebarPath: require.resolve('./sidebars.js'),
      lastVersion: 'current',
      versions: {
        current: {
          label: 'ZIO 1.0.x',
        },
      },
        },
        "blog": {},
        "theme": {
          "customCss": "./src/css/customTheme.css"
        }
      }
    ]
  ],
  "plugins": [],
  "themeConfig": {
    "navbar": {
      "title": "ZIO",
      "logo": {
        "src": "img/navbar_brand.png"
      },
      "items": [
        { type: 'doc', docId: 'overview/overview_index', label: 'Overview', position: 'left' },
        { type: 'doc', docId: 'reference/index', label: 'Reference', position: 'left' },
        { type: 'doc', docId: 'guides/index', label: 'Guides', position: 'left' },
        { type: 'doc', docId: 'resources/index', label: 'Resources', position: 'left' },
        { type: 'doc', docId: 'about/about_index', label: 'About', position: 'left' },
        {
          type: 'docsVersionDropdown',
          position: 'right',
          dropdownActiveClassDisabled: true,
        }
      ]
    },
    "footer": {
      "links": [],
      "copyright": "Copyright © 2023 ZIO Maintainers",
      "logo": {
        "src": "img/navbar_brand.png"
      }
    }
  }
}

module.exports = config;
