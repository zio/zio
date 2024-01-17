---
id: contributing-to-documentation
slug: contributing-to-documentation
title: "Contributing to The ZIO Documentation"
---

The ZIO documentation is provided by a worldwide community, just like the project itself. So if you are reading this page, you can help us to improve the documentation.

Please read the [Contributor Guideline](contributor-guidelines.md) before contributing to documentation.

## Toolchain

1. The documentation is written in [Markdown](https://en.wikipedia.org/wiki/Markdown) format.
2. During the build process of the microsite, we use [mdoc](https://scalameta.org/mdoc/) to type-check code snippets in Markdown.
3. Our site generator is [Docusaurus](https://docusaurus.io/).

## Editing with GitHub Editor

We encourage contributors to use GitHub's editor for making minor changes to existing documents.

1. On each page, there is a button called _Edit this page_, by clicking this button, we will be redirected to the GitHub editor.

![Edit this page](/img/assets/edit-this-page.png)

2. After editing the page we can check whether our changes have been formatted correctly or not by using the _Preview_ tab.

![GitHub Editor](/img/assets/github-editor.png)

3. We can scroll to the bottom of the page and write a title and description of the work, and then propose the changes by clicking on _Propose changes_.

![Propose changes](/img/assets/propose-changes.png)

4. Our browser will be redirected to a new page titled _Comparing changes_ after clicking the _Propose changes_ button. We can compare our changes proposal and then create a pull request by clicking the _Create pull request_ button.

![Open a pull request](/img/assets/comparing-changes.png)

5. On the new page, we can edit the title and description of our pull request on the new page and finally click _Create pull request_.

![Open a pull request](/img/assets/open-a-pull-request.png)

6. A pull request has been created. Eventually, our work will be reviewed by the rest of the team.

## Editing Documentation Locally

ZIO contributors are encouraged to use this approach for introducing new documentation pages, or when we have lots of improvements on code snippets since we can compile check all changes locally before committing and sending a pull request to the project:

1. First, we need to clone the ZIO project on our machine:

```bash
git clone git@github.com:zio/zio.git
cd zio
```

2. The documentation source code can be found in the `docs` directory and they are all in Markdown format. Now we can begin improving the existing documentation or adding new documentation.

3. To generate documentation site from type-checked markdowns we can use the following command:

```bash
sbt docs/mdoc
```

If one of our snippet codes fails to compile, this command doesn't succeed and will guide us on which line of the documentation caused this error.

It is recommended to run this command with sbt shell with the `--watch` option. This will start a file watcher and livereload on changes. It's useful when we want to see the intermediate results while we are writing documentation:

```bash
sbt
sbt:docs> docs/mdoc --watch
```

4. Finally, by the following command we can serve the microsite locally:

```bash
cd website
npm install
npm run start --watch
```

It will be served on [localhost](http://127.0.0.1:3000/) address.

5. When we are finished with the documentation, we can commit those changes and [create a pull request](contributor-guidelines.md#create-a-pull-request).

## Documentation of Official Libraries

The ZIO documentation is not limited to the ZIO core library. We have several other libraries that are part of the ZIO ecosystem, called _official libraries_. The documentation for these libraries is also part of the ZIO documentation, but the source code for their documentation is not in the [ZIO repository](https://github.com/zio/zio). Instead, they are located in the `docs` directory of each library repository. For example, the documentation for the ZIO Prelude library, which is deployed at [https://zio.dev/zio-prelude](https://zio.dev/zio-prelude), can be found at [https://github.com/zio/zio-prelude/tree/series/2.x/docs](https://github.com/zio/zio-prelude/tree/series/2.x/docs).

To contribute to the documentation of an official library, one must make changes to the documentation source code of that library and then create a pull request to the respective repository. Upon merging that pull request, the documentation for that library will be updated on the zio.dev website with the release of the next version of the library.

Note: After each release of an official library, the documentation for that library will be published to the npm registry. For example, the documentation for the ZIO Prelude library is published on the npm registry at [https://www.npmjs.com/package/@zio.dev/zio-prelude](https://www.npmjs.com/package/@zio.dev/zio-prelude). In the CI of the ZIO repository, after each release,the latest versions of documentations that are published to npm registry will be downloaded and integrated into the zio.dev site.

### Autogenerated README File

The `README.md` file of each library is generated automatically from the `docs/index.md` file. Therefore, to make changes to the `README.md` file, one must update the `docs/index.md` file and then regenerate the `README.md` file using the `sbt docs/generateReadme` command. After making these changes, they can be committed, and a pull request can be created.

The `zio-sbt-website` plugin is used to generate the readme file. It also uses the `build.sbt` to convert the `docs/index.md` to the `README.md` file. So if we want to change anything in the `README.md` file that is not part of the `docs/index.md` file, we must change/add related sbt settings in `build.sbt` file. For more information about this plugin, please refer to the [ZIO SBT](https://zio.dev/zio-sbt) documentation.

### Live Preview

Another point to note is that before creating pull requests for the documentation of an official library, it is advisable to check if everything is fine. To do so, we can locally build the documentation by following these steps:

1. Install the local website using the `sbt docs/installWebsite` command.

2. Run the `sbt docs/previewWebsite` command to serve the website locally.

3. Open the http://localhost:3000/ address in the browser.

By editing the documentation source code of the library, we can see the changes on the local website.

Note: If the `sbt docs/previewWebsite` command fails, please clean the `target` directory for the documentation module and try again from step 1.

## Giving Feedback

Sometimes we see some problem in the documentation, or we have some idea to make better documentation, but we haven't time or knowledge to do that personally. We can discuss those ideas with the community. There are two ways to do this:

1. Using Discord (https://discord.gg/2ccFBr4) is a great way to share our thoughts with others, discuss them, and brainstorm big ideas.
2. Opening a new issue (https://github.com/zio/zio/issues/new) is appropriate when we have actionable ideas, such as reorganizing a specific page of a documentation, or a problem with the current documentation. 
