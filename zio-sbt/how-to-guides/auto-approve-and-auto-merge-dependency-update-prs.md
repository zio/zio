# Auto-Approve and Auto-Merge Dependency Update PRs

> Besides `ci.yml`, the `ciGenerateGithubWorkflow` task also generates two more workflow files: `auto-approve.yml` and `auto-merge.yml`. These workflows automatically approve, and squash-merge once CI is green, pull requests opened by dependency-update bots, such as [Scala Steward](https://github.com/scala-steward-org/scala-steward), [Dependabot](https://github.com/dependabot), and [Renovate](https://github.com/renovatebot/renovate).

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
> When the PR's base branch requires status checks (through classic branch protection or a ruleset), `auto-merge.yml` enables GitHub's native auto-merge with `gh pr merge --auto --squash` and finishes at once; GitHub merges the PR when the required checks pass. On a branch without required status checks, where `--auto` would merge before CI finishes, it instead waits for every check on the PR (other than its own bot jobs) to finish and merges with `gh pr merge --squash` only if none failed. A red check fails the job and leaves the PR open. The `workflow_dispatch` backfill still uses `gh pr merge --auto`, which merges immediately on a branch with no required status checks, so configure branch protection with required checks before relying on it.

> **Note:**
>
> `GITHUB_TOKEN` can never be granted the "workflows" scope needed to auto-merge a bot PR that touches `.github/workflows/**`, so `auto-merge.yml` mints a GitHub App token for that case instead, falling back to `GITHUB_TOKEN` when no app is configured (which still auto-merges ordinary, non-workflow-touching PRs fine). This needs the same `APP_ID`/`APP_PRIVATE_KEY` secrets as `update-readme`, and the [ZIO Assistant](https://github.com/apps/zio-assistant) app installation must additionally have the **Workflows** repository permission granted and accepted — without it, workflow-touching PRs still fail to auto-merge and need a manual merge.
