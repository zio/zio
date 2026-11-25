# Enable Netlify Deploy Previews

> Setting `ciEnableDeployPreview := true` makes `ciGenerateGithubWorkflow` also generate `deploy-preview.yml`, which deploys the built Docusaurus site to Netlify for every pull request that touches `docs/` or `website/`.

```scala
ThisBuild / ciEnableDeployPreview := true
```

The feature is split across two workflows, decoupled by artifacts rather than triggering the deploy directly from a pull request event:

- The `build` job gains a few extra steps. `Check website is installed` runs first, checking that `website/package.json` and `website/docusaurus.config.js` both exist — the same signal [`installWebsite`](../reference/website.md) itself uses to tell a real Docusaurus scaffold apart from a `website/` directory holding only `mdocOut`'s generated output. Only when the website is installed does the job go on to detect whether the pull request touched `docs/` or `website/`, and if so upload `website/build` as a `website-artifact`, plus the PR number as a `pr-metadata` artifact.
- `deploy-preview.yml` triggers on `workflow_run` once the CI workflow completes, downloads those two artifacts, deploys `website-artifact` to Netlify with [`nwtgck/actions-netlify`](https://github.com/nwtgck/actions-netlify) under a deterministic `pr-<number>` alias — so the preview URL for a given PR is stable across redeploys, of the shape `pr-<number>--<your-netlify-site-name>.netlify.app` — then posts a fresh comment with the preview URL via [`peter-evans/create-or-update-comment`](https://github.com/peter-evans/create-or-update-comment) — each deploy gets its own comment rather than editing one in place, so pushing a follow-up commit doesn't erase the record of the previous deploy. Earlier preview comments (tagged with an `<!-- netlify-preview-comment -->` marker) are collapsed as outdated first, via [`actions/github-script`](https://github.com/actions/github-script), so the thread doesn't accumulate clutter — the whole comment history is paginated through, not just the first page, so this still works on pull requests with a long comment history.

Using `workflow_run` instead of triggering directly on `pull_request` means the deploy step only ever runs after CI has actually succeeded, and it runs with read/write access to secrets even for pull requests from forks (where a plain `pull_request` trigger would not have that access).

`deploy-preview.yml` triggers on *every* successful pull-request CI run, not only the ones where `build` actually uploaded artifacts — a PR that doesn't touch `docs/`/`website/`, or a repo with no website installed yet, never produces `website-artifact`/`pr-metadata` at all. To handle that, the two `actions/download-artifact` steps set `continue-on-error: true`, since a missing artifact there is an expected outcome, not a failure worth stopping the job over; every step after them then checks `steps.download-website.outcome == 'success' && steps.download-pr-metadata.outcome == 'success'` before running. So a run with nothing to deploy ends with two soft-failed downloads and nothing else — no deploy call, no PR comment — instead of a hard job failure.

Put together, this makes `ciEnableDeployPreview` safe to turn on before the Docusaurus site exists: neither workflow does any real work until `website/` is a genuine installation and a pull request actually changes it.

This requires two repository secrets: `NETLIFY_AUTH_TOKEN`, a [personal access token](https://docs.netlify.com/api/get-started/#authentication) generated from the Netlify user account that owns the site, and `NETLIFY_SITE_ID`, found under the site's **Site configuration → General → Site details** in the Netlify dashboard. The default value of `ciEnableDeployPreview` is `false`, so existing builds see no change until they opt in.

#### On-demand previews

Every preview deploy costs Netlify credits. To deploy only when someone asks for it, switch the mode:

```scala
import zio.sbt.githubactions.DeployPreviewMode

ThisBuild / ciEnableDeployPreview := true
ThisBuild / ciDeployPreviewMode   := DeployPreviewMode.OnDemand
```

`ciDeployPreviewMode` defaults to `DeployPreviewMode.Automatic`, the behavior described above, so nothing changes until you opt in. With `OnDemand`, `deploy-preview.yml` is still generated, but it works differently:

1. After a successful pull-request CI run, the `post-preview-comment` job creates one bot comment on the pull request with an unticked **Deploy preview** checkbox. Nothing is deployed yet. A later push resets the same comment to the new commit with the box unticked again.
2. When someone ticks the box, GitHub fires an `issue_comment` `edited` event and the `deploy-preview` job runs. It first checks that the person who ticked has `admin`, `maintain` or `write` access to the repository and adds a 👍 reaction to the comment to acknowledge the request, then finds the successful `ci.yml` run for the exact commit named in the comment, downloads that run's `website-artifact`, and deploys it to Netlify.
3. The comment is rewritten with the preview URL, the deploy time and the commit, plus a **Redeploy preview** checkbox. If the deployment fails, the box is unticked again and the comment links to the failed run.

Deployments are serialized per pull request (a second tick waits for the first instead of cancelling it). The two Netlify secrets and the `website-artifact` / `pr-metadata` artifacts from `build` are the same as for the automatic mode, and the generated workflow looks up CI by its file name, `ci.yml`.
