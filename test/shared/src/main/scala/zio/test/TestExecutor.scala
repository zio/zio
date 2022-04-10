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
import zio.{ExecutionStrategy, ZIO, ZTraceElement}

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
abstract class TestExecutor[+R, E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit
    trace: ZTraceElement
  ): UIO[Summary]

  def environment: ZLayer[Scope, Nothing, R]
}

object TestExecutor {

  def default[R <: Annotations, E](
    env: ZLayer[Scope, Nothing, R],
    sinkLayer: Layer[Nothing, ExecutionEventSink]
  ): TestExecutor[R, E] = new TestExecutor[R, E] {
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit
      trace: ZTraceElement
    ): UIO[
      Summary
    ] =
      (for {
        sink      <- ZIO.service[ExecutionEventSink]
        topParent <- SuiteId.newRandom
        _ <- {
          def loop(
            labels: List[String],
            spec: Spec[Scope, E, Annotated[TestSuccess]],
            exec: ExecutionStrategy,
            ancestors: List[SuiteId],
            sectionId: SuiteId
          ): ZIO[Scope, Nothing, Unit] =
            (spec.caseValue match {
              case Spec.ExecCase(exec, spec) =>
                loop(labels, spec, exec, ancestors, sectionId)

              case Spec.LabeledCase(label, spec) =>
                loop(label :: labels, spec, exec, ancestors, sectionId)

              case Spec.ScopedCase(managed) =>
                ZIO.scoped(
                  managed
                    .flatMap(loop(labels, _, exec, ancestors, sectionId))
                )

              case Spec.MultipleCase(specs) =>
                ZIO.uninterruptibleMask(restore =>
                  for {
                    newMultiSectionId <- SuiteId.newRandom
                    newAncestors       = sectionId :: ancestors
                    _                 <- sink.process(ExecutionEvent.SectionStart(labels, newMultiSectionId, newAncestors))
                    _ <-
                      restore(
                        ZIO.foreachExec(specs)(exec)(spec => loop(labels, spec, exec, newAncestors, newMultiSectionId))
                      )
                        .ensuring(
                          sink.process(ExecutionEvent.SectionEnd(labels, newMultiSectionId, newAncestors))
                        )
                  } yield ()
                )
              case Spec.TestCase(
                    test,
                    staticAnnotations: TestAnnotationMap
                  ) =>
                for {
                  result                  <- test.either
                  (testEvent, annotations) = extract(result)
                  _ <-
                    sink.process(
                      ExecutionEvent
                        .Test(labels, testEvent, staticAnnotations ++ annotations, ancestors, 1L, sectionId)
                    )
                } yield ()
            }).catchAllCause { e =>
              sink.process(ExecutionEvent.RuntimeFailure(sectionId, labels, TestFailure.Runtime(e), ancestors))
            }.unit

          val scopedSpec =
            (spec @@ TestAspect.aroundTest(ZTestLogger.default.build.as((x: TestSuccess) => ZIO.succeed(x)))).annotated
              .provideLayer(environment)
          ZIO.scoped {
            loop(List.empty, scopedSpec, defExec, List.empty, topParent)
          }
        }

        summary <- sink.getSummary
      } yield summary).provideLayer(sinkLayer)

    val environment = env

    private def extract(result: Either[TestFailure[E], (TestSuccess, TestAnnotationMap)]) =
      result match {
        case Left(testFailure)                 => (Left(testFailure), testFailure.annotations)
        case Right((testSuccess, annotations)) => (Right(testSuccess), annotations)
      }
  }

}
