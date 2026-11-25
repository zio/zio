# Keep the Generated Workflow in Sync

> The generated files are meant to be committed and never edited by hand. To stop them drifting from the build, run the check in CI — the default `lint` job already does:

```bash
sbt ciCheckGithubWorkflow
```

It regenerates the workflows and fails if the result differs from what is committed, naming the files that are stale. If a generated file is edited directly, the next run of this check fails, which is what keeps `build.sbt` the single source of truth.
