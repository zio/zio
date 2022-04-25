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
import zio.test.render.{ConsoleRenderer, LogLine}
import zio.test.render.LogLine.Fragment.Style
import zio.test.render.LogLine.Message
import zio.{ExecutionStrategy, ZIO, ZTraceElement}

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
abstract class TestExecutor[+R, E] {
  def run(spec: Spec[R, E], defExec: ExecutionStrategy)(implicit
    trace: ZTraceElement
  ): UIO[Summary]
}

object TestExecutor {

  def default[R, E](
    sharedSpecLayer: ZLayer[Any, E, R],
    freshLayerPerSpec: ZLayer[Any, Nothing, TestEnvironment with ZIOAppArgs with Scope],
    sinkLayer: Layer[Nothing, ExecutionEventSink],
    eventHandlerZ: ZTestEventHandler
  ): TestExecutor[R with TestEnvironment with ZIOAppArgs with Scope, E] =
    new TestExecutor[R with TestEnvironment with ZIOAppArgs with Scope, E] {
      def run(spec: Spec[R with TestEnvironment with ZIOAppArgs with Scope, E], defExec: ExecutionStrategy)(implicit
        trace: ZTraceElement
      ): UIO[
        Summary
      ] =
        (for {
          sink     <- ZIO.service[ExecutionEventSink]
          summary  <- Ref.make[Summary](Summary(0, 0, 0, ""))
          topParent = SuiteId.global
          _ <- {
            def loop(
              labels: List[String],
              spec: Spec[Scope, E],
              exec: ExecutionStrategy,
              ancestors: List[SuiteId],
              sectionId: SuiteId
            ): ZIO[Scope, Nothing, Unit] =
              spec.caseValue match {
                case Spec.ExecCase(exec, spec) =>
                  loop(labels, spec, exec, ancestors, sectionId)

                case Spec.LabeledCase(label, spec) =>
                  loop(label :: labels, spec, exec, ancestors, sectionId)

                case Spec.ScopedCase(managed) =>
                  ZIO
                    .scoped(
                      managed
                        .flatMap(loop(labels, _, exec, ancestors, sectionId))
                    )
                    .catchAllCause { e =>
                      val event =
                        ExecutionEvent.RuntimeFailure(sectionId, labels, TestFailure.Runtime(e), ancestors)
                      summary.update(
                        _.add(event)
                      ) *>
                        sink.process(
                          event
                        )
                    }

                case Spec.MultipleCase(specs) =>
                  ZIO.uninterruptibleMask(restore =>
                    for {
                      newMultiSectionId <- SuiteId.newRandom
                      newAncestors       = sectionId :: ancestors
                      _                 <- sink.process(ExecutionEvent.SectionStart(labels, newMultiSectionId, newAncestors))
                      _ <-
                        restore(
                          ZIO.foreachExec(specs)(exec)(spec =>
                            loop(labels, spec, exec, newAncestors, newMultiSectionId)
                          )
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
                  (for {
                    result <- test.either
                    event =
                      ExecutionEvent
                        .Test(
                          labels,
                          result,
                          staticAnnotations ++ extractAnnotations(result),
                          ancestors,
                          1L,
                          sectionId
                        )
                    _ <- summary.update(_.add(event)) *> sink.process(event) *> eventHandlerZ.handle(event)
                  } yield ()).catchAllCause { e =>
                    val event = ExecutionEvent.RuntimeFailure(sectionId, labels, TestFailure.Runtime(e), ancestors)
                    ConsoleRenderer.render(e, labels).foreach(println)
                    summary.update(_.add(event)) *> sink.process(event)
                  }
              }

            val scopedSpec =
              (spec @@ TestAspect.aroundTest(
                ZTestLogger.default.build.as((x: TestSuccess) => ZIO.succeed(x))
              )).annotated
                .provideSomeLayer[R](freshLayerPerSpec)
                .provideLayerShared(sharedSpecLayer.tapErrorCause { e =>
                  sink.process(
                    ExecutionEvent.RuntimeFailure(
                      SuiteId(-1),
                      List("Top level layer construction failure. No tests will execute."),
                      TestFailure.Runtime(e),
                      List.empty
                    )
                  )
                })

            ZIO.scoped {
              loop(List.empty, scopedSpec, defExec, List.empty, topParent)
            } *>
              sink.process(
                ExecutionEvent.TopLevelFlush(
                  topParent
                )
              )
          }
          summary <- summary.get
        } yield summary).provideLayer(sinkLayer)

      private def extractAnnotations(result: Either[TestFailure[E], TestSuccess]) =
        result match {
          case Left(testFailure)  => testFailure.annotations
          case Right(testSuccess) => testSuccess.annotations
        }
    }

}
