/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

/**
 * A `TestExecutor[R, E, L, T, S]` is capable of executing specs containing
 * tests of type `T`, annotated with labels of type `L`, that require an
 * environment `R` and may fail with an `E` or succeed with a `S`.
 */
trait TestExecutor[+R, E, L, -T, +S] {
  def run(spec: ZSpec[R, E, L, T], defExec: ExecutionStrategy): UIO[ExecutedSpec[E, L, S]]
  def environment: Managed[Nothing, R]
}

object TestExecutor {
  def managed[R <: Annotations, E, L, S](
    env: Managed[Nothing, R]
  ): TestExecutor[R, E, L, S, S] = new TestExecutor[R, E, L, S, S] {
    def run(spec: ZSpec[R, E, L, S], defExec: ExecutionStrategy): UIO[ExecutedSpec[E, L, S]] =
      spec.annotated
        .provideManaged(environment)
        .foreachExec(defExec)(
          e =>
            e.failureOrCause.fold(
              { case (failure, annotations) => ZIO.succeed((Left(failure), annotations)) },
              cause => ZIO.succeed((Left(TestFailure.Runtime(cause)), TestAnnotationMap.empty))
            ), {
            case (success, annotations) => ZIO.succeed((Right(success), annotations))
          }
        )
    val environment = env
  }
}
