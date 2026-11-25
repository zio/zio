# ZIO SBT Website

> `WebsitePlugin` compiles the documentation in the `docs` directory with mdoc, scaffolds, builds and previews a Docusaurus website, publishes the docs to npm and generates the `README.md` of the project. `UnifiedScaladocPlugin` is a small companion that publishes one Scaladoc for several modules.

To install the plugin, add `addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.8.12")` to `project/plugins.sbt` and enable it on the docs project with `enablePlugins(WebsitePlugin)`, as described in [Installation](../index.md#installation).

## Tasks

The plugin defines these tasks. Run them on the docs project, for example `sbt docs/compileDocs`.

| Task                      | Type        | Description                                                                                                                                                  |
| ------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `compileDocs`             | `InputKey`  | Compiles the documentation inside `docs` with mdoc into `website/docs`. With `--watch` it recompiles on every change                                         |
| `installWebsite`          | `TaskKey`   | Creates the website for the project inside `website` with `@zio.dev/create-zio-website`, pinned by `createZioWebsiteVersion`, then runs `npm install`. It does nothing when `website/package.json` exists |
| `buildWebsite`            | `TaskKey`   | Runs `installWebsite` and `compileDocs`, installs the npm dependencies if `node_modules` is missing, then builds the site into `website/build`               |
| `previewWebsite`          | `TaskKey`   | Compiles the docs with `--watch` and runs `npm run start`, which serves the website on `http://localhost:3000` and reloads it when the docs change            |
| `checkDocsOnFreshWebsite` | `TaskKey`   | Builds the compiled docs in a freshly scaffolded website, the way the ZIO website builds them. It is skipped when the project has no committed website       |
| `publishToNpm`            | `InputKey`  | Compiles the docs and publishes `website/docs` to npm under the version given by `docsVersion`                                                               |
| `publishSnapshotToNpm`    | `InputKey`  | Compiles the docs and publishes them with the project version under the `snapshot` dist-tag, replacing `+` with `--` in the version                          |
| `publishHashverToNpm`     | `InputKey`  | Compiles the docs and publishes them with a hash version, a date plus the short git hash, under the `hash` dist-tag                                          |
| `generateReadme`          | `TaskKey`   | Generates `README.md` from `docs/index.md` and the `readme*` settings                                                                                        |
| `checkReadme`             | `TaskKey`   | Runs `generateReadme` and fails when `git diff` shows that `README.md` changed                                                                               |

`publishToNpm` puts a prerelease version, such as `1.0.0-beta.1`, under its own dist-tag (`beta`) only when the `latest` tag already points at a stable release. Otherwise the version goes to `latest`, published with an explicit `--tag latest` because npm refuses a prerelease without a tag. The `publishToNpm`, `publishSnapshotToNpm` and `publishHashverToNpm` tasks need `scmInfo` to be set, because they record the repository URL in the package.

The `website/docs` directory is generated output, not source. `mdocOut` points there, so `compileDocs`, and anything that runs it, including `generateReadme`, writes compiled documentation into it. To keep it out of version control, add these lines to `.gitignore`:

```gitignore
/website/docs
/website/build
/website/node_modules
/website/.docusaurus
```

Committing `website/docs` leaves a `website` directory that holds generated output but no Docusaurus scaffold, and `buildWebsite` then has no `package.json` to install from.

## Settings

| Setting                   | Type                    | Default                                      | Description                                                                                                      |
| ------------------------- | ----------------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `websiteDir`              | `Path`                  | `<root>/website`                             | The website directory                                                                                            |
| `projectName`             | `String`                |                                              | The project name, such as `ZIO SBT`, used in the README title and badges. Set it on the docs project             |
| `mainModuleName`          | `String`                |                                              | The main module, such as `zio-sbt`, used for the artifact in badges. Set it on the docs project                  |
| `projectStage`            | `ProjectStage`          |                                              | The stage badge: `ProductionReady`, `Development`, `Experimental`, `Research`, `Concept` or `Deprecated`         |
| `projectHomePage`         | `String`                |                                              | Declared by the plugin, but nothing reads it. The README documentation link uses sbt's `homepage` instead       |
| `badgeArtifactId`         | `String`                | `mainModuleName` plus `_` plus the Scala binary version | The artifact id in the release, snapshot and javadoc badges. Override it for artifacts with other coordinates, such as sbt plugins (`name_2.12_1.0`) |
| `ciWorkflowName`          | `String`                | `"CI"`                                       | The name of the CI workflow that the CI badge points at. This is not the key that names the generated workflow; that is `ciWorkflowTitle` of [ZIO SBT CI](./ci.md) |
| `docsDependencies`        | `Seq[ModuleID]`         | `Seq.empty`                                  | Dependencies that the docs project adds to `libraryDependencies`                                                 |
| `docsVersioningScheme`    | `VersioningScheme`      | `VersioningScheme.SemanticVersioning`        | `SemanticVersioning` or `HashVersioning`                                                                         |
| `docsVersion`             | `String`                | derived from `docsVersioningScheme`          | The version of the docs npm package: the latest release with semantic versioning, a date and short git hash with hash versioning |
| `createZioWebsiteVersion` | `String`                | `"0.1.0"`                                    | The version of the `@zio.dev/create-zio-website` npm package that scaffolds the website                          |
| `npmToken`                | `String`                |                                              | Declared by the plugin, but its tasks do not read it. Publishing uses the credentials of the `npm` command       |

`projectName` and `mainModuleName` have no default, so the docs project must set them. `projectStage` is set with an object, for example `ProjectStage.ProductionReady`.

## mdoc Variables

The plugin adds these variables to `mdocVariables`, so a documentation page can write them between `@` signs:

| Variable           | Value                                                                                         |
| ------------------ | --------------------------------------------------------------------------------------------- |
| `0.8.12`        | The latest release version, or the project version when there is no release                  |
| `0.8.12` | The latest release version, or `NOT RELEASED YET`                                            |
| `0.8.12` | The current project version                                                                 |
| `[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sbt/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-sbt-website_2.12_1.0.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-sbt-website_2.12_1.0) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-sbt-website_2.12_1.0%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-sbt-website_2.12_1.0/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sbt-website_2.12_1.0/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sbt-website_2.12_1.0) [![ZIO SBT](https://img.shields.io/github/stars/zio/zio-sbt?style=social)](https://github.com/zio/zio-sbt)` | The stage, CI, snapshot and GitHub star badges, plus the release and javadoc badges once a release exists |

The plugin also passes `--markdown-extensions` for `md`, `html` and `mdx` to mdoc, so `docs/index.mdx` is processed like `docs/index.md`.

## README Settings

`generateReadme` builds `README.md` from the introduction in `docs/index.md` and these settings. Each one is a `String` with Markdown content, except `readmeShowTitle`. An empty string omits the Acknowledgement, Credits and Maintainers sections. The other sections are always written, so an empty string leaves their heading with no content.

| Setting                | Default                                                                          | Section it fills                                           |
| ---------------------- | -------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `readmeBanner`         | `""`                                                                             | A banner above the introduction                            |
| `readmeShowTitle`      | `None`                                                                           | Whether the README starts with `# <projectName>`. `None` shows the title unless `readmeBanner` is set. The type is `Option[Boolean]` |
| `readmeDocumentation`  | A link to the `homepage` setting of the build                                    | Documentation                                              |
| `readmeContribution`   | A link to the ZIO contributor's guide                                            | Contributing                                               |
| `readmeCodeOfConduct`  | A link to the ZIO code of conduct                                                | Code of Conduct                                            |
| `readmeSupport`        | A Discord chat badge and link                                                    | Support                                                    |
| `readmeLicense`        | A link to the `LICENSE` file                                                     | License                                                    |
| `readmeAcknowledgement`| `""`                                                                             | Acknowledgement                                            |
| `readmeCredits`        | `""`                                                                             | Credits                                                    |
| `readmeMaintainers`    | `""`                                                                             | Maintainers                                                |

A README setting can be appended to as well as replaced:

```scala
readmeContribution := readmeContribution.value + "\n\nRun `sbt prepare` before you submit a pull request."
```

## Unified Scaladoc

`UnifiedScaladocPlugin` requires the sbt-unidoc `ScalaUnidocPlugin` and has `noTrigger`, so a project enables it explicitly. It sets `Compile / doc` to `ScalaUnidoc / doc`, so `doc` produces one Scaladoc for every module that unidoc includes:

```scala
lazy val docs = project
  .enablePlugins(WebsitePlugin, UnifiedScaladocPlugin)
  .settings(ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core))
```
