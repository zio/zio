/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import _root_.java.util.concurrent.{CompletionStage, Future}
import zio.interop.javaz
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait FiberPlatformSpecific {

  def fromCompletionStage[A](thunk: => CompletionStage[A]): Fiber[Throwable, A] = {
    lazy val cs: CompletionStage[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      override def await(implicit trace: Trace): UIO[Exit[Throwable, A]] = ZIO.fromCompletionStage(cs).exit

      def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] = ZIO.succeedNow(Chunk.empty)

      override def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.suspendSucceed {
          val cf = cs.toCompletableFuture
          if (cf.isDone) {
            ZIO
              .isFatalWith(isFatal => javaz.unwrapDone(isFatal)(cf))
              .fold(Exit.fail, Exit.succeed)
              .map(Some(_))
          } else {
            ZIO.succeedNow(None)
          }
        }

      def id: FiberId = FiberId.None

      final def interruptAs(id: FiberId)(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO.succeed(cs.toCompletableFuture.cancel(false)) *> join.fold(Exit.fail, Exit.succeed)

      final def inheritRefs(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }

  /**
   * WARNING: this uses the blocking Future#get, consider using
   * `fromCompletionStage`
   */
  def fromFutureJava[A](thunk: => Future[A]): Fiber[Throwable, A] = {
    lazy val ftr: Future[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      def await(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO.fromFutureJava(ftr).exit

      def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
        ZIO.succeedNow(Chunk.empty)

      def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.suspendSucceed {
          if (ftr.isDone) {
            ZIO
              .isFatalWith(isFatal => javaz.unwrapDone(isFatal)(ftr))
              .fold(Exit.fail, Exit.succeed)
              .map(Some(_))
          } else {
            ZIO.none
          }
        }

      def id: FiberId = FiberId.None

      def interruptAs(id: FiberId)(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO.succeed(ftr.cancel(false)) *> join.fold(Exit.fail, Exit.succeed)

      def inheritRefs(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }
}
