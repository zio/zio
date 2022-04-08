package zio.test

import zio.test.sbt._
import zio.test._
import sbt._

/**
 * =General Test Pieces=
 *
 * [[zio.test.ZIOSpecAbstract]]
 *
 * Contains test logic and how it should be executed. The most important method is:
 *
 * [[zio.test.ZIOSpecAbstract.runSpec]]
 *        - Runtime interaction
 *        - build TestRunner
 *        - fold aspects into logic
 *        - Builds `TestExecutor` and passes spec to it
 *        - returns summary
 *
 * [[ zio.test.TestExecutor ]]
 *
 * Capable of executing specs that require an environment `R` and may fail with an `E`
 *    Recursively traverses tree of specs, executing suites/tests in parallel
 *
 *
 * [[zio.test.TestRunner]]
 *
 * Encapsulates the logic necessary to run specs that require an environment `R` and may fail with an error `E`.
 * {{{
 * class TestRunner[R, E](
 *                       executor: TestExecutor[R, E],
 *                       runtimeConfig: RuntimeConfig,
 *                       reporter: TestReporter[E],
 *                       bootstrap: Layer[Nothing, TestLogger with ExecutionEventSink]
 * )
 * }}}
 *
 * ==SBT-specific pieces==
 *
 * [[sbt.testing.Task]]
 *
 * SBT needs everything packaged in these to run tests/suites
 *
 * [[zio.test.sbt.ZTestTask]] extends [[Task]]
 *
 * Contains a ZIOSpecAbstract and everything that SBT needs to run/report it.
 *
 * [[sbt.testing.Runner]]
 *
 * SBT delegates to `Runner` clients for managing/executing test
 *
 * [[zio.test.sbt.ZTestRunner]] extends [[Runner]]
 *
 * Receives all Specs found by the `FingerPrint` and merges them into a single `ZTestTask`
 *
 * [[zio.test.sbt.ZioSpecFingerprint]]
 * What SBT needs to find your tests. Finds `ZIOSpecAbstract` implementations in your codebase.
 *
 * [[sbt.testing.Framework]]
 * Required for SBT to recognize ZIO-test as a legitimate test framework.
 *
 * [[zio.test.sbt.ZTestFramework]] extends [[Framework]]
 * Defines `ZIOSpecFingerPrint` & `ZTestRunner` and passes them to SBT
 *
 */
package object sbt {}
