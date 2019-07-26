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
abstract class Runner[+R, E, L](
  environment: Managed[E, R],
  platform: Platform = PlatformLive.makeDefault().withReportFailure(_ => ()),
  reporter: Reporter[L] = ???
) { self =>

  // TODO: More fine-grained control / composable managed environments.
  final def run(spec: ZSpec[R, E, L]): IO[E, ExecutedSpec[Any, E, L]] =
    environment.use { env =>
      execute(spec.provide(env)).flatMap { results =>
        reporter.report(results) *> ZIO.succeed(results)
      }
    }

  /**
   * An unsafe, asynchronous run of the specified spec.
   */
  final def unsafeRunAsync(spec: ZSpec[R, E, L])(k: ExecutedSpec[Any, E, L] => Unit): Unit =
    Runtime((), platform).unsafeRunAsync(run(spec)) {
      case Exit.Success(v) => k(v)
      case Exit.Failure(c) => println(c.toString)
    }

  final def main(args: Array[String]): Unit =
    // TODO: Reflectively find and run all classes that use this runner?
    ???

  private final def execute(spec: ZSpec[Any, E, L]): UIO[ExecutedSpec[Any, E, L]] = ???

  /**
   * Runs tests in parallel, up to the specified limit.
   */
  def parallel[R, E, L](n: Int)(spec: ZSpec[R, E, L]): ZIO[R, Nothing, ExecutedSpec[R, E, L]] =
    spec match {
      case ZSpec.Suite(label, specs) =>
        ZIO
          .foreachParN(n.toLong)(specs)(parallel[R, E, L](n)(_))
          .map { results =>
            ZSpec.Suite((label, AssertResult.Pending), results.toVector)
          }
      case ZSpec.Test(label, assert) =>
        assert.foldCauseM(
          e => ZIO.succeed(ZSpec.Test((label, error(e)), assert)),
          a => ZIO.succeed(ZSpec.Test((label, a), assert))
        )
      case ZSpec.Concat(head, tail) =>
        parallel(n)(head)
          .zipWithPar(ZIO.foreachParN(n.toLong)(tail)(parallel[R, E, L](n)(_)))((h, t) => ZSpec.Concat(h, t.toVector))
    }

  /**
   * Runs tests sequentially.
   */
  def sequential[R, E, L](spec: ZSpec[R, E, L]): ZIO[R, Nothing, ExecutedSpec[R, E, L]] =
    spec match {
      case ZSpec.Suite(label, specs) =>
        ZIO
          .foreach(specs)(sequential[R, E, L](_))
          .map { results =>
            ZSpec.Suite((label, AssertResult.Pending), results.toVector)
          }
      case ZSpec.Test(label, assert) =>
        assert.foldCauseM(
          e => ZIO.succeed(ZSpec.Test((label, error(e)), assert)),
          a => ZIO.succeed(ZSpec.Test((label, a), assert))
        )
      case ZSpec.Concat(head, tail) =>
        sequential(head).zipWith(ZIO.foreach(tail)(sequential[R, E, L]))((h, t) => ZSpec.Concat(h, t.toVector))
    }

  private def error[E](e: Cause[E]): TestResult =
    AssertResult.failure(FailureDetails.Other(e.toString))
}
