---
id: contributing-to-zio-ecosystem
slug: contributing-to-zio-ecosystem
title: "Contributing to the ZIO Ecosystem Projects"
---

The ZIO ecosystem is provided by a worldwide community, just like the project itself. So if you are reading this page, you can help us to improve the ecosystem.

Please read the [Contributor Guideline](contributor-guidelines.md) before contributing to the ecosystem.

## Branching Naming Convention

Until now, the [ZIO core library](https://github.com/zio/zio) has undergone two major releases: 1.x and 2.x. Therefore, the ZIO ecosystem projects may maintain two branches: `series/1.x` and `series/2.x`, with the default branch typically set to `series/2.x`. The `series/1.x` branch accomodates releases compatible with ZIO 1.x, while the `series/2.x` branch is designated for releases compatible with ZIO 2.x.

When considering a contribution to a ZIO ecosystem project, it's essential to determine the targeted ZIO version beforehand. Once identified, we can proceed to checkout the corresponding branch, make changes, and submit a pull request against that specific branch.

## Documentation

The documentation for official libraries is also part of the ZIO documentation. However, the source code for their documentation is not housed within the [ZIO repository](https://github.com/zio/zio). Instead, they are located in the `docs` directory of each library repository.

As an example, the documentation for the ZIO Prelude library, accessible at [https://zio.dev/zio-prelude](https://zio.dev/zio-prelude), can be found within its respective respository at [https://github.com/zio/zio-prelude/tree/series/2.x/docs](https://github.com/zio/zio-prelude/tree/series/2.x/docs).

Each official ZIO project maintains its dedicated documentation project, which is published with every release to the npm registry under the [@zio.dev](https://www.npmjs.com/package/@zio.dev/) organization. Subsequently, these documentation projects are seamlessly integrated into the [ZIO Website](https://zio.dev) during the CI process of [ZIO core](https://github.com/zio/zio) repository.

### Documentation Structure

The base minimum structure of the documentation source code under the `docs` directory of each library repository is as follows:

```shell
~/zio-xyz > tree docs
docs
├── index.md
├── package.json
└── sidebars.js
```

Now, let's delve into each of them.

#### The `package.json` File

Each library's documentation source code is a NPM project that will be published to the NPM registry. So the `package.json` file is required. The file is used to specify the name, description, and license of the documentation project. Example:

```json
{
  "name": "@zio.dev/zio-prelude",
  "description": "ZIO Prelude Documentation",
  "license": "Apache-2.0"
}
```

That is, the name of the documentation project must be `@zio.dev/<library-name>`, and the description and license must be the same as the library. All official libraries will be published under the [`@zio.dev`](https://www.npmjs.com/package/@zio.dev/) scope on the npm registry. For example, the ZIO Prelude's documentation package will be published at [https://www.npmjs.com/package/@zio.dev/zio-prelude](https://www.npmjs.com/package/@zio.dev/zio-prelude). We will discuss more about this on the CI section.

### The `index.md` File

The `index.md` file serves as the main page of the documentation, providing a description of the library and its features. The structure of this file is as follows:

```scala mdoc:passthrough
println(
  """
    |```markdown
    |---
    |id: index
    |title: "Introduction to ZIO XYZ"
    |sidebar_title: "ZIO XYZ"
    |---
    |
    |ZIO XYZ is a library that ...
    |
    |@‎PROJECT_BADGES@
    |
    |## Introduction
    |
    |Key features of ZIO XYZ are:
    |  - Feature 1
    |  - Feature 2
    |  - ...
    |
    |## Installation
    |
    |In order to use this library, we need to add the following line in our `build.sbt` file:
    |
    |```scala
    |libraryDependencies += "dev.zio" %% "zio-xyz" % "@‎VERSION@"
    |```
    |
    |## Example
    |
    |Let's see an example of how to use this library:
    |
    |....
    |```
    |""".stripMargin)
```

Inside the `index.md` file, we can use the `@‎PROJECT_BADGES@` placeholder to add badges to the documentation. The `@‎PROJECT_BADGES@` placeholder will be replaced with the badges of the library. Also, the `@‎VERSION@` placeholder will be replaced with the latest version of the library.

The `index.md` file is the primary source for generating the `README.md` file of the library. Further details on this process will be discussed in the [Generating README File](#generating-readme-file) section.

#### The `sidebars.js` File

Inside the `sidebars.js` file, we can specify the documentation's structure, including the order of pages, the arrangement of sections within each page, and the introduction of sections and subsections. The structure of this file is as follows:

```js
const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO XYZ", // Project Name
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [ ]
    }
  ]
};

module.exports = sidebars;
```

To add a new page to the documentation, in addition to the `index.md` page, we can include a new item in the `items` array. For instance, to add a new page with the id `getting-started`, we can do the following:

```js
const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO XYZ", // Project Name
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [ ]
    }
  ]
};

module.exports = sidebars;
```

To introduce a new section, simply append a new object to the `items` array. Set the `type` field to `category`, the `label` field to the desired section name, the `link` field to the main page's ID corresponding to the section, and finally, populate the `items` field with a list of pages to be included in that section:

```js
const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO XYZ", // Project Name
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        `installation`,
        `getting-started`,
        `basics`,
        {
          type: "category",
          label: "Examples",
          collapsed: false,
          link: { type: "doc", id: "examples/index" },
          items: [
            `examples/example-1`,
            `examples/example-2`
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
```

In the above example, we have introduced a new section called `Examples` which refers to the `examples/index.md` page. Additionally, we have added two pages to this section.

To learn more about the `sidebars.js` file, please refer to the [Docusaurus documentation](https://docusaurus.io/docs/sidebar).

### ZIO SBT Website Plugin

After preparing the documentation source code inside the `docs` directory of the library repository, we are ready to compile the documentation. To do this, we need to install the `zio-sbt-website` plugin.

With this plugin, we gain the capability to enhance our documentation in several ways:

1. **Ensure Type-Checked Code Snippets:** Leverage the power of the [`mdoc`](https://scalameta.org/mdoc/) tool to include type-checked code snippets seamlessly within our documentation.

2. **Effortless README.md Generation:** Streamline our documentation process by generating the `README.md` file automatically from the content inside `docs/index.md` file.

3. **Simplified Documentation Deployment:** Install and preview our documentation website effortlessly.

#### Installing the Plugin

To install the plugin, we need to add the following line to the `project/plugins.sbt` file of the library repository:

```scala
// replace <version> with the latest version currently 0.4.0-alpha.22
addSbtPlugin("dev.zio" % "zio-sbt-website" % "<version>") 
```

To enable the plugin, we suggest adding new submodule to the `build.sbt` file called `docs` and then configuring the plugin for that submodule:

```scala
lazy val root = (project in file("."))
  .settings(publish / skip := true)
  .aggregate(core, docs)

lazy val core = (project in file("zio-xyz"))

lazy val docs = (project in file("zio-xyz-docs"))
  .enablePlugins(WebsitePlugin)
  .settings(projectName := "ZIO XYZ")
  .settings(mainModuleName := (core / moduleName).value)
  .settings(projectStage := ProjectStage.ProductionReady)
```

Let's explain the above settings:

- `projectName`: This setting is used to set the name of the documentation project.
- `mainModuleName`: This setting is used to specify the name of the main module of the library. It is employed to create the `@‎PROJECT_BADGES@` placeholder in the `docs/index.md` file.
- `projectStage`: This setting is used to determine the stage of the documentation project. Possible values are `ProductionReady`, `Development`, `Experimental`, `Research`, `Concept`, and `Deprecated`. It is utilized to create the `@‎PROJECT_STAGE@` placeholder in the `docs/index.md` file. To learn more about these stages, please refer to [this page](https://github.com/zio#project-status).

#### README Auto-Generation

After installing the plugin, we can generate the `README.md` file by running the `sbt docs/generateReadme` command. This command will generate the `README.md` file from the `docs/index.md` file and place it in the root directory of the repository.

:::note
As a contributor to a well-established official library, you don't need to update the `README.md` file every time you make changes to the `docs/index.md` file. The CI of the ZIO repository will take care of this for you. After merging any pull request that has changes to the `docs/index.md` file, the CI of the ZIO repository will automatically create a new pull request to update the `README.md` file.
:::

There are some sbt settings used to generate the `README.md` file, and they have default values. However, if you want to customize them, you can do so. These settings are as follows:

- `readmeBanner`
- `readmeDocumentation`
- `readmeContribution`
- `readmeCodeOfConduct`
- `readmeSupport`
- `readmeMaintainers`
- `readmeCredits`
- `readmeAcknowledgement`
- `readmeLicense`

For example, to change the credits section, we can add the following setting to the `build.sbt` file:

```scala
lazy val docs = (project in file("zio-xyz-docs"))
  .enablePlugins(WebsitePlugin)
  .settings(projectName := "ZIO XYZ")
  .settings(mainModuleName := (core / moduleName).value)
  .settings(projectStage := ProjectStage.ProductionReady)
  .settings(
    readmeCredits :=
      """
        |The creation of this library is deeply influenced by the exploration and  
        | implementation conducted at ABC corporation.""".stripMargin,
  )
```

#### Live Preview

Before creating pull requests for the documentation of an official library, it is advisable to check if everything is fine, especially when there are numerous changes to the documentation. To do so, we can locally build the documentation by following these steps:

1. Install the local website using the `sbt docs/installWebsite` command.

2. Run the `sbt docs/previewWebsite` command to serve the website locally.

3. Open the http://localhost:3000/ address in the browser.

By editing the documentation source code of the library, we can observe the changes on the local website.

:::note
If the `sbt docs/previewWebsite` command fails, please clean the `target` directory for the documentation module and try again from step 1.
:::

### Publishing Documentation

To contribute to the documentation of an official library, one must make changes to the documentation source code of that library and then create a pull request to the respective repository. Upon merging that pull request, the goal is to have the documentation for that library updated on the zio.dev website with the release of the next version of the library.

To achieve this, the only thing that needs to be done from the library repository perspective is to publish the documentation to the npm registry. There are two options for this:

1. Write a manual CI job responsible for publishing the documentation package after each release of the library.
2. Use the ZIO SBT CI plugin to generate the CI jobs automatically, including the job for publishing the documentation package.

#### Manual CI Job

By adding the following CI job to the `.github/workflows/<workflow-name>.yaml` file, we can utilize the `sbt docs/publishToNpm` command provided by the ZIO SBT Website plugin to publish the documentation package to the npm registry:

```yaml
release-docs:
  name: Release Docs
  runs-on: ubuntu-latest
  continue-on-error: false
  needs:
  - release
  if: ${{ ((github.event_name == 'release') && (github.event.action == 'published')) }}
  steps:
  - name: Git Checkout
    uses: actions/checkout@v4.1.1
    with:
      fetch-depth: '0'
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup NodeJs
    uses: actions/setup-node@v4
    with:
      node-version: 20.x
      registry-url: https://registry.npmjs.org
  - name: Publish Docs to NPM Registry
    run: sbt docs/publishToNpm
    env:
      NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
```

The `NPM_TOKEN` is a secret used to publish the documentation to the npm registry. All official libraries under GitHub's [ZIO organization](https://github.com/zio) have access to this secret.

#### Automatic CI Job (Recommended)

The ZIO SBT CI plugin can generate the CI jobs automatically, including the job for publishing the documentation package to the NPM registry.

To do this, first, we need to install the plugin in the `project/plugins.sbt` file of the library repository:

```scala
// replace <version> with the latest version currently 0.4.0-alpha.22
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "<version>") 
```

By adding this line, the plugin will be enabled, and we can use the `sbt ciGenerateGithubWorkflow` command to generate the CI jobs. The generated CI jobs will be placed in the `.github/workflows/ci.yml`.

:::note
After each release of an official library, the documentation for that library will be published to the npm registry. In the CI of the ZIO repository, the latest versions of documentations that are published to the NPM registry will be downloaded and integrated into the zio.dev site.
:::