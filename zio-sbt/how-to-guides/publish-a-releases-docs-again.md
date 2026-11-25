# Publish a Release's Docs Again

> `release-docs` needs `release`, and Maven Central rejects a version it already has. So when `release` has published a version but `release-docs` failed (for example, because the npm package was not set up yet), running `release` again fails with `already exists` and the docs are skipped again.

To publish the docs without the Maven release, run the CI workflow by hand with the `docs-only` input set to `true`:

```bash
gh workflow run ci.yml --ref main -f docs-only=true
```

Or open **Actions → CI → Run workflow**, pick the branch, and type `true` in `docs-only`. The `release` job is skipped, `release-docs` publishes the docs, and `notify-docs-release` runs once they are published. The docs version is the newest `v*` tag in the repository; the docs content comes from the branch or tag the workflow runs on.

The run uses the `ci.yml` of that branch or tag, so the input is only there once the workflow has been regenerated with this version of the plugin. Every `workflow_dispatch` trigger of `ci.yml` gets the input, including one in a custom `ciWorkflowTriggers`, as long as the workflow has a `release-docs` job.

If the failure is caught right away, **Re-run failed jobs** on the original run also works: it keeps the successful `release` result and only runs `release-docs` and `notify-docs-release` again. **Re-run all jobs** runs `release` again and fails.
