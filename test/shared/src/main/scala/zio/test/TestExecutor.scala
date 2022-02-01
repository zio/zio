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

import zio.{Chunk, ExecutionStrategy, Layer, Random, Ref, UIO, ZEnvironment, ZIO, ZManaged, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.UUID

/**
 * A `TestExecutor[R, E]` is capable of executing specs that require an
 * environment `R` and may fail with an `E`.
 */
abstract class TestExecutor[+R, E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[ExecutedSpec[E]]
  def environment: Layer[Nothing, R]
}

trait ExecutionEventSink {
  def process(event: ExecutionEvent): UIO[Unit]
}
sealed trait ReporterEvent
case class SectionState(results: Chunk[ExecutionEvent.Test[_]]) extends ReporterEvent
case class Failure[E](
                    labelsReversed: List[String],
                    failure: TestFailure[E],
                    ancestors: List[UUID]
                  ) extends ReporterEvent

object ExecutionEventSink {
  def make[R](stateReporter: ReporterEvent => ZIO[R, Nothing, Any])(implicit
                                                                    trace: ZTraceElement
  ): ZIO[R, Nothing, ExecutionEventSink] = { 
    for {
      sectionState <- Ref.make(Map.empty[UUID, SectionState])
      env <- ZIO.environment[R]
    } yield new ExecutionEventSink {
      override def process(event: ExecutionEvent): UIO[Unit] = {
        event match {
          case testEvent @ ExecutionEvent.Test(labelsReversed, test, annotations, ancestors) =>
            ancestors match {
              case Nil => 
                stateReporter(SectionState(Chunk(testEvent))).provideEnvironment(env).unit
              case head :: _ =>
                sectionState.update( curState =>
                  curState.get(head) match {
                    case Some(SectionState(results)) =>
                      val newResults = results :+ testEvent
                      curState.updated(head, SectionState(newResults))
                    case None =>
                      curState.updated(head, SectionState(Chunk(testEvent)))
                  }
                )
            }
          case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) => 
            sectionState.update(curState => curState.updated(id, SectionState(Chunk.empty)))
          case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) => 
            sectionState.modify( curState =>
              curState.get(id) match {
                case Some(sectionState) =>
                  (sectionState, curState - id)
                case None =>
                  (SectionState(Chunk.empty), curState)
              }
            ).flatMap(finalSectionState =>
              stateReporter(finalSectionState).provideEnvironment(env).unit
            )
          case ExecutionEvent.Failure(labelsReversed, failure, ancestors) => 
            stateReporter(Failure(labelsReversed, failure, ancestors)).provideEnvironment(env).unit
        }
        
      }
    }
  }

}

abstract class TestExecutor2[+R, E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): ZIO[ExecutionEventSink, Nothing, Unit]
  def environment: Layer[Nothing, R]
}

object TestExecutor2 {
  def default[R <: Annotations, E](
                                    env: Layer[Nothing, R]
                                  ): TestExecutor2[R, E] = new TestExecutor2[R, E] {
    // TODO Instead of building an ExecutedSpec, we want to write results to our new structure.
    //      Will return a UIO[Unit]
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement):ZIO[ExecutionEventSink, Nothing, Unit] = {
      def loop(labels: List[String], spec: Spec[R, Annotated[TestFailure[E]], Annotated[TestSuccess]], exec: ExecutionStrategy, sink: ExecutionEventSink, ancestors: List[UUID]):ZIO[R with Random, Nothing, Unit]   = {
        spec.caseValue match {
          case Spec.ExecCase(exec, spec) => loop(labels, spec, exec, sink, ancestors)

          case Spec.LabeledCase(label, spec) =>
            loop(label :: labels, spec, exec, sink, ancestors)
          case Spec.ManagedCase(managed) =>
            managed
              .use(loop(labels, _, exec, sink, ancestors))
              .catchAll(
                e =>
                  sink.process(ExecutionEvent.Failure(labels, e._1, ancestors))
              )

          case Spec.MultipleCase(specs) =>
            ZIO.uninterruptibleMask(restore => for {
              uuid <- Random.nextUUID
              _ <- sink.process(ExecutionEvent.SectionStart(labels, uuid, ancestors))
              newAncestors = uuid :: ancestors
              _ <- restore(ZIO.foreachExec(specs)(exec)(loop(labels, _, exec, sink, newAncestors))).ensuring(
                sink.process(ExecutionEvent.SectionEnd(labels, uuid, ancestors))
              )
            } yield ())
          case Spec.TestCase(test: ZIO[R, Annotated[TestFailure[E]], Annotated[TestSuccess]], staticAnnotations) =>
            for {
              result <- test.either
              _ <- result match {
                case Left((testFailure: TestFailure[E], annotations)) =>
                  sink.process(ExecutionEvent.Test(labels, Left(testFailure), staticAnnotations ++ annotations, ancestors))
                case Right((testSuccess, annotations)) =>
                  sink.process(ExecutionEvent.Test(labels, Right(testSuccess), staticAnnotations ++ annotations, ancestors))
              }
            } yield ()
        }
      }
      for {
        sink <- ZIO.service[ExecutionEventSink]
        //        _ <- Random.live(loop(List.empty, spec.annotated, defExec, sink, List.empty))
        _ <- loop(List.empty, spec.annotated, defExec, sink, List.empty).provide(Random.live, env)
      } yield  ()
    }


    val environment = env
  }
}

object TestExecutor {
  def default[R <: Annotations, E](
    env: Layer[Nothing, R]
  ): TestExecutor[R, E] = new TestExecutor[R, E] {
    // TODO Instead of building an ExecutedSpec, we want to write results to our new structure.
    //      Will return a UIO[Unit]
    def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): UIO[ExecutedSpec[E]] =
      spec.annotated
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
        .use(_.foldManaged[Any, Nothing, ExecutedSpec[E]](defExec) {
          case Spec.ExecCase(_, spec) =>
            ZManaged.succeedNow(spec)
          case Spec.LabeledCase(label, spec) =>
            ZManaged.succeedNow(ExecutedSpec.labeled(label, spec))
          case Spec.ManagedCase(managed) =>
            managed
          case Spec.MultipleCase(specs) =>
            ZManaged.succeedNow(ExecutedSpec.multiple(specs))
          case Spec.TestCase(test, staticAnnotations) =>
            test.map { case (result, dynamicAnnotations) =>
              ExecutedSpec.test(result, staticAnnotations ++ dynamicAnnotations)
            }.toManaged
        }.useNow)
    val environment = env
  }
}
