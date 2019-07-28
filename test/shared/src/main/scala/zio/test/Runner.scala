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
  reporter: Reporter[R, L] = new Reporter[Any, L] {
    def report(executedSpec: ExecutedSpec[L]): UIO[Unit] =
      UIO(println(executedSpec.toString))
  }
) { self =>

  // TODO: More fine-grained control / composable managed environments.
  final def run[E](spec: ZSpec[R, E, L]): UIO[ExecutedSpec[L]] =
    environment.use { r =>
      execute(Managed.succeed(r))(spec).flatMap { results =>
        reporter.report(results).provide(r) *> ZIO.succeed(results)
    }
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

  private final def execute[R, E](environment: Managed[Nothing, R])(spec: ZSpec[R, E, L]): UIO[ExecutedSpec[L]] =
    parallel(environment, 5)(spec)

  /**
   * Runs tests in parallel, up to the specified limit.
   */
  def parallel[R, E, L](managed: Managed[E, R], n: Int)(spec: ZSpec[R, E, L]): UIO[ExecutedSpec[L]] =
    spec match {
      case Spec.Suite(label, specs) =>
        ZIO
          .foreachParN(n.toLong)(specs)(parallel[R, E, L](managed, n)(_))
          .map { results =>
            Spec.Suite(label, results.toVector)
          }
      case Spec.Test(label, assert) =>
        val provided = assert.provideManaged(managed)

        provided.foldCauseM(
          e => ZIO.succeed(Spec.Test(label, fail(e))),
          a => ZIO.succeed(Spec.Test(label, a))
        )
    }

  /**
   * Runs tests sequentially.
   */
  def sequential[R, E, L](managed: Managed[E, R])(spec: ZSpec[R, E, L]): UIO[ExecutedSpec[L]] =
    spec match {
      case Spec.Suite(label, specs) =>
        ZIO
          .foreach(specs)(sequential[R, E, L](managed)(_))
          .map { results =>
            Spec.Suite(label, results.toVector)
          }
      case Spec.Test(label, assert) =>
        val provided = assert.provideManaged(managed)

        provided.foldCauseM(
          e => ZIO.succeed(Spec.Test(label, fail(e))),
          a => ZIO.succeed(Spec.Test(label, a))
        )
    }
}
