/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.interop.javaz
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.nio.channels.CompletionHandler
import java.util.concurrent.{CompletableFuture, CompletionStage}

/**
 * JVM and Scala Native constructors of `Task` for Java interop. No object mixes
 * this trait in; the `ZIO` object defines the same methods.
 */
private[zio] trait TaskPlatformSpecific {

  /**
   * Imports a callback-style API that takes a `CompletionHandler` into a
   * `Task`. `op` is called each time the effect runs and receives the handler:
   * `completed` succeeds the task with the result, and `failed` fails it with
   * the exception (a `VirtualMachineError` becomes a defect instead). An
   * exception thrown by `op` fails the task. The fiber can be interrupted while
   * it waits for the handler.
   */
  def asyncWithCompletionHandler[T](op: CompletionHandler[T, Any] => Any)(implicit trace: Trace): Task[T] =
    javaz.asyncWithCompletionHandler(op)

  /**
   * Alias for `fromCompletionStage` that takes a `CompletableFuture`.
   */
  def fromCompletableFuture[A](cs: => CompletableFuture[A])(implicit trace: Trace): Task[A] =
    fromCompletionStage(cs)

  /**
   * Imports a `CompletionStage` into a `Task`. The `cs` thunk is evaluated each
   * time the effect runs, and an exception that it throws fails the task. When
   * the stage completes with a failure, the task fails with the exception,
   * unwrapped from a `CompletionException` or `ExecutionException`; a cancelled
   * stage interrupts the fiber. Interrupting the fiber while it waits cancels
   * the `CompletableFuture` of the stage.
   */
  def fromCompletionStage[A](cs: => CompletionStage[A])(implicit trace: Trace): Task[A] =
    javaz.fromCompletionStage(cs)

}
