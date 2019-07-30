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

import zio.{ Managed, UIO, ZIO }

trait TestExecutor[L, -T] {
  def execute(spec: Spec[L, T], defExec: ExecutionStrategy): UIO[ExecutedSpec[L]]
}
object TestExecutor {
  def managed[R, E, L](environment: Managed[E, R]): TestExecutor[L, ZTest[R, E]] =
    new TestExecutor[L, ZTest[R, E]] {
      def execute(spec: ZSpec[R, E, L], defExec: ExecutionStrategy): UIO[ExecutedSpec[L]] =
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
}
