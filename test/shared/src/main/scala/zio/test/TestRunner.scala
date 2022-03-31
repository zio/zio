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

import zio._
import zio.internal.Platform
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.render.TestRenderer

/**
 * A `TestRunner[R, E]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`. Test runners
 * require a test executor, a runtime configuration, and a reporter.
 */
final case class TestRunner[R, E](
  executor: TestExecutor[R, E],
  runtimeConfig: RuntimeConfig = RuntimeConfig.makeDefault(),
  reporter: TestReporter[E] =
    DefaultTestReporter(TestRenderer.default, TestAnnotationRenderer.default)(ZTraceElement.empty),
  bootstrap: Layer[Nothing, TestLogger with ExecutionEventSink] = {
    implicit val emptyTracer = ZTraceElement.empty
    val printerLayer =
      Console.live.to(TestLogger.fromConsole)

    val sinkLayer = ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live

    Clock.live ++ (printerLayer >+> sinkLayer) ++ Random.live
  }
) { self =>

  lazy val runtime: Runtime[Any] = Runtime(ZEnvironment.empty, runtimeConfig)

  /**
   * Runs the spec, producing the execution results.
   */
  def run(
    spec: ZSpec[R, E]
  )(implicit
    trace: ZTraceElement
  ): URIO[ExecutionEventSink, Summary] =
    executor.run(spec, ExecutionStrategy.ParallelN(4)).timed.flatMap { case (duration, summary) =>
      // TODO Why is duration 0 here? Resolve for #6482
      ZIO.succeed(summary)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRun(
    spec: ZSpec[R, E]
  )(implicit trace: ZTraceElement): Unit =
    runtime.unsafeRun(run(spec).provideLayer(bootstrap))

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  def unsafeRunAsync(
    spec: ZSpec[R, E]
  )(
    k: => Unit
  )(implicit trace: ZTraceElement): Unit =
    runtime.unsafeRunAsyncWith(run(spec).provideLayer(bootstrap)) {
      case Exit.Success(v) => k
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  /**
   * An unsafe, synchronous run of the specified spec.
   */
  def unsafeRunSync(
    spec: ZSpec[R, E]
  )(implicit trace: ZTraceElement): Exit[Nothing, Unit] =
    runtime.unsafeRunSync(run(spec).unit.provideLayer(bootstrap))

  /**
   * Creates a copy of this runner replacing the platform
   */
  @deprecated("use withRuntimeConfig", "2.0.0")
  def withPlatform(f: Platform => Platform): TestRunner[R, E] =
    withRuntimeConfig(f)

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
    trace: ZTraceElement
  ): ZIO[Scope, Nothing, Runtime[TestLogger]] =
    bootstrap.toRuntime(runtimeConfig)
}
