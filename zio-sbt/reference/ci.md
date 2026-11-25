# ZIO SBT CI

> `ZioSbtCiPlugin` generates the GitHub Actions workflows of a project from its `build.sbt`, so continuous integration (CI) is configured in one place. The default workflow builds, tests, lints and publishes the project, and every part of it can be changed with sbt settings or replaced by custom jobs. The model of workflows, jobs and steps lives in [ZIO SBT GitHub Actions](./githubactions.md).

To install the plugin, add `addSbtPlugin("dev.zio" % "zio-sbt-ci" % "0.8.12")` to `project/plugins.sbt`, as described in [Installation](../index.md#installation). Using SNAPSHOT versions of the plugin also needs `resolvers ++= Resolver.sonatypeOssRepos("public")`.

The plugin declares `trigger = allRequirements`, so having it on the classpath is enough, and `enablePlugins(ZioSbtCiPlugin)` is not strictly required. Its settings are defined at `ThisBuild` level, so set them as `ThisBuild / ciSomething := ...`, or inside `inThisBuild(...)`.

To use the plugin, install the [ZIO Assistant](https://github.com/apps/zio-assistant) GitHub App on the repository.

## Generated Files

`ciGenerateGithubWorkflow` writes these files. Commit them and never edit them by hand. See [Keep the Generated Workflow in Sync](../how-to-guides/keep-the-workflow-in-sync.md) to check that they match the build.

| File                                       | Generated when                                                                              |
| ------------------------------------------ | ------------------------------------------------------------------------------------------- |
| `.github/workflows/ci.yml`                 | Always. The file name is `ciWorkflowTitle` lower-cased, with non-alphanumeric characters turned into `-` |
| `.github/workflows/auto-approve.yml`       | Always                                                                                      |
| `.github/workflows/auto-merge.yml`         | Always                                                                                      |
| `.github/workflows/deploy-preview.yml`     | `ciEnableDeployPreview` is `true`                                                           |
| `.github/workflows/release-drafter.yml`    | `ciEnableReleaseDrafter` is `true` (the default). Scaffolds `.github/release-drafter.yml` once |
| `.github/workflows/scala-steward.yml`      | `ciEnableScalaSteward` is `true` (the default). Scaffolds `.scala-steward.conf` once when `ciScalaStewardConfig` renders content |
| `.github/dependabot.yml`                   | `ciEnableDependabot` is `true` (the default)                                                |
| `.github/workflows/regenerate-workflows.yml` | `ciEnableRegenerateWorkflows` is `true` (the default)                                     |

## Default Jobs

The generated `ci.yml` contains these jobs:

| Job | What it does |
| --- | --- |
| `build` | Compiles everything, publishes locally, and builds the website |
| `lint` | Checks the generated workflow is up to date, then runs `sbt lint` |
| `test` | Runs the test suite across the target Java versions |
| `update-readme` | Regenerates `README.md` from `docs/index.md` and opens a PR |
| `ci` | Aggregate gate: succeeds only if the jobs above did. Point branch protection at this one |
| `release` | Runs `sbt ci-release` on a published release or a manual run, and on pushes to the default branch for SNAPSHOTs |
| `release-docs` | Publishes the docs to npm after a successful `release`, or on a manual run with `docs-only` set to `true` |
| `notify-docs-release` | Dispatches an `update-docs` event to `zio/zio` with the docs package name and version, so the ZIO website picks up the new docs |

`notify-docs-release` takes the docs version from the release tag, or from the newest `v` tag on a manual `docs-only` run, and waits for up to five minutes until npm lists exactly that version. It fails if the version never appears. With `HashVersioning` as `ciDocsVersioningScheme`, it still reads the `latest` version with `npm view <package> version`.

The default workflow runs on `workflow_dispatch`, published releases, pushes (to `ciEnabledBranches` when set, otherwise every branch) and pull requests that do not target `gh-pages`.

## Concurrency of the Default Workflow

By default `ciConcurrency` is one run per workflow and ref, and an in-progress run is cancelled when a newer run starts in the same group. The group is `${{ github.workflow }}-${{ github.ref == format('refs/heads/{0}', github.event.repository.default_branch) && github.run_id || github.ref }}`. On the default branch the group ends with the run id, so every run is kept. On any other ref, including a tag such as `refs/tags/v1.9.2`, the group ends with the ref.

A release event and the push of its tag have the same ref, so they share one group, and the run that starts later cancels the earlier one. To change this, set `ciConcurrency` as described in [Control CI Concurrency](../how-to-guides/control-ci-concurrency.md).

## Settings

All settings are `ThisBuild`-scoped.

### Workflow-Level

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciWorkflowTitle` | `String` | `"CI"` | Workflow name. The file name is its lower-cased form with each run of non-alphanumeric characters replaced by `-` (`ci.yml` for `CI`). Named this way to avoid colliding with zio-sbt-website's `ciWorkflowName`, which sets the README badge |
| `ciEnabledBranches` | `Seq[String]` | `Seq.empty` | Branches the workflow runs on. Empty means every branch |
| `ciWorkflowEnv` | `Map[String, String]` | derived from `ciJvmOptions`/`ciNodeOptions` | Workflow-level environment. Assigning **replaces** the derived map, which is how a build opts out of `JDK_JAVA_OPTIONS` |
| `ciJvmOptions` | `Seq[String]` | `Seq.empty` | Appended to `JDK_JAVA_OPTIONS` |
| `ciNodeOptions` | `Seq[String]` | `Seq.empty` | Sets `NODE_OPTIONS` when non-empty |
| `ciConcurrency` | `Option[Concurrency]` | one run per ref, cancelling in progress, except that every run on the default branch is kept | Concurrency group, or `None` to omit the block. See [Concurrency of the Default Workflow](#concurrency-of-the-default-workflow) |
| `ciWorkflowPermissions` | `Option[Map[String, String]]` | `Some(id-token: write, contents: read)` | Workflow-level `permissions`. `None` omits the block, so the workflow runs with the repository's default token permissions; `Some(Map.empty)` instead renders `permissions: {}`, which grants nothing |
| `ciSwapSizeGB` | `Int` | `0` | Adds a swap-space step to every job when greater than zero |
| `ciBackgroundJobs` | `Seq[String]` | `Seq.empty` | Commands prefixed to each `run`, for daemons a job needs |
| `ciEnableDeployPreview` | `Boolean` | `false` | Adds website-artifact upload steps to `build` and generates `deploy-preview.yml`. Requires `NETLIFY_AUTH_TOKEN`/`NETLIFY_SITE_ID` secrets |
| `ciDeployPreviewMode` | `DeployPreviewMode` | `DeployPreviewMode.Automatic` | Only used with `ciEnableDeployPreview`. `Automatic` deploys every successful pull-request CI run; `OnDemand` posts a "Deploy preview" checkbox comment and deploys only when it is ticked |
| `ciEnableReleaseDrafter` | `Boolean` | `true` | Generates `release-drafter.yml` and scaffolds `.github/release-drafter.yml` (once). Overwrites a hand-written `release-drafter.yml` when on; when off, only deletes a copy it generated itself |
| `ciReleaseDrafterCategories` | `Seq[ReleaseDrafterCategory]` | Features/Bug Fixes/Maintenance/Dependency Updates | Categories written into the scaffolded config on first generation only |
| `ciReleaseDrafterVersionResolver` | `Option[ReleaseDrafterVersionResolver]` | `None` | Optional `version-resolver` block for the scaffolded config |
| `ciReleaseDrafterAutolabeler` | `Seq[ReleaseDrafterAutolabelerRule]` | `Seq.empty` | Optional `autolabeler` rules for the scaffolded config |
| `ciReleaseDrafterExcludeLabels` | `Seq[String]` | `Seq("skip-changelog")` | `exclude-labels` for the scaffolded config |
| `ciReleaseDrafterBranch` | `Option[Branch]` | `None` | Branch `release-drafter.yml`'s `push` trigger runs on. Falls back to the first of `ciEnabledBranches`, then `"main"` |
| `ciEnableScalaSteward` | `Boolean` | `true` | Generates `scala-steward.yml` and scaffolds `.scala-steward.conf` (once, only if non-empty). Overwrites a hand-written `scala-steward.yml` when on; when off, only deletes a copy it generated itself |
| `ciScalaStewardSchedule` | `String` | `"0 0 * * *"` | Cron schedule for `scala-steward.yml`'s `schedule` trigger |
| `ciScalaStewardTimeoutMinutes` | `Int` | `45` | `timeout-minutes` for the scala-steward job |
| `ciScalaStewardPermissions` | `Map[String, String]` | `contents: read` | Permissions block for `scala-steward.yml`; the action authenticates via the GitHub App's installation token, not `GITHUB_TOKEN`, so no write scope is needed by default |
| `ciScalaStewardWorkflowEnv` | `Map[String, String]` | `Map.empty` | Extra env vars for the scala-steward job |
| `ciScalaStewardConfig` | `ScalaStewardConfig` | `ScalaStewardConfig()` | Settings scaffolded into `.scala-steward.conf` on first non-empty render |
| `ciEnableDependabot` | `Boolean` | `true` | Generates `.github/dependabot.yml`. Overwrites a hand-written one when on; when off, only deletes a copy it generated itself |
| `ciEnableRegenerateWorkflows` | `Boolean` | `true` | Generates `regenerate-workflows.yml`, which re-runs `ciGenerateGithubWorkflow` on bot PRs and pushes the result to the PR branch. When off, only deletes a copy it generated itself |
| `ciDependabotConfig` | `DependabotConfig` | one weekly `github-actions` update at `/` | Content of the generated `dependabot.yml` |

### Test Matrix

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciTargetJavaVersions` | `Seq[String]` | `Seq("17", "21", "25")` | Java versions in the test matrix |
| `ciDefaultJavaVersion` | `String` | `"17"` | Java version for the non-matrix jobs |
| `ciTargetScalaVersions` | `Map[String, Seq[String]]` | `Map.empty` | Module to Scala versions. Empty runs a plain `sbt +test` |
| `ciTargetMinJavaVersions` | `Map[String, String]` | `Map.empty` | Module to minimum Java version, gating modules per matrix entry |
| `ciGroupSimilarTests` | `Boolean` | `false` | Groups by Java and Scala version instead of one entry per module |
| `ciMatrixMaxParallel` | `Option[Int]` | `None` | `strategy.max-parallel` |

### Jobs and Steps

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciBuildJobs` | `Seq[Job]` | one `build` job | Replaces or extends the build jobs |
| `ciLintJobs` | `Seq[Job]` | one `lint` job | |
| `ciTestJobs` | `Seq[Job]` | one `test` job | |
| `ciUpdateReadmeJobs` | `Seq[Job]` | one `update-readme` job | Set to `Seq.empty` if the README is maintained by hand |
| `ciReportSuccessfulJobs` | `Seq[Job]` | the aggregate `ci` job | Set to `Seq.empty` for a workflow made up entirely of custom jobs that does not want one |
| `ciReleaseJobs` | `Seq[Job]` | one `release` job | |
| `ciPostReleaseJobs` | `Seq[Job]` | `release-docs`, `notify-docs-release` | |
| `ciPullRequestApprovalJobs` | `Seq[String]` | the ids of `ciLintJobs`, `ciTestJobs` and `ciBuildJobs` | Job ids the aggregate `ci` job waits on. Follows the jobs those three settings produce, so renaming or adding one is picked up without touching this |
| `ciReleaseApprovalJobs` | `Seq[String]` | `Seq("ci")` | Job ids the release waits on |
| `ciCheckArtifactsCompilationSteps` | `Seq[Step]` | `sbt +Test/compile` | |
| `ciCheckArtifactsBuildSteps` | `Seq[Step]` | `sbt +publishLocal` | |
| `ciCheckWebsiteBuildProcess` | `Seq[Step]` | `sbt docs/clean; sbt docs/buildWebsite`, then `sbt docs/checkDocsOnFreshWebsite` | Set to `Seq.empty` in a build with no `docs` project |
| `ciCheckGithubWorkflowSteps` | `Seq[Step]` | `sbt ciCheckGithubWorkflow` | |

### Release and Docs

| Setting | Type | Default | Description |
| --- | --- | --- | --- |
| `ciPublishSnapshots` | `Boolean` | `true` | Also publish SNAPSHOTs on pushes to the default branch |
| `ciUpdateReadmeCondition` | `Option[Condition]` | `None` | When to run the README job. Defaults to published releases |
| `ciDocsVersioningScheme` | `DocsVersioning` | `SemanticVersioning` | `SemanticVersioning` or `HashVersioning` |
| `ciDependencyUpdateBots` | `Seq[DependencyBot]` | Dependabot, Renovate, `ScalaSteward("zio-scala-steward")` | Bots whose PRs are auto-approved and auto-merged |

### Tasks

| Task | Description |
| --- | --- |
| `ciGenerateGithubWorkflow` | Writes `ci.yml`, `auto-approve.yml` and `auto-merge.yml`, plus `deploy-preview.yml` when `ciEnableDeployPreview` is `true` and `release-drafter.yml` (and scaffolds `.github/release-drafter.yml` once) when `ciEnableReleaseDrafter` is `true`, and scala-steward.yml (and scaffolds .scala-steward.conf once, when ciScalaStewardConfig renders non-empty content) when ciEnableScalaSteward is true, `.github/dependabot.yml` when `ciEnableDependabot` is `true`, and `regenerate-workflows.yml` when `ciEnableRegenerateWorkflows` is `true` |
| `ciCheckGithubWorkflow` | Regenerates and fails if the committed files are stale |
| `ciGenerateAutoApproveWorkflow` | Writes `auto-approve.yml` only |
| `ciGenerateAutoMergeWorkflow` | Writes `auto-merge.yml` only |

## Types Used in Settings

The types that the settings above take, such as `Job`, `Step`, `Concurrency`, `DependencyBot` and `DeployPreviewMode`, are described in [ZIO SBT GitHub Actions](./githubactions.md). They live in `zio.sbt.githubactions` and are not exported through `autoImport`, so import them explicitly. The reusable steps live in `zio.sbt.ZioSbtCiPlugin`.

## Helper Methods

`targetScalaVersionsFor(projects*)` takes a list of projects and returns a `Map` from each project id to its `crossScalaVersions`. It is meant for `ciTargetScalaVersions`, as shown in [Test Against Several Scala and Java Versions](../how-to-guides/test-against-several-scala-and-java-versions.md).
