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

1. First, we need to fork and clone the ZIO project on our machine. Follow the [Get The Project](contributor-guidelines#get-the-project) instructions to fork the repository and clone your fork.

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


## AI-friendly Markdown Variants

The website publishes `/llms.txt` and `/llms-full.txt` at the site root (per the [llmstxt.org](https://llmstxt.org/) standard) so LLMs and AI agents can discover and ingest the documentation without scraping HTML. It also serves a Markdown variant next to every documentation page — for example, the page rendered at `/reference/schedule/` is also available as plain Markdown at `/reference/schedule.md`.

Two pieces cooperate to produce this:

1. The [`docusaurus-plugin-llms`](https://github.com/rachfop/docusaurus-plugin-llms) plugin generates `llms.txt`, `llms-full.txt`, and a `.md` variant for every documentation source file. Its configuration lives in `website/docusaurus.config.js`.
2. A small inline `llms-markdown-mirror` plugin (also in `website/docusaurus.config.js`) runs after the llms plugin and mirrors the generated Markdown to match the canonical page URL.

The mirror step exists because `docusaurus-plugin-llms` writes each `.md` at the *source file path*, while Docusaurus renders HTML at a different path in two common cases:

- `reference/schedule/index.md` is rendered at `/reference/schedule/`, so the natural Markdown URL is `/reference/schedule.md`, not `/reference/schedule/index.md`.
- `reference/core/zio/zio.md` is rendered at `/reference/core/zio/` (Docusaurus's folder-named-doc convention), so the natural Markdown URL is `/reference/core/zio.md`.

The mirror plugin scans the build output and, for every `<dir>/index.md` or `<dir>/<dir>.md` it finds, creates a sibling `<dir>.md` so appending `.md` to any rendered URL resolves. It never overwrites an existing file, so its output composes cleanly with the llms plugin and with any future upstream fix. This keeps the solution in one place rather than requiring explicit `slug:` frontmatter on every index and folder-named-doc file across the site and the ~30 ecosystem subprojects synced in from npm.

## Giving Feedback

Sometimes we see some problem in the documentation, or we have some idea to make better documentation, but we haven't time or knowledge to do that personally. We can discuss those ideas with the community. There are two ways to do this:

1. Using Discord (https://discord.gg/2ccFBr4) is a great way to share our thoughts with others, discuss them, and brainstorm big ideas.
2. Opening a new issue (https://github.com/zio/zio/issues/new) is appropriate when we have actionable ideas, such as reorganizing a specific page of a documentation, or a problem with the current documentation. 
