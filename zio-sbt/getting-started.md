# Getting Started with ZIO SBT

> This page takes an sbt project from nothing to a formatted, linted build with a generated CI workflow. Each step uses one of the plugins listed in the [introduction](./index.md).

## Prerequisites

We need an sbt build with a `project/build.properties` file that names a current sbt version. The installation steps are in [Installation](./index.md#installation).

## Step 1: Add the Plugins

To add the plugins, put these lines in `project/plugins.sbt`:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.8.12")

addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "0.8.12")

addSbtPlugin("dev.zio" % "zio-sbt-website"   % "0.8.12")

resolvers ++= Resolver.sonatypeOssRepos("public")
```

The `resolvers` line is only needed to use SNAPSHOT versions of the plugins.

## Step 2: Configure the Build

To apply the standard ZIO settings, set the ZIO version explicitly and call `stdSettings` in the build:

```scala
ThisBuild / zioVersion := "2.1.22"

lazy val root = project
  .in(file("."))
  .settings(stdSettings(name = Some("my-library")))
  .settings(enableZIO())
```

`zioVersion` has a default, but the default changes with every release of `zio-sbt-ecosystem`, so set it explicitly. The other settings are described in the [ZIO SBT Ecosystem reference](./reference/ecosystem.md).

## Step 3: Format and Lint

The ecosystem plugin adds the `fmt`, `lint` and `prepare` commands. To format all sources and apply scalafix rules before a pull request:

```bash
sbt prepare
```

To check that formatting and scalafix are clean, the same way CI does:

```bash
sbt lint
```

## Step 4: Generate the CI Workflow

The CI plugin generates `.github/workflows/ci.yml` and the other workflow files from `build.sbt`:

```bash
sbt ciGenerateGithubWorkflow
```

Commit the generated files and never edit them by hand. The [ZIO SBT CI reference](./reference/ci.md) lists the generated files, the default jobs and every setting that changes them.

:::note
To use the CI plugin, install the [ZIO Assistant](https://github.com/apps/zio-assistant) GitHub App on the repository.
:::

## Step 5: Build the Documentation

The website plugin compiles the documentation in `docs/` and scaffolds the Docusaurus website. To compile the docs, then create the website the first time:

```bash
sbt docs/compileDocs
sbt docs/installWebsite
```

To preview the website on `http://localhost:3000`:

```bash
sbt docs/previewWebsite
```

The compiled output goes to `website/docs`, which is generated and must stay out of version control. The [ZIO SBT Website reference](./reference/website.md) shows the `.gitignore` entries.

## Next Steps

To see the whole path with a worked project, follow the [tutorial](./tutorials/set-up-a-new-zio-library.md). To solve a specific problem, pick a guide from the How-to Guides section.
