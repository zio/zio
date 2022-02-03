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

import zio.{ExecutionStrategy, Layer, Random, ZIO, ZTraceElement}

import java.util.UUID


abstract class TestExecutor[+R, E] {
  def run(spec: ZSpec[R, E], defExec: ExecutionStrategy)(implicit trace: ZTraceElement): ZIO[ExecutionEventSink, Nothing, Unit]
  def environment: Layer[Nothing, R]
}

object TestExecutor {
  def default[R <: Annotations, E](
                                    env: Layer[Nothing, R]
                                  ): TestExecutor[R, E] = new TestExecutor[R, E] {
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

/*
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


 */