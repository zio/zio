/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.concurrent.duration.{Duration => ScalaDuration}
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.Try

/**
 * A `scala.concurrent.Future` that can be cancelled. It wraps the `future` that
 * holds the result, and delegates the `Future` operations to it. ZIO creates
 * one for each effect it runs as a future, for example with
 * `Runtime.unsafe.runToFuture` and `ZIO#toFuture`.
 *
 * {{{
 * import scala.concurrent.Future
 *
 * val running: CancelableFuture[Unit] =
 *   Unsafe.unsafe { implicit unsafe =>
 *     Runtime.default.unsafe.runToFuture(ZIO.sleep(1.hour))
 *   }
 *
 * // completes with a failed `Exit` that holds an interruption
 * val cancelled: Future[Exit[Throwable, Unit]] = running.cancel()
 * }}}
 *
 * @param future
 *   The future that holds the result of the operation.
 */
abstract class CancelableFuture[+A](val future: Future[A]) extends Future[A] with FutureTransformCompat[A] {

  /**
   * Interrupts the operation and returns a [[scala.concurrent.Future]]
   * containing the result. The returned future completes when the operation has
   * finished, after its finalizers have run; when the operation is
   * uninterruptible as a whole, it completes after the operation ends, with an
   * interruption. When the operation has already finished, the returned future
   * holds its exit.
   */
  def cancel(): Future[Exit[Throwable, A]]

  /** Registers `f` to run on `executor` when the wrapped future completes. */
  final def onComplete[U](f: Try[A] => U)(implicit executor: ExecutionContext): Unit =
    future.onComplete(f)(executor)

  /** Returns `true` when the wrapped future has completed. */
  final def isCompleted: Boolean =
    future.isCompleted

  /**
   * Returns `None` while the wrapped future has not completed, and `Some` with
   * its result otherwise.
   */
  final def value: Option[Try[A]] =
    future.value

  /**
   * Blocks until the wrapped future completes, and returns this future. Throws
   * a `java.util.concurrent.TimeoutException` when `atMost` passes first. It
   * needs a `CanAwait` permit, which `Await` provides.
   */
  final def ready(atMost: ScalaDuration)(implicit permit: CanAwait): this.type = {
    future.ready(atMost)(permit)
    this
  }

  /**
   * Blocks until the wrapped future completes, and returns its value, or throws
   * the exception it failed with. Throws a
   * `java.util.concurrent.TimeoutException` when `atMost` passes first. It
   * needs a `CanAwait` permit, which `Await` provides.
   */
  final def result(atMost: ScalaDuration)(implicit permit: CanAwait): A =
    future.result(atMost)
}
