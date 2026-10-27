# Overview 

We use [Docusaurus 2](https://v2.docusaurus.io/) as a generator to produce the static web site from markdown documents. 

At the core the website is a collection of markdown documents that are translated to static HTML, which can be hosted on any web server, in our case github pages. [Docusaurus 2](https://v2.docusaurus.io/) uses [Remark.js](https://github.com/gnab/remark) under the covers, which gives the user the option to rely on additional remark plugins in the rendering process. 

## Additional tools 

### Scala MDoc

[Scala mdoc](https://scalameta.org/mdoc/) can be used to run Scala codeblocks through the compiler to ensure that the code examples given in the documentation do compile. Furthermore, 
it provides the option to amend the code block by inserting commented lines with REPL output. 

Mdoc is applied by the sbt mdoc plugin and creates a copy of the markdown files used as input with the Scala code blocks changed according to the mdoc processing rules. 

The mdoc plugin can be invoked via sbt by 

```
sbt docs/mdoc
```

The resulting output is written to a directory within the `website` folder structure, so that it can be picked up by 
docusaurus. 

### Code include plugin 

In many cases we want to reference code either from within our own project or from a URL. Within _Blended ZIO_ we are using the [Include Code Plugin](https://www.npmjs.com/package/blended-include-code-plugin) which allows us to copy code blocks from the file system of from URLs into our documentation. 

### Text to Image generator 

Within the documentation we often have the requirement to produce technical images such as graph visualizations, flow charts, sequence diagrams etc. Rather than using a dedicated graphics program, we prefer to create the images from a textual description. There are many generators that support that. 

The fantastic [Kroki Web Service](https://kroki.io/) provides a unified web service as a common interface to many generators that can create images from various specialized DSL's. 

The [Kroki Image Plugin](https://www.npmjs.com/package/remark-kroki-plugin) is used within _Blended ZIO_ to execute the Kroki web service for code blocks having `kroki` as their language. 

## Overall processing 

### Project layout 

The project documentation is located within the `docs` directory of the projects. All markdown files from this directory will be preprocessed by Scala Mdoc. 

Everything related to the web site is located within the `website` directory. 

### Site generation 

The overall site generation can be triggered by 

```
sbt docs/docusaurusCreateSite
```

First, the content of `docs` is run through Scala Mdoc. This will produce a modified copy of the markdown files in `website/docs`. This will be the primary input to the site generator. 

The markdown input will be run though the chain of Remark processors, the last of which will actually generate the HTML pages. We have configured the plugins above into our Docusaurus 2 config file as below:

```
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
        customCss: [require.resolve('./src/css/custom.css')],
      },
    },
  ],
],
```

## Installation

Before running the documentation locally, install Node.js dependencies:

```bash
cd website
yarn install
```

This needs to be run once to download all npm packages into `node_modules`. Subsequent runs only require `yarn start`.

## Working on the documentation 

To work on the documentation locally, it is best to run two terminal windows to execute the individual parts of the generator chain:

### Start mdoc (Terminal 1)

In the first terminal, navigate to the project's root checkout directory and start a `sbt` shell. 

Within the sbt shell, execute 

```bash
sbt
```

Then within the sbt prompt:

```
docs/mdoc --watch
```

This will monitor the docs directory and execute the mdoc preprocessor whenever a change in one of the markdown documents is detected. 

### Start the docusaurus dev server (Terminal 2)

In the second terminal, navigate to the `website` directory and run:

```bash
yarn start
```

This will start a local web server on port 3000, so that the documentation can be viewed at http://localhost:3000/. 

When changes to markdown files in `docs` are made and saved:
1. MDoc detects the change (Terminal 1)
2. Processes Scala code blocks and outputs to `website/docs`
3. Docusaurus detects the change (Terminal 2)
4. Rebuilds and refreshes the browser automatically

:::note
**Important:** Both terminals must be running for the local preview to work correctly. Without the mdoc preprocessor running in Terminal 1, changes to markdown files in the `docs` directory will not appear on the website served by Terminal 2.
:::

## Production Build

To build the complete website for production:

```bash
# From the project root: the docs, then the API reference (two sbt runs, as CI does)
sbt docs/mdoc
sbt apiDocs

# Then in the website directory
cd website
yarn build
```

The static HTML output will be generated in `website/build/`. This build includes:
- All processed markdown documentation
- API documentation (Scaladoc) at `/api/`, see [API reference at `/api`](#api-reference-at-api)
- Optimized CSS and JavaScript

To preview the production build locally:

```bash
cd website
yarn serve
```

This serves the static build at http://localhost:3000/.

## Tests

```bash
cd website
yarn test
```

Runs the unit tests in `test/` with Node's test runner.

## API reference at `/api`

`sbt apiDocs` (an alias for `++3.x apiReference/generateApiDocs`, see `../build.sbt`) builds one unified Scaladoc
(sbt-unidoc, Scala 3) for every published library module and copies it into `static/api` (gitignored), so `yarn build`
publishes it at `/api/` next to the docs. ZIO contributes its JVM side, since JVM, JS and Native share the public API.
The Scaladoc has a project of its own, `apiReference`, because `docs` is Scala 2.13 only (mdoc compiles the docs
against libraries with no Scala 3 build). The module list is its `ScalaUnidoc / unidoc / unidocProjectFilter`; a new
published module must be added there. Source links point at the commit the docs were built from. CI's `build-website`
job runs `sbt docs/mdoc` and `sbt apiDocs` before `yarn build`.

The task also fixes the generated output (all in `generateApiDocs`):

- Links to other ZIO types stay on `/api`. sbt-api-mappings would otherwise send them to javadoc.io, which has no page
  for a snapshot; the mappings of the modules at this build's version are dropped, other libraries keep theirs.
- Scaladoc's `-dynamic-side-menu` (without it, every page repeats the whole package tree) comes with bugs in
  `scripts/ux.js` that break navigation, patched in place: menu links joined with commas under a sub-path
  (`,api/zio.html`), link clicks that depend on jQuery from a CDN (`$.get`), Back/Forward leaving the menu highlight on
  the page you left, and a URL naming a section (`#attributes`) aborting the page setup, which skipped code
  highlighting. Each patch fails the task when its target text disappears, so a Scaladoc upgrade gets them reviewed.

Scaladoc is a separate generator with its own HTML, stylesheets and scripts, so after `docusaurus build`
`scripts/theme-api.mjs` gives every page in `build/api` the site's look (`scripts/lib/api-theme.mjs`, assets in
`src/api-theme/`). It reads each page from `static/api` and writes it over its copy in `build/api`, so it can be rerun:

- Scaladoc's header is replaced by a copy of the site navbar (logo, the same links on the same sides, the GitHub link,
  onboard-agent button and colour-mode toggle, collapsing into the mobile menu at 996px), keeping the ids Scaladoc's
  scripts look up (`#search-toggle`, `#mobile-menu-toggle`, a hidden version dropdown). Scaladoc's search, drawn as the
  docs' search pill, takes the place of the Algolia search, and the docs version dropdown is left out (the API reference
  has one version). A unit test keeps the links and logo in step with `docusaurus.config.js`.
- URLs have no `.html`, like the docs pages: `/api/zio/stream/ZStream`, not `ZStream.html` (an index page is its
  folder). The files keep their names, since the hosts serve `ZStream.html` for `/…/ZStream`; the relative links in
  every page, the search index (`scripts/searchData.js`) and the side menu's link builder in `scripts/ux.js` drop the
  extension. Old `.html` links still work.
- Nested types get folders: Scaladoc files `Cause.Fail` as `Cause$$Fail.html`, the site serves it at
  `/api/zio/Cause/Fail` (`nestedTypePaths` reads each nested type's parent off the side menu; `relocatePage` rewrites a
  moved page's links, root path and location for its new folder, and every link to it). Objects keep Scaladoc's `$`
  (`Chunk$` is the companion of `Chunk`).
- The site favicon (`favicon` in `docusaurus.config.js`) replaces Scaladoc's `favicon.ico`, and the site font (Inter,
  from Google Fonts, linked as `plugins/google-fonts` links it) replaces Scaladoc's.
- The window scrolls, as on a docs page, so its scrollbar runs the whole height beside a fixed navbar. `api.css` puts
  `#main` back in the page flow and fixes the navbar, sidebar and overlays; `theme.js` sends Scaladoc's `#main.scrollTo`
  calls to the window.
- The index page sends `/api` to `/api/` first thing in `<head>` (with `<base href="/api/">` for the requests the
  browser starts before that runs): without the slash, Scaladoc's relative paths resolve against the site root and the
  page comes up unstyled.
- Scaladoc's "Generated with Scaladoc" footers are removed, and the sidebar runs to the bottom of the window.
- The breadcrumbs become the docs breadcrumbs (home icon, API, the packages as the side menu names them, the page) at
  the top of the text column.
- The side menu starts at `zio` (`checkSideMenu` fails the build if another top-level package appears), which
  `theme.js` keeps unfolded across page loads unless the reader folds it. It is drawn as the docs sidebar: Infima's
  rows, colours and chevron caret, 12px per level, Scaladoc's kind icons, the browser's own scrollbars, no Docs/API
  switcher.
- The columns follow the docs page grid: a 300px sidebar shown down to 997px, a container of at most 1280px, a 75%/25%
  text/contents split with 1rem gutters, and the contents hidden below 997px.
- `src/api-theme/theme.js` replaces Scaladoc's `scripts/theme.js`: the colour mode is the one Docusaurus keeps in
  `localStorage.theme` (light when nothing is stored, as `colorMode` is left at its defaults), so switching it on either
  side carries over. It also keeps the navbar's site links out of Scaladoc's in-place page loader.

`yarn start` serves `static/api` as generated, without this skin.

The navbar and footer link to it with `pathname:///api/`: Scaladoc is plain HTML outside the Docusaurus router, and the
`pathname://` protocol makes the link a full page load and keeps it out of the broken-link checker. Without a prior
`sbt apiDocs` the build still succeeds, but `/api/` is a 404.
