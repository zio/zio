/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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
import zio.internal.{ Platform, PlatformLive }

/**
 * A `Runner[R, E, L]` encapsulates all the logic necessary to run specs that
 * require an environment `R` and may fail with an error `E`, using labels of
 * type `L`. Runners have main functions, so if they are extended from
 * (non-abstract) classes, they can be run by the JVM / Scala.js.
 */
abstract class Runner[+R, L](
  environment: Managed[Nothing, R],
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: Reporter[L] = new Reporter[L] {
    def report(executedSpec: ExecutedSpec[L]): UIO[Unit] =
      UIO(println(executedSpec.toString))
  }
) { self =>

  // TODO: More fine-grained control / composable managed environments.
  final def run[E](spec: ZSpec[R, E, L]): UIO[ExecutedSpec[L]] =
    execute(spec, ExecutionStrategy.ParallelN(4)).flatMap { results =>
      reporter.report(results) *> ZIO.succeed(results)
    }

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  final def unsafeRunAsync[E](spec: ZSpec[R, E, L])(k: ExecutedSpec[L] => Unit): Unit =
    Runtime((), platform).unsafeRunAsync(run(spec)) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => throw FiberFailure(c)
    }

  final def main(args: Array[String]): Unit =
    // TODO: Reflectively find and run all classes that use this runner?
    ???

  private final def execute[E](spec: ZSpec[R, E, L], defExec: ExecutionStrategy): UIO[ExecutedSpec[L]] =
    spec.foldM[Any, Nothing, ExecutedSpec[L]](defExec) {
      case Spec.SuiteCase(label, specs, exec) => ZIO.succeed(Spec.suite(label, specs, exec))

      case Spec.TestCase(label, test) =>
        val provided = test.provideManaged(environment)

        provided.foldCauseM(
          e => ZIO.succeed(Spec.test(label, fail(e))),
          a => ZIO.succeed(Spec.test(label, a))
        )
    }
}
