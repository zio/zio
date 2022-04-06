# General Test Pieces

`zio.test.ZIOSpecAbstract`

Contains and executes test logic

    #runSpec
        - Runtime interaction
        - build TestRunner
        - fold aspects into logic
        - Builds `TestExecutor` and passes spec to it
        - returns summary

`zio.test.TestExecutor[+R, E]`

Capable of executing specs that require an environment `R` and may fail with an `E`
    Recursively traverses tree of specs, executing suites/tests in parallel
  

`zio.test.TestRunner[R, E]` 

Encapsulates the logic necessary to run specs that require an environment `R` and may fail with an error `E`.
```scala
class TestRunner[R, E](
                        executor: TestExecutor[R, E],
                        runtimeConfig: RuntimeConfig = RuntimeConfig.makeDefault(),
                        reporter: TestReporter[E] =
                        DefaultTestReporter(TestRenderer.default, TestAnnotationRenderer.default)(ZTraceElement.empty),
                        bootstrap: Layer[Nothing, TestLogger with ExecutionEventSink]
)
```

## SBT-specific pieces

`sbt.testing.Task`

SBT needs everything packaged in these to run tests/suites

`zio.test.sbt.ZTestTask extends Task`

Contains a ZIOSpecAbstract and everything that SBT needs to run/report it.

`sbt.testing.Runner`

SBT delegates to `Runner` clients for managing/executing test

`zio.test.sbt.ZTestRunner extends TestRunner`

Receives all Specs found by the `FingerPrint` and merges them into a single `ZTestTask`
TODO Farm ExecutionEvents out to appropriate thin "Monitor" Tasks that keep things flat enough for SBT to report on

`zio.test.sbt.ZioSpecFingerprint`
What SBT needs to find your tests. Finds `ZIOSpecAbstract` implementations.

`sbt.testing.Framework`
Required for SBT to recognize ZIO-test as a legitimate test framework.

`zio.test.sbt.ZTestFramework extends Framework`
Defines `ZIOSpecFingerPrint` & `ZTestRunner` and passes them to SBT

