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

## Working on the documentation 

To work on the documentation locally, it is best to run two terminal windows to execute the individual parts of the generator chain:

### Start mdoc

In the first terminal, navigate to the project's checkout directory and start a `sbt` shell. 

Within the sbt shell, execute 

```
docs/mdoc --watch
```

This will monitor the docs directory and execute the mdoc preprocessor whenever a change in one of the markdown documents is detected. 

### Start the docusaurus test server 

In the second terminal, navigate to the `website` directory and 

1. For the first start, run `npm install`
1. Otherwise, just run `yarn start`

This will start a local web server on port 3000, so that the documentation can be viewed at http://localhost:3000/. Now, when changes to one of the markdown files in `docs` are made and saved, the website will update automatically within the browser. 

:::note
Without running the mdoc preprocessor, the changes to the markdown files in `doc` will not trigger an update to the generated site. 
:::