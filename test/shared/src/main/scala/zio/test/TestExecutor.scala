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

import zio.{ ExecutionStrategy, Has, Layer, UIO, ZIO }

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
abstract class TestExecutor[+R <: Has[_], E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy): UIO[ExecutedSpec[E]]
  def environment: Layer[Nothing, R]
}

object TestExecutor {
  def default[R <: Annotations, E](
    env: Layer[Nothing, R]
  ): TestExecutor[R, E] = new TestExecutor[R, E] {
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy): UIO[ExecutedSpec[E]] =
      spec.annotated
        .provideLayer(environment)
        .foreachExec(defExec)(
          e =>
            e.failureOrCause.fold(
              { case (failure, annotations) => ZIO.succeedNow((Left(failure), annotations)) },
              cause => ZIO.succeedNow((Left(TestFailure.Runtime(cause)), TestAnnotationMap.empty))
            ), {
            case (success, annotations) => ZIO.succeedNow((Right(success), annotations))
          }
        )
        .use(_.fold[UIO[ExecutedSpec[E]]] {
          case Spec.SuiteCase(label, specs, exec) =>
            UIO.succeedNow(Spec.suite(label, specs.mapM(UIO.collectAll(_)).map(_.toVector), exec))
          case Spec.TestCase(label, test, annotations) =>
            test.map {
              case (result, annotations1) =>
                Spec.test(label, UIO.succeedNow(result), annotations ++ annotations1)
            }
        })
    val environment = env
  }
}
