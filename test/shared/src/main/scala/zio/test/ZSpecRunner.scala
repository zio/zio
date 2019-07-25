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

trait ZSpecRunner {
  def run[E, L](spec: ZSpec[Any, E, L]): UIO[ExecutedSpec[Any, E, L]]
}

object ZSpecRunner {

  /**
   * Runs tests in parallel, up to the specified limit.
   */
  def parallel(n: Int): ZSpecRunner = new ZSpecRunner {
    def run[E, L](spec: ZSpec[Any, E, L]): UIO[ExecutedSpec[Any, E, L]] =
      spec match {
        case ZSpec.Suite(label, specs) =>
          ZIO
            .foreachParN(n.toLong)(specs)(run)
            .map { results =>
              if (results.exists(_.exists(_._2.failure)))
                ZSpec.Suite((label, failure), results.toVector)
              else
                ZSpec.Suite((label, success), results.toVector)
            }
        case ZSpec.Test(label, assert) =>
          assert.fold(
            e => ZSpec.Test((label, error(e)), assert),
            a => ZSpec.Test((label, a), assert)
          )
        case ZSpec.Concat(head, tail) =>
          run(head).zipWithPar(ZIO.foreachParN(n.toLong)(tail)(run))((h, t) => ZSpec.Concat(h, t.toVector))
      }
  }

  /**
   * Runs tests sequentially.
   */
  val sequential: ZSpecRunner = new ZSpecRunner {
    def run[E, L](spec: ZSpec[Any, E, L]): UIO[ExecutedSpec[Any, E, L]] =
      spec match {
        case ZSpec.Suite(label, specs) =>
          ZIO
            .foreach(specs)(run)
            .map { results =>
              if (results.exists(_.exists(_._2.failure)))
                ZSpec.Suite((label, failure), results.toVector)
              else
                ZSpec.Suite((label, success), results.toVector)
            }
        case ZSpec.Test(label, assert) =>
          assert.fold(
            e => ZSpec.Test((label, error(e)), assert),
            a => ZSpec.Test((label, a), assert)
          )
        case ZSpec.Concat(head, tail) =>
          run(head).zipWith(ZIO.foreach(tail)(run))((h, t) => ZSpec.Concat(h, t.toVector))
      }
  }

  private val success = AssertResult.success(FailureDetails.Other(""))
  private val failure = AssertResult.failure(FailureDetails.Other(""))

  private def error[E](e: E): TestResult =
    AssertResult.failure(FailureDetails.Other(e.toString))
}
