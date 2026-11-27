/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test

import zio.Clock.ClockLive
import zio._
import zio.test.ReporterEventRenderer.ConsoleEventRenderer

import java.util.concurrent.TimeUnit

/**
 * A `TestRunner[R, E]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`. A test runner
 * needs only a [[TestExecutor]], which provides each test with its environment
 * and reports the results; the runner adds the timing of the whole run.
 * `defaultTestRunner` is a runner for specs that need only the
 * `TestEnvironment`.
 *
 * {{{
 * import zio._
 * import zio.test.{test, _}
 *
 * val spec: Spec[TestEnvironment, Any] =
 *   suite("arithmetic")(
 *     test("addition")(assertTrue(1 + 1 == 2)),
 *     test("multiplication")(assertTrue(2 * 3 == 6))
 *   )
 *
 * val summary: UIO[Summary] =
 *   defaultTestRunner.run("example.ArithmeticSpec", spec)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/running-tests/ Running Tests]]
 */
final case class TestRunner[R, E](executor: TestExecutor[R, E]) { self =>

  /**
   * The runtime that the `unsafe` methods run the spec on, `Runtime.default`.
   */
  val runtime: Runtime[Any] = Runtime.default

  /**
   * Runs the spec, producing the execution results. The returned `Summary`
   * counts the passed, failed and ignored tests, and its duration is measured
   * with the live clock. `fullyQualifiedName` names the spec in the execution
   * events, and `defExec` is the execution strategy for suites that do not set
   * their own.
   */
  def run(fullyQualifiedName: String, spec: Spec[R, E], defExec: ExecutionStrategy = ExecutionStrategy.ParallelN(4))(
    implicit trace: Trace
  ): UIO[Summary] =
    for {
      start   <- ClockLive.instant
      summary <- executor.run(fullyQualifiedName, spec, defExec)
      end     <- ClockLive.instant
    } yield summary.timed(start, end)

  /**
   * Methods that run a spec outside of a ZIO effect, on `runtime`.
   */
  trait UnsafeAPI {

    /**
     * Runs the spec and returns when it has finished. Failed tests do not make
     * it throw.
     */
    def run(spec: Spec[R, E])(implicit trace: Trace, unsafe: Unsafe): Unit

    /**
     * Starts running the spec, returns at once, and calls `k` when the spec has
     * finished.
     */
    def runAsync(spec: Spec[R, E])(k: => Unit)(implicit trace: Trace, unsafe: Unsafe): Unit

    /**
     * Runs the spec and returns the `Exit` of the run when it has finished. The
     * `Exit` is a success when tests fail or die.
     */
    def runSync(spec: Spec[R, E])(implicit trace: Trace, unsafe: Unsafe): Exit[Nothing, Unit]
  }

  /**
   * Runs specs outside of a ZIO effect. Each method requires an implicit
   * `Unsafe`.
   */
  val unsafe: UnsafeAPI =
    new UnsafeAPI {

      /**
       * An unsafe, synchronous run of the specified spec. Failed tests do not
       * make it throw.
       */
      def run(spec: Spec[R, E])(implicit trace: Trace, unsafe: Unsafe): Unit =
        runtime.unsafe
          .run(self.run("Test Task name unavailable in this context.", spec))
          .getOrThrowFiberFailure()

      /**
       * An unsafe, asynchronous run of the specified spec. It returns at once
       * and calls `k` when the spec has finished.
       */
      def runAsync(spec: Spec[R, E])(k: => Unit)(implicit trace: Trace, unsafe: Unsafe): Unit = {
        val fiber =
          runtime.unsafe.fork(self.run("Test Task name unavailable in this context.", spec))
        fiber.unsafe.addObserver { exit => exit.getOrThrowFiberFailure(); k }
      }

      /**
       * An unsafe, synchronous run of the specified spec, returning the `Exit`
       * of the run. The `Exit` is a success when tests fail or die.
       */
      def runSync(spec: Spec[R, E])(implicit trace: Trace, unsafe: Unsafe): Exit[Nothing, Unit] =
        runtime.unsafe.run(self.run("Test Task name unavailable in this context.", spec).unit)
    }
}
