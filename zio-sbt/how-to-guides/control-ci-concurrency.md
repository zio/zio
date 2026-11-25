# Control CI Concurrency

> By default one run per branch is kept and in-progress runs are cancelled. `cancelInProgress` accepts an expression as well as a boolean, which is what lets a workflow cancel superseded pull request runs while letting releases finish:

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
