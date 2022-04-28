/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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
import zio.internal.Platform
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.TestRenderer

import java.util.concurrent.TimeUnit

/**
 * A `TestRunner[R, E]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`. Test runners
 * require a test executor, a runtime configuration, and a reporter.
 */
final case class TestRunner[R, E](
  executor: TestExecutor[R, E],
  runtimeConfig: RuntimeConfig = RuntimeConfig.makeDefault(),
  reporter: TestReporter[E] = DefaultTestReporter(TestRenderer.default, TestAnnotationRenderer.default)(Trace.empty),
  bootstrap: Layer[Nothing, TestLogger with ExecutionEventSink] = {
    implicit val emptyTracer = Trace.empty
    val printerLayer =
      TestLogger.fromConsole(Console.ConsoleLive)

    Clock.live ++ (printerLayer >+> sinkLayer) ++ Random.live
  }
) { self =>

  lazy val runtime: Runtime[Any] = Runtime(ZEnvironment.empty, runtimeConfig)

  /**
   * Runs the spec, producing the execution results.
   */
  def run(
    spec: Spec[R, E]
  )(implicit
    trace: Trace
  ): UIO[
    Summary
  ] =
    for {
      start    <- ClockLive.currentTime(TimeUnit.MILLISECONDS)
      summary  <- executor.run(spec, ExecutionStrategy.ParallelN(4))
      finished <- ClockLive.currentTime(TimeUnit.MILLISECONDS)
      duration  = Duration.fromMillis(finished - start)
    } yield summary.copy(duration = duration)

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRun(
    spec: Spec[R, E]
  )(implicit trace: Trace): Unit =
    runtime.unsafeRun(run(spec).provideLayer(bootstrap))

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  def unsafeRunAsync(
    spec: Spec[R, E]
  )(
    k: => Unit
  )(implicit trace: Trace): Unit =
    runtime.unsafeRunAsyncWith(run(spec).provideLayer(bootstrap)) {
      case Exit.Success(v) => k
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRunSync(
    spec: Spec[R, E]
  )(implicit trace: Trace): Exit[Nothing, Unit] =
    runtime.unsafeRunSync(run(spec).unit.provideLayer(bootstrap))

  /**
   * Creates a copy of this runner replacing the reporter.
   */
  def withReporter[E1 >: E](reporter: TestReporter[E1]): TestRunner[R, E] =
    copy(reporter = reporter)

  /**
   * Creates a copy of this runner replacing the runtime configuration.
   */
  def withRuntimeConfig(f: RuntimeConfig => RuntimeConfig): TestRunner[R, E] =
    copy(runtimeConfig = f(runtimeConfig))

  private[test] def buildRuntime(implicit
    trace: Trace
  ): ZIO[Scope, Nothing, Runtime[TestLogger]] =
    bootstrap.toRuntime(runtimeConfig)
}
