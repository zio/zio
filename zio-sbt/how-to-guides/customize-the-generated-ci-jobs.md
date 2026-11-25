# Customize the Generated CI Jobs

> Jobs are values, so they can be transformed or replaced. The types live in `zio.sbt.githubactions` and the reusable steps in `zio.sbt.ZioSbtCiPlugin`; neither is exported through `autoImport`, so import them explicitly.

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

`Checkout`, `SetupJava(version)`, `SetupSBT`, `CacheDependencies`, `SetupNodeJs` and `SetSwapSpace` are all available; the ones defined as settings need `.value`. (`SetupLibuv` is deprecated and no longer used by the generated jobs; Scala Native does not need libuv, so drop it from custom jobs.)
