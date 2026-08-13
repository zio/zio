# ZIO SBT

> _ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

_ZIO SBT_ contains multiple sbt plugins that are useful for ZIO projects. It provides high-level SBT utilities that simplify the development of ZIO applications.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sbt/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-sbt-website_2.12_1.0.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-sbt-website_2.12_1.0) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-sbt-website_2.12_1.0%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-sbt-website_2.12_1.0/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sbt-website_2.12_1.0/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sbt-website_2.12_1.0) [![ZIO SBT](https://img.shields.io/github/stars/zio/zio-sbt?style=social)](https://github.com/zio/zio-sbt)

## Installation

Add the following lines to your `project/plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ecosystem" % "0.7.0")
addSbtPlugin("dev.zio" % "zio-sbt-ci"        % "0.7.0")
addSbtPlugin("dev.zio" % "zio-sbt-website"   % "0.7.0")
```

Then you can enable them by using the following code in your `build.sbt` file:

```scala
enablePlugins(
  ZioSbtWebsitePlugin,
  ZioSbtEcosystemPlugin,
  ZioSbtCiPlugin
)
```

:::note
Always try to keep the SBT version specified in the `project/build.properties` file up to date to ensure compatibility with the ZIO SBT plugins.
:::

## ZIO SBT Ecosystem

ZIO SBT Ecosystem plugin is an sbt plugin that provides a set of sbt settings and tasks that are very common and useful for configuring and managing ZIO projects. It is designed help developers to quickly set up a new ZIO project with a minimal amount of effort.

This pluging provides the following settings with default values:

- scala212
- scala213
- scala3

The default values are the latest stable versions of Scala 2.12, 2.13, and Scala 3. All of these settings are of type `String` and can be overridden by the user.

By having these settings, then we can use them in other sbt settings. For example, we can use them to define the `crossScalaVersions` setting:

```scala
crossScalaVersions := Seq(scala212.value, scala213.value, scala3.value)
```

There are also some other settings that are useful for configuring the projects:

- `stdSettings`— a set of standard settings which are common for every ZIO project, which includes configuring:
  - silencer plugin
  - kind projector plugin
  - cross project plugin
  - scalafix plugin
  - java target platform
- `enableZIO`- a set of ZIO related settings such as enabling zio streams and ZIO test framework.
- `jsSettings`, `nativeSettings`- common platform specific settings for Scala.js and Scala Native.

It also provides some helper methods that are useful for configuring a compiler option for a specific Scala version:

- `optionsOn`
- `optionsOnExcept`
- `optionsOnOrElse`
- `addOptionsOn`
- `addOptionsOnOrElse`
- `addOptionsOnExcept`

And the same for adding a dependency for a specific Scala version:

- `dependenciesOn`
- `dependenciesOnExcept`
- `dependenciesOnOrElse`
- `addDependenciesOn`
- `addDependenciesOnExcept`
- `addDependenciesOnOrElse`

## ZIO SBT Website

ZIO SBT Website is an SBT plugin that has the following tasks:

- `sbt compileDocs`— compile documentation inside `docs` directory. The compilation result will be inside `website/docs` directory.
- `sbt installWebsite`— creates a website for the project inside the `website` directory.
- `sbt previewWebsite`— runs a local webserver that serves documentation locally on http://localhost:3000. By changing the documentation inside the `docs` directory, the website will be reloaded with new content.
- `sbt publishToNpm`— publishes documentation inside the `docs` directory to the npm registry.
- `sbt generateReadme`— generate README.md file from `docs/index.md` and sbt setting keys.

## ZIO SBT CI Plugin

ZIO SBT CI is an sbt plugin which generates a GitHub workflow for a project, making it easier to set up continuous integration (CI) pipelines for Scala projects. With this plugin, developers can streamline their development workflow by automating the testing and deployment process, reducing manual effort and errors. The plugin is designed to work seamlessly with sbt, the popular build tool for Scala projects, and integrates smoothly with GitHub Actions, the CI/CD platform provided by GitHub.

ZIO SBT CI provides a simple and efficient way to configure, manage, and run CI pipelines, helping teams to deliver high-quality software faster and with greater confidence.

ZIO SBT CI plugin generates a default GitHub workflow that includes common CI tasks such as building, testing, and publishing artifacts. However, users can also manually customize the workflow. This plugin is designed to be flexible and extensible, making it easy for users to tailor the workflow to their specific needs. Additionally, the plugin also provides tons of optional sbt settings that users can modify to change various aspects of the generated workflow. Overall, ZIO SBT CI plugin strikes a balance between automation and flexibility, allowing users to automate their CI process while still giving them control over how the workflow is generated.

### Getting Started

To use ZIO SBT CI plugin, add the following lines to your `plugins.sbt` file:

```scala
addSbtPlugin("dev.zio" % "zio-sbt-ci" % "0.7.0")

resolvers ++= Resolver.sonatypeOssRepos("public")
```

Then in your `build.sbt` file, enable the plugin by adding the following line:

```scala
enablePlugins(ZioSbtCiPlugin)
```

Now you can generate a Github workflow by running the following command:

```bash
sbt ciGenerateGithubWorkflow
```

This will generate a GitHub workflow file inside the `.github/workflows` directory, named `ci.yml`. The workflow file contains the following default jobs:

| Job | What it does |
| --- | --- |
| `build` | Compiles everything, publishes locally, and builds the website |
| `lint` | Checks the generated workflow is up to date, then runs `sbt lint` |
| `test` | Runs the test suite across the target Java versions |
| `update-readme` | Regenerates `README.md` from `docs/index.md` and opens a PR |
| `ci` | Aggregate gate: succeeds only if the jobs above did. Point branch protection at this one |
| `release` | Runs `sbt ci-release` on a published release, and on pushes to enabled branches for SNAPSHOTs |
| `release-docs` | Publishes the docs to npm |
| `notify-docs-release` | Tells `zio/zio` to rebuild the docs site |

Note that the plugin declares `trigger = allRequirements`, so having it on the classpath is enough — `enablePlugins(ZioSbtCiPlugin)` is not strictly required. Its settings are defined at `ThisBuild` level, so set them as `ThisBuild / ciSomething := ...`, or inside `inThisBuild(...)`.

> **Note:**
> 
> To use this plugin, we also need to install [ZIO Assistant](https://github.com/apps/zio-assistant) bot.

### Auto-Approving and Auto-Merging Dependency Update PRs

Besides `ci.yml`, the `ciGenerateGithubWorkflow` task also generates two more workflow files: `auto-approve.yml` and `auto-merge.yml`. These workflows automatically approve and enable GitHub's native auto-merge (squash strategy) on pull requests opened by dependency-update bots, such as [Scala Steward](https://github.com/scala-steward-org/scala-steward), [Dependabot](https://github.com/dependabot), and [Renovate](https://github.com/renovatebot/renovate).

Both workflows trigger on `pull_request_target` and also support `workflow_dispatch`, which backfills the approval/auto-merge on every currently open PR from the configured bots—handy for recovering PRs that were opened before the workflow existed, or after a workflow bug is fixed.

The set of trusted bots is controlled by the `ciDependencyUpdateBots` setting, which takes a `Seq[DependencyBot]`:

```scala
import zio.sbt.githubactions.DependencyBot

ciDependencyUpdateBots := Seq(
  DependencyBot.Dependabot,
  DependencyBot.Renovate,
  DependencyBot.ScalaSteward("zio-scala-steward"),
  DependencyBot.Custom("some-other-bot[bot]")
)
```

- `DependencyBot.Dependabot` — matches login `dependabot[bot]`
- `DependencyBot.Renovate` — matches login `renovate[bot]`
- `DependencyBot.ScalaSteward(githubAppName)` — matches login `<githubAppName>[bot]`, where `githubAppName` is the name of the GitHub App you registered for Scala Steward
- `DependencyBot.Custom(login)` — matches any exact GitHub login, for bots not covered by the predefined cases

The default value mirrors the bots used by the `zio/zio` repository: `Seq(DependencyBot.Dependabot, DependencyBot.Renovate, DependencyBot.ScalaSteward("zio-scala-steward"))`. If your Scala Steward GitHub App has a different name, override the setting with the correct `ScalaSteward` app name.

> **Note:**
>
> For `gh pr merge --auto` to actually merge a PR (rather than just queue it), the target repository needs "Allow auto-merge" enabled under **Settings → General**, and branch protection with required status checks configured on the target branch.

### Keeping the Workflow in Sync

The generated files are meant to be committed and never edited by hand. To stop them drifting from the build, run the check in CI — the default `lint` job already does:

```bash
sbt ciCheckGithubWorkflow
```

It regenerates the workflows and fails if the result differs from what is committed, naming the files that are stale. If a generated file is edited directly, the next run of this check fails, which is what keeps `build.sbt` the single source of truth.

### Settings Reference

All settings are `ThisBuild`-scoped.

**Workflow-level**

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciWorkflowTitle` | `String` | `"CI"` | Workflow name, lowercased to form the filename (`ci.yml`). Named this way to avoid colliding with zio-sbt-website's `ciWorkflowName`, which sets the README badge |
| `ciEnabledBranches` | `Seq[String]` | `Seq.empty` | Branches the workflow runs on. Empty means every branch |
| `ciWorkflowEnv` | `Map[String, String]` | derived from `ciJvmOptions`/`ciNodeOptions` | Workflow-level environment. Assigning **replaces** the derived map, which is how a build opts out of `JDK_JAVA_OPTIONS` |
| `ciJvmOptions` | `Seq[String]` | `Seq.empty` | Appended to `JDK_JAVA_OPTIONS` |
| `ciNodeOptions` | `Seq[String]` | `Seq.empty` | Sets `NODE_OPTIONS` when non-empty |
| `ciConcurrency` | `Option[Concurrency]` | one run per branch, cancelling in progress | Concurrency group, or `None` to omit the block |
| `ciSwapSizeGB` | `Int` | `0` | Adds a swap-space step to every job when greater than zero |
| `ciBackgroundJobs` | `Seq[String]` | `Seq.empty` | Commands prefixed to each `run`, for daemons a job needs |

**Test matrix**

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciTargetJavaVersions` | `Seq[String]` | `Seq("17", "21", "25")` | Java versions in the test matrix |
| `ciDefaultJavaVersion` | `String` | `"17"` | Java version for the non-matrix jobs |
| `ciTargetScalaVersions` | `Map[String, Seq[String]]` | `Map.empty` | Module to Scala versions. Empty runs a plain `sbt +test` |
| `ciTargetMinJavaVersions` | `Map[String, String]` | `Map.empty` | Module to minimum Java version, gating modules per matrix entry |
| `ciGroupSimilarTests` | `Boolean` | `false` | Groups by Java and Scala version instead of one entry per module |
| `ciMatrixMaxParallel` | `Option[Int]` | `None` | `strategy.max-parallel` |

**Jobs and steps**

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciBuildJobs` | `Seq[Job]` | one `build` job | Replaces or extends the build jobs |
| `ciLintJobs` | `Seq[Job]` | one `lint` job | |
| `ciTestJobs` | `Seq[Job]` | one `test` job | |
| `ciUpdateReadmeJobs` | `Seq[Job]` | one `update-readme` job | Set to `Seq.empty` if the README is maintained by hand |
| `ciReleaseJobs` | `Seq[Job]` | one `release` job | |
| `ciPostReleaseJobs` | `Seq[Job]` | `release-docs`, `notify-docs-release` | |
| `ciPullRequestApprovalJobs` | `Seq[String]` | the ids of `ciLintJobs`, `ciTestJobs` and `ciBuildJobs` | Job ids the aggregate `ci` job waits on. Follows the jobs those three settings produce, so renaming or adding one is picked up without touching this |
| `ciReleaseApprovalJobs` | `Seq[String]` | `Seq("ci")` | Job ids the release waits on |
| `ciCheckArtifactsCompilationSteps` | `Seq[Step]` | `sbt +Test/compile` | |
| `ciCheckArtifactsBuildSteps` | `Seq[Step]` | `sbt +publishLocal` | |
| `ciCheckWebsiteBuildProcess` | `Seq[Step]` | `sbt docs/buildWebsite` | Set to `Seq.empty` in a build with no `docs` project |
| `ciCheckGithubWorkflowSteps` | `Seq[Step]` | `sbt ciCheckGithubWorkflow` | |

**Release and docs**

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciPublishSnapshots` | `Boolean` | `true` | Also publish SNAPSHOTs on pushes to enabled branches |
| `ciUpdateReadmeCondition` | `Option[Condition]` | `None` | When to run the README job. Defaults to published releases |
| `ciDocsVersioningScheme` | `DocsVersioning` | `SemanticVersioning` | `SemanticVersioning` or `HashVersioning` |
| `ciDependencyUpdateBots` | `Seq[DependencyBot]` | Dependabot, Renovate, `ScalaSteward("zio-scala-steward")` | Bots whose PRs are auto-approved and auto-merged |

**Tasks**

| Task | Description |
| --- | --- |
| `ciGenerateGithubWorkflow` | Writes `ci.yml`, `auto-approve.yml` and `auto-merge.yml` |
| `ciCheckGithubWorkflow` | Regenerates and fails if the committed files are stale |
| `ciGenerateAutoApproveWorkflow` | Writes `auto-approve.yml` only |
| `ciGenerateAutoMergeWorkflow` | Writes `auto-merge.yml` only |

### Customizing Jobs

Jobs are values, so they can be transformed or replaced. The types live in `zio.sbt.githubactions` and the reusable steps in `zio.sbt.ZioSbtCiPlugin`; neither is exported through `autoImport`, so import them explicitly.

To adjust the generated jobs, map over them:

```scala
import zio.sbt.githubactions.Job

def onUbuntu22(job: Job): Job = job.copy(runsOn = "ubuntu-22.04")

inThisBuild(
  List(
    ciTestJobs  := ciTestJobs.value.map(onUbuntu22),
    ciBuildJobs := ciBuildJobs.value.map(onUbuntu22)
  )
)
```

To write one from scratch, build it from the exported steps:

```scala
import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.{Job, Step, Strategy}

ciTestJobs := Seq(
  Job(
    id = "integration-test",
    name = "Integration Test",
    jobTimeout = Some(30),
    strategy = Some(Strategy(matrix = Map("java" -> List("17", "21")), failFast = false)),
    steps = Seq(
      Checkout.value,
      SetupJava("17"),
      SetupSBT,
      CacheDependencies,
      Step.SingleStep(name = "Test", run = Some("sbt it:test"))
    )
  )
)
```

The aggregate `ci` job waits on the ids of whatever `ciLintJobs`, `ciTestJobs` and `ciBuildJobs`
produce, so replacing `test` with `integration-test` above is enough on its own. Set
`ciPullRequestApprovalJobs` only to wait on a different set than those three:

```scala
ciPullRequestApprovalJobs := Seq("lint", "integration-test")
```

`Checkout`, `SetupLibuv`, `SetupJava(version)`, `SetupSBT`, `CacheDependencies`, `SetupNodeJs` and `SetSwapSpace` are all available; the ones defined as settings need `.value`.

### Job Timeouts

A job with no timeout inherits GitHub's default of six hours, which is a long time to wait for a hung build:

```scala
Job(id = "test", name = "Test", jobTimeout = Some(25), steps = ...)

// or on an existing job
ciTestJobs := ciTestJobs.value.map(_.withTimeout(25))
```

> **Note:**
>
> `Job` also has an older `timeoutMinutes: Int` field. It predates `jobTimeout` and was accepted but never rendered, so it is deprecated. It is still honoured when set to anything other than its historical default of `30`, so builds that set it keep working.

### Concurrency

By default one run per branch is kept and in-progress runs are cancelled. `cancelInProgress` accepts an expression as well as a boolean, which is what lets a workflow cancel superseded pull request runs while letting releases finish:

```scala
import zio.sbt.githubactions.{CancelInProgress, Concurrency, Condition}

ciConcurrency := Some(
  Concurrency(
    group = "ci-pr-${{ github.event_name == 'pull_request' && github.event.pull_request.number || github.ref }}",
    cancelInProgress = CancelInProgress.When(Condition.Expression("github.event_name == 'pull_request'"))
  )
)
```

Individual jobs can override it. A release should generally not be cancelled halfway through:

```scala
Job(
  id = "release",
  name = "Release",
  concurrency = Some(
    Concurrency(group = "release-${{ github.ref }}", cancelInProgress = CancelInProgress.Never)
  ),
  steps = ...
)
```

### Service Containers

Jobs that need a database can attach a service. `options` carries the raw `docker create` arguments, which is where the health check goes — without one the job races the container and steps can start before it accepts connections:

```scala
import zio.Chunk
import zio.sbt.githubactions.{ImageRef, Service, ServicePort}

Job(
  id = "test",
  name = "Test",
  services = Seq(
    Service(
      name = "postgres",
      image = ImageRef("postgres:16"),
      env = Map("POSTGRES_USER" -> "postgres", "POSTGRES_PASSWORD" -> "postgres"),
      ports = Chunk(ServicePort(5432, 5432)),
      options = Some("--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5")
    )
  ),
  steps = ...
)
```

`ServicePort(inner, outer)` renders as `inner:outer`, which GitHub reads as `<host>:<container>` — so `inner` is the port a step connects to on `localhost`.

### The Workflow Environment

By default the workflow exports `JDK_JAVA_OPTIONS`, built from `ciJvmOptions`. That is not always wanted: it makes `java -version` print a note containing commas, which corrupts the cache keys computed by `setup-sbt` and `coursier/cache-action`. Assigning `ciWorkflowEnv` replaces the derived map, so JVM flags can go through `SBT_OPTS` instead:

```scala
ciWorkflowEnv := Map(
  "SBT_OPTS" -> "-XX:+PrintCommandLineFlags -Djava.locale.providers=CLDR,JRE"
)
```

## ZIO SBT Source

ZIO SBT Source is a Scala 2.13 + Scala 3 cross-compiled library that provides utilities for self-documenting example code. It includes the `ExprEval` macro, which captures the source text of expressions at compile time and prints them alongside their evaluated results at runtime.

### Installation

Add the following line to your `libraryDependencies` in `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-sbt-source" % "0.7.0"
```

### Features

The `ExprEval.show` macro provides an intuitive way to write self-documenting example code:

```scala
import zio.sbt.ExprEval.show

// Print expression source and result
show(
  42 + 8,
  "Hello, " + "World!"
)
```

Output:
```
42 + 8
// 50
"Hello, " + "World!"
// Hello, World!
```

### Comment Labels

Add `//` comments above your `show` call to add context labels to the output:

```scala
// Calculate the answer to life, the universe, and everything
show(6 * 7)
```

Output:
```
// Calculate the answer to life, the universe, and everything
6 * 7
// 42
```

### Block Form

For multiple expressions, use the block form:

```scala
show {
  1 + 2
  3 * 4
  "hello"
}
```

### SourceFile: Embedding Source Code in Documentation

The `SourceFile` utility provides methods for embedding source files into mdoc documentation with support for syntax highlighting and line numbers:

```scala
import zio.sbt.SourceFile

// Embed a source file in mdoc
SourceFile.printSource("path/to/Example.scala")
```

This generates a fenced code block with the file's language tag detected from its extension.

### EmbedSourceModifier: mdoc Plugin for Source Embedding

The `EmbedSourceModifier` extends mdoc with an `embed` directive for embedding source files directly from markdown.
Use the modifier name `embed` followed by the file path:

```
\`\`\`scala mdoc:embed:path/to/Example.scala
\`\`\`
```

Renders as a fenced code block with syntax highlighting based on file extension.

#### Docusaurus Line Numbers

To enable line numbers in [Docusaurus](https://docusaurus.io) code blocks, add the `:showLineNumbers` flag (or kebab-case `:show-line-numbers` alias):

```
\`\`\`scala mdoc:embed:path/to/Example.scala:showLineNumbers
\`\`\`
```

or

```
\`\`\`scala mdoc:embed:path/to/Example.scala:show-line-numbers
\`\`\`
```

Both forms emit `showLineNumbers` in the code fence header, enabling line numbering in the rendered documentation.

### Implementation Details

- **Scala 3**: Uses `scala.quoted.*` with `inline def` for compile-time source capture
- **Scala 2.13**: Uses `scala.reflect.macros.whitebox` to achieve the same behavior
- **Runtime Helper**: `SourceReader` utility reads comment lines from source files

## Testing Strategies

### Default Testing Strategy

The default testing strategy for ZIO SBT CI plugin is to run `sbt +test` on Corretto Java 17, 21 and 25. So this will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java: ['17', '21', '25']
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v5
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v6
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt +test
```

The `sbt +test` command will run the `test` task for all submodules in the project against all Scala versions defined in the `crossScalaVersions` setting.

### Concurrent Testing Strategy

In some cases, we may have multiple submodules in our project and we want to test them concurrently using GitHub Actions matrix strategy.

The `ciTargetScalaVersions` setting key is used to define a mapping of project names to the Scala versions that should be used for testing phase of continuous integration (CI).

For example, suppose we have a project with the name "submoduleA" and we want to test it against Scala `2.12.20`, and for the "submoduleB" we want to test it against Scala `2.12.20` and `2.13.18` and `3.3.7`, We can define the `ciTargetScalaVersions` setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
    "submoduleA" -> Seq("2.12.20"),
    "submoduleB" -> Seq("2.12.20", "2.13.18", "3.3.7")
  )
```

In the example provided, `ciTargetScalaVersions` is defined at the `ThisBuild` level, meaning that the setting will apply to all projects within the build. The setting defines a Map where the key is the name of the current project, obtained by calling the `id` method on the `thisProject` setting, and the value is a sequence of Scala versions obtained from the `crossScalaVersions` of each submodule setting.

To simplify this process, we can populate the versions using each submodule's crossScalaVersions setting as follows:

```scala
ThisBuild / ciTargetScalaVersions := Map(
  (submoduleA / thisProject).value.id -> (submoduleA / crossScalaVersions).value,
  (submoduleB / thisProject).value.id -> (submoduleB / crossScalaVersions).value
)
```

The above code can be simplified further by using `targetScalaVersionsFor` helper method, it takes a list of submodules and returns a Map of project names to their `crossScalaVersions`:

```scala
ThisBuild / ciTargetScalaVersions := targetScalaVersionsFor(submoduleA, submoduleB).value
```

This will generate the following job:

```yaml
test:
  name: Test
  runs-on: ubuntu-latest
  continue-on-error: false
  strategy:
    fail-fast: false
    matrix:
      java: ['17', '21', '25']
      scala-project:
      - ++2.12.20 submoduleA
      - ++2.12.20 submoduleB
      - ++2.13.18 submoduleB
      - ++3.3.7 submoduleB
  steps:
  - name: Install libuv
    run: sudo apt-get update && sudo apt-get install -y libuv1-dev
  - name: Setup Scala
    uses: actions/setup-java@v5
    with:
      distribution: corretto
      java-version: ${{ matrix.java }}
      check-latest: true
  - name: Cache Dependencies
    uses: coursier/cache-action@v6
  - name: Git Checkout
    uses: actions/checkout@v6
    with:
      fetch-depth: '0'
  - name: Test
    run: sbt ${{ matrix.scala-project }}/test
```
