# ZIO SBT GitHub Actions

> `zio-sbt-githubactions` is a Scala model of GitHub Actions workflows and of the config files of dependency bots. [ZIO SBT CI](./ci.md) uses it to render the generated YAML, and a build uses it to describe jobs, steps and triggers in `build.sbt`. The module is published as a dependency of `zio-sbt-ci`, so it needs no installation of its own. Its types live in the `zio.sbt.githubactions` package and are not exported through `autoImport`, so import them explicitly.

## Workflow Model

A `Workflow` holds a name, environment variables, triggers, jobs and an optional `Concurrency`. A `Job` holds steps and the settings of one GitHub Actions job. The following types make up the model.

| Type                  | Description                                                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Workflow`            | Name, `env`, `triggers`, `jobs`, `concurrency` and `permissions`. `withoutPermissions` omits the `permissions` block, which is not the same as an empty map |
| `Job`                 | `id`, `name`, `runsOn`, `continueOnError`, `strategy`, `steps`, `need`, `services`, `condition`, `jobTimeout`, `concurrency` and `permissions`    |
| `Strategy`            | `Strategy(matrix, maxParallel = None, failFast = true)`, the matrix of a job                                                                      |
| `Step`                | `Step.SingleStep` has `name`, `id`, `uses`, `condition`, `parameters`, `run` and `env`. `Step.StepSequence` groups several steps                  |
| `ActionRef`           | The `uses` reference of a step, such as `actions/checkout@v7.0.1`                                                                                     |
| `Trigger`             | `WorkflowDispatch`, `Schedule`, `Release`, `PullRequest`, `PullRequestTarget`, `Push`, `Create`, `WorkflowRun` and `IssueComment`                 |
| `Branch`              | `Branch.All` or `Branch.Named(name)`, used by push and pull request triggers                                                                      |
| `Condition`           | `Condition.Expression(expression)` renders as `${{ expression }}`. `Condition.Function(expression)` renders as written. Both combine with `&&` and `\|\|`, but a `Function` cannot be combined |
| `Service`             | A service container: `name`, `image`, `env`, `ports` and `options`                                                                                |
| `ImageRef`            | The image of a service, such as `ImageRef("postgres:16")`                                                                                         |
| `ServicePort`         | `ServicePort(inner, outer)` renders as `inner:outer`                                                                                              |
| `Concurrency`         | `Concurrency(group, cancelInProgress = CancelInProgress.Always)`                                                                                  |
| `CancelInProgress`    | `Always`, `Never` or `When(condition)`                                                                                                            |
| `OS`                  | The runner operating system. `OS.UbuntuLatest` renders as `ubuntu-latest`                                                                         |

To change a job that a setting produced, copy it with the `Job` methods `withStrategy`, `withRunsOn`, `withSteps`, `withServices`, `withTimeout` and `withPermissions`. A job's `timeoutMinutes` field is deprecated in favor of `jobTimeout`.

The `zio.sbt.V` object resolves the pinned version of an action, so `V("actions/checkout")` returns the `actions/checkout@<version>` reference that the generated workflows use.

## CI Setting Types

These types are the values of the `ci*` settings of [ZIO SBT CI](./ci.md).

| Type                           | Used by                                          | Description                                                                                           |
| ------------------------------ | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| `DependencyBot`                | `ciDependencyUpdateBots`                         | `Dependabot` (`dependabot[bot]`), `Renovate` (`renovate[bot]`), `ScalaSteward(githubAppName)` (`<githubAppName>[bot]`) and `Custom(login)` |
| `DeployPreviewMode`            | `ciDeployPreviewMode`                            | `Automatic` or `OnDemand`                                                                             |
| `DependabotConfig`             | `ciDependabotConfig`                             | `DependabotConfig(updates)`, by default one weekly `github-actions` update                            |
| `DependabotUpdate`             | `DependabotConfig`                               | `ecosystem`, `directory = "/"`, `interval = "weekly"`, `openPullRequestsLimit` and `groups`           |
| `DependabotGroup`              | `DependabotUpdate`                               | `name`, `patterns` and `updateTypes`                                                                  |
| `ScalaStewardConfig`           | `ciScalaStewardConfig`                           | The `.scala-steward.conf` content: `pins`, `allow`, `ignore`, `updatesLimit`, `updatePullRequests`, `commitMessage`, `pullRequestsFrequency`, `grouping`, `assignees` and `reviewers` |
| `ScalaStewardDependency`       | `ScalaStewardConfig`                             | `groupId`, `artifactId` and `version`                                                                 |
| `ScalaStewardGroupingRule`     | `ScalaStewardConfig`                             | `name` and a `filter` of `ScalaStewardGroupFilter(group, version)`                                    |
| `ReleaseDrafterCategory`       | `ciReleaseDrafterCategories`                     | `title` and the `labels` that put a pull request in the category                                      |
| `ReleaseDrafterVersionResolver`| `ciReleaseDrafterVersionResolver`                | The labels for `major`, `minor` and `patch`, and the `default` bump                                   |
| `ReleaseDrafterAutolabelerRule`| `ciReleaseDrafterAutolabeler`                    | A `label` and the `files`, `branch`, `title` and `body` patterns that apply it                        |

The how-to guides show these types at work. See [Auto-Approve and Auto-Merge Dependency Update PRs](../how-to-guides/auto-approve-and-auto-merge-dependency-update-prs.md), [Configure Dependabot Updates](../how-to-guides/configure-dependabot-updates.md) and [Customize the Generated CI Jobs](../how-to-guides/customize-the-generated-ci-jobs.md).
