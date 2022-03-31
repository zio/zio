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
  ): ZIO[ExecutionEventSink, Nothing, Summary]

  def environment: ZLayer[Scope, Nothing, R]
}

object TestExecutor {

  def default[R <: Annotations, E](
    env: ZLayer[Scope, Nothing, R]
  ): TestExecutor[R, E] = new TestExecutor[R, E] {
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit
      trace: ZTraceElement
    ): ZIO[ExecutionEventSink, Nothing, Summary] =
      for {
        sink      <- ZIO.service[ExecutionEventSink]
        topParent <- SuiteId.newRandom
        _         <- sink.process(ExecutionEvent.SectionStart(List.empty, topParent, List.empty))
        _ <- {
          def loop(
            labels: List[String],
            spec: Spec[Scope, Annotated[TestFailure[E]], Annotated[TestSuccess]],
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
                managed
                  .map(loop(labels, _, exec, ancestors, sectionId))
                  .catchAll(e => sink.process(ExecutionEvent.RuntimeFailure(sectionId, labels, e._1, ancestors)))

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
            }).unit

          val scopedSpec =
            (spec @@ TestAspect.aroundTest(ZTestLogger.default.build.as((x: TestSuccess) => ZIO.succeed(x)))).annotated
              .provideLayer(environment)
          ZIO.scoped {
            loop(List.empty, scopedSpec, defExec, List.empty, topParent)
          }
        }
        _ <- sink.process(
               ExecutionEvent.SectionEnd(List.empty, topParent, List.empty)
             )

        summary <- sink.getSummary
      } yield summary

    val environment = env

    private def extract(result: Either[(TestFailure[E], TestAnnotationMap), (TestSuccess, TestAnnotationMap)]) =
      result match {
        case Left(
              (
                testFailure: TestFailure[
                  E
                ],
                annotations
              )
            ) =>
          (
            Left(
              testFailure
            ),
            annotations
          )
        case Right(
              (
                testSuccess,
                annotations
              )
            ) =>
          (
            Right(
              testSuccess
            ),
            annotations
          )
      }
  }

}
