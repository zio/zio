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

import _root_.java.util.concurrent.{CompletionStage, Future}

private[zio] trait FiberPlatformSpecific {

  /**
   * Returns a `Fiber` that is backed by the specified `CompletionStage`. The
   * stage is created the first time the fiber is used. Interrupting the fiber
   * cancels the stage's `CompletableFuture` with `cancel(false)`. Only on the
   * JVM and Scala Native.
   */
  def fromCompletionStage[A](thunk: => CompletionStage[A]): Fiber[Throwable, A] = {
    lazy val cs: CompletionStage[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      override def await(implicit trace: Trace): UIO[Exit[Throwable, A]] = ZIO.fromCompletionStage(cs).exit

      def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeed(Chunk.empty)

      override def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.suspendSucceed {
          val cf = cs.toCompletableFuture
          if (cf.isDone) {
            javaz.unwrapDone(cf).fold(e => Some(Exit.fail(e)), v => Some(Exit.succeed(v)))
          } else {
            Exit.none
          }
        }

      def id: FiberId = FiberId.None

      final def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO.succeed(cs.toCompletableFuture.cancel(false): Unit)

      final def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }

  /**
   * Returns a `Fiber` that is backed by the specified Java `Future`. The future
   * is created the first time the fiber is used. Interrupting the fiber cancels
   * the future with `cancel(false)`. Only on the JVM and Scala Native.
   *
   * WARNING: unless the future is already done or is also a `CompletionStage`,
   * awaiting the fiber calls the blocking `Future#get` on the blocking thread
   * pool; consider using `fromCompletionStage`.
   */
  def fromFutureJava[A](thunk: => Future[A]): Fiber[Throwable, A] = {
    lazy val ftr: Future[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      def await(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO.fromFutureJava(ftr).exit

      def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
        ZIO.succeed(Chunk.empty)

      def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.suspendSucceed {
          if (ftr.isDone) {
            javaz.unwrapDone(ftr).fold(e => Some(Exit.fail(e)), v => Some(Exit.succeed(v)))
          } else {
            Exit.none
          }
        }

      def id: FiberId = FiberId.None

      def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO.succeed(ftr.cancel(false): Unit)

      def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }
}
