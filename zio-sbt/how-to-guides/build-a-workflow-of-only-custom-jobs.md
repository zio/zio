# Build a Workflow of Only Custom Jobs

> A build that replaces every stock job, for instance one whose jobs are generated from its module list, can also drop the two things the plugin adds around them: the aggregate `ci` job, and the workflow-level `permissions` block.

```scala
ciReportSuccessfulJobs := Seq.empty // no aggregate `ci` job
ciWorkflowPermissions  := None      // inherit the repository's default token permissions
ciConcurrency          := None
ciWorkflowEnv          := Map.empty
```

Omit `permissions` when a job needs more than the default `contents: read`, such as one that pushes a release tag with `GITHUB_TOKEN`. `None` is not the same as `Some(Map.empty)`, which renders `permissions: {}` and grants the token nothing.
