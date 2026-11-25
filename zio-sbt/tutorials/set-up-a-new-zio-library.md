# Tutorial: Set Up a New ZIO Library

> In this tutorial, we build the sbt setup of a small library called `zio-greeter` from an empty directory. By the end, the project formats and lints with one command, has a documentation website with a generated README, and has a generated GitHub workflow that tests it.

We use all three plugins of ZIO SBT, one per step. The tutorial assumes sbt and Node.js are installed and that the project lives in a Git repository on GitHub.

## Step 1: Create the Build

Let's start with the files sbt needs. In an empty directory, create `project/build.properties` with a current sbt version, then `project/plugins.sbt`:

```scala
// Standard settings, compiler options and the fmt, lint and prepare commands
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.8.12")
```

Next, create `build.sbt`. We set the ZIO version explicitly, so that upgrading the plugin never changes it:

```scala
ThisBuild / zioVersion := "2.1.22"

lazy val root = project
  .in(file("."))
  .settings(stdSettings(name = Some("zio-greeter")))
  .settings(enableZIO())
```

Run `sbt compile`. The welcome banner lists the commands the plugin added, which means the plugin is active.

## Step 2: Write Some Code and Format It

Create `src/main/scala/Greeter.scala`:

```scala
object Greeter {
  def greet(name: String): String = s"Hello, $name!"
}
```

To format the code and apply the scalafix rules, run:

```bash
sbt prepare
```

To check that nothing is left to fix, the way CI will, run `sbt lint`. It passes when `prepare` left nothing to change.

## Step 3: Add the Documentation

Now we add the website plugin. Append it to `project/plugins.sbt`:

```scala
// Builds, previews and publishes the documentation website
addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.8.12")
```

In `build.sbt`, add a `docs` project that depends on the library:

```scala
lazy val docs = project
  .in(file("zio-greeter-docs"))
  .settings(
    moduleName     := "zio-greeter-docs",
    projectName    := "ZIO Greeter",
    mainModuleName := (root / moduleName).value,
    projectStage   := ProjectStage.Development
  )
  .dependsOn(root)
  .enablePlugins(WebsitePlugin)
```

Create `docs/index.md`:

```
---
id: index
title: "Introduction to ZIO Greeter"
sidebar_label: "Introduction"
---

ZIO Greeter greets people.
```

Writing `[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sbt/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-sbt-website_2.12_1.0.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-sbt-website_2.12_1.0) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-sbt-website_2.12_1.0%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-sbt-website_2.12_1.0/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sbt-website_2.12_1.0/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sbt-website_2.12_1.0) [![ZIO SBT](https://img.shields.io/github/stars/zio/zio-sbt?style=social)](https://github.com/zio/zio-sbt)` on its own line of the introduction adds the stage, CI, snapshot and GitHub star badges to the page and to the README. The release and javadoc badges appear once the project has a release.

Compile the docs and create the website:

```bash
sbt docs/compileDocs
sbt docs/installWebsite
```

Add the generated directories to `.gitignore`:

```gitignore
/website/docs
/website/build
/website/node_modules
/website/.docusaurus
```

To preview the website, run `sbt docs/previewWebsite` and open `http://localhost:3000`.

## Step 4: Generate the README

The README comes from `docs/index.md`, so the two never drift apart. Generate it, then check that it is up to date:

```bash
sbt docs/generateReadme
sbt docs/checkReadme
```

## Step 5: Generate the CI Workflow

Finally, we add the CI plugin. Append it to `project/plugins.sbt`:

```scala
// Generates the GitHub Actions workflows from build.sbt
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "0.8.12")
```

Tell the workflow which Scala versions to test:

```scala
ThisBuild / ciTargetScalaVersions := targetScalaVersionsFor(root).value
```

Then generate the workflow files:

```bash
sbt ciGenerateGithubWorkflow
```

The `.github` directory now holds `ci.yml` and the other generated workflows. Commit them together with the rest of the project. From now on a pull request runs the `build`, `lint` and `test` jobs, and `sbt ciCheckGithubWorkflow` tells us when the committed files fall behind `build.sbt`.

## What We Built

We set up one project with the ecosystem plugin for formatting and linting, the website plugin for documentation and the README, and the CI plugin for the workflow. To go further, see the How-to Guides, for example [Enable Netlify Deploy Previews](../how-to-guides/enable-netlify-deploy-previews.md) and [Publish a Release's Docs Again](../how-to-guides/publish-a-releases-docs-again.md).
