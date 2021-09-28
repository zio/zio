---
id: contributing-to-documentation
title: "Contributing to The ZIO Documentation"
---

The ZIO documentation is provided by a worldwide community, just like the project itself. So if you are reading this page, you can help us to improve the documentation.

Documentation source code can be found in [ZIO's repository on GitHub under the `docs` directory](https://github.com/zio/zio/tree/master/docs).

Please read the [Contributor Guideline](contributing.md) before contributing to documentation.

## Editing Documentation Locally

First, we need to clone the ZIO project on our machine:

```bash
git clone git@github.com:zio/zio.git
cd zio
```

To generate documentation site from type-checked markdowns we can use the following command:

```bash
sbt docs/mdoc
```

If one of our snippet codes fails to compile, this command doesn't succeed and will guide us on which line of the documentation caused this error.

It is recommended to run this command with sbt shell with the `--watch` option. This will start a file watcher and livereload on changes. It's useful when we want to see the intermediate results while we are writing documentation:

```bash
sbt
sbt:docs> docs/mdoc --watch
```

And finally, by the following command serve the microsite locally:

```bash
cd website
npm install
npm run start --watch
```

It will be served on [localhost](http://127.0.0.1:3000/) address.
