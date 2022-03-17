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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
abstract class TestExecutor[+R, E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[ExecutedSpec[E]]
  def environment: ZLayer[Scope, Nothing, R]
}

object TestExecutor {
  def default[R <: Annotations, E](
    env: ZLayer[Scope, Nothing, R]
  ): TestExecutor[R, E] = new TestExecutor[R, E] {
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit
      trace: ZTraceElement
    ): UIO[ExecutedSpec[E]] =
      ZIO.scoped {
        (spec @@ TestAspect.aroundTest(ZTestLogger.default.build.as((x: TestSuccess) => ZIO.succeed(x)))).annotated
          .provideLayer(environment)
          .foreachExec(defExec)(
            e =>
              e.failureOrCause.fold(
                { case (failure, annotations) => ZIO.succeedNow((Left(failure), annotations)) },
                cause => ZIO.succeedNow((Left(TestFailure.Runtime(cause)), TestAnnotationMap.empty))
              ),
            { case (success, annotations) =>
              ZIO.succeedNow((Right(success), annotations))
            }
          )
          .flatMap { spec =>
            ZIO.scoped {
              spec.foldScoped[Scope, Nothing, ExecutedSpec[E]](defExec) {
                case Spec.ExecCase(_, spec) =>
                  ZIO.succeedNow(spec)
                case Spec.LabeledCase(label, spec) =>
                  ZIO.succeedNow(ExecutedSpec.labeled(label, spec))
                case Spec.ScopedCase(scoped) =>
                  scoped
                case Spec.MultipleCase(specs) =>
                  ZIO.succeedNow(ExecutedSpec.multiple(specs))
                case Spec.TestCase(test, staticAnnotations) =>
                  test.map { case (result, dynamicAnnotations) =>
                    ExecutedSpec.test(result, staticAnnotations ++ dynamicAnnotations)
                  }
              }
            }
          }
      }
    val environment = env
  }
}
