# Introduction to ZIO SBT

> _ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sbt/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-sbt-website_2.12_1.0.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-sbt-website_2.12_1.0) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-sbt-website_2.12_1.0%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-sbt-website_2.12_1.0/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sbt-website_2.12_1.0/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sbt-website_2.12_1.0) [![ZIO SBT](https://img.shields.io/github/stars/zio/zio-sbt?style=social)](https://github.com/zio/zio-sbt)

## Overview

ZIO SBT is made of five modules, three of which are sbt plugins that a ZIO project enables in its build:

- **ZIO SBT Ecosystem** (`zio-sbt-ecosystem`) holds the standard settings, compiler options and commands (`fmt`, `lint`, `prepare`) that every ZIO project shares.
- **ZIO SBT Website** (`zio-sbt-website`) compiles the documentation in `docs/` with mdoc, builds and previews the Docusaurus website, publishes the docs to npm, and generates the `README.md`.
- **ZIO SBT CI** (`zio-sbt-ci`) generates the GitHub Actions workflows of a project from its `build.sbt`.
- **ZIO SBT GitHub Actions** (`zio-sbt-githubactions`) is the model of GitHub workflows (`Workflow`, `Job`, `Step`, ...) that ZIO SBT CI is built on. It comes with `zio-sbt-ci`, so it needs no installation of its own.
- **ZIO SBT Source** (`zio-sbt-source`) is a library, not an sbt plugin, that embeds source files and evaluated expressions into the documentation.

## Installation

Add the following lines to your `project/plugins.sbt` file:

```scala
// Standard settings, compiler options and the fmt, lint and prepare commands
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.8.12")

// Generates the GitHub Actions workflows from build.sbt
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "0.8.12")

// Builds, previews and publishes the documentation website
addSbtPlugin("dev.zio" % "zio-sbt-website"   % "0.8.12")
```

Then you can enable them by using the following code in your `build.sbt` file:

```scala
enablePlugins(
  WebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)
```

:::note
Always try to keep the SBT version specified in the `project/build.properties` file up to date to ensure compatibility with the ZIO SBT plugins.
:::

To embed source files into the documentation, also add `zio-sbt-source`, which is a regular library dependency and belongs in the `build.sbt` of the docs project:

```scala
libraryDependencies += "dev.zio" %% "zio-sbt-source" % "0.8.12"
```

## Where to Go Next

Read [Getting Started](https://zio.dev/zio-sbt/getting-started) to set up a project, the [Reference](https://zio.dev/zio-sbt/reference/) for every setting, task and command, and the [How-to Guides](https://zio.dev/zio-sbt/how-to-guides/publish-a-releases-docs-again) for common CI tasks.
