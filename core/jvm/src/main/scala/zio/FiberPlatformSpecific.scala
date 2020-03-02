/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import _root_.java.util.concurrent.{ CompletionStage, Future }

import zio.blocking.Blocking
import zio.interop.javaz

private[zio] trait FiberPlatformSpecific {

  def fromCompletionStage[A](thunk: => CompletionStage[A]): Fiber[Throwable, A] = {
    lazy val cs: CompletionStage[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      override def await: UIO[Exit[Throwable, A]] = ZIO.fromCompletionStage(cs).run

      override def poll: UIO[Option[Exit[Throwable, A]]] =
        UIO.effectSuspendTotal {
          val cf = cs.toCompletableFuture
          if (cf.isDone) {
            Task
              .effectSuspendWith((p, _) => javaz.unwrapDone(p.fatal)(cf))
              .fold(Exit.fail, Exit.succeed)
              .map(Some(_))
          } else {
            UIO.succeedNow(None)
          }
        }

      final def children: UIO[Iterable[Fiber[Any, Any]]] = UIO(Nil)

      final def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

      final def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

      final def inheritRefs: UIO[Unit] = IO.unit
    }
  }

  /** WARNING: this uses the blocking Future#get, consider using `fromCompletionStage` */
  def fromFutureJava[A](thunk: => Future[A]): Fiber[Throwable, A] = {
    lazy val ftr: Future[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      def await: UIO[Exit[Throwable, A]] =
        Blocking.live.build.use(ZIO.fromFutureJava(ftr).provide(_).run)

      def poll: UIO[Option[Exit[Throwable, A]]] =
        UIO.effectSuspendTotal {
          if (ftr.isDone) {
            Task
              .effectSuspendWith((p, _) => javaz.unwrapDone(p.fatal)(ftr))
              .fold(Exit.fail, Exit.succeed)
              .map(Some(_))
          } else {
            UIO.succeed(None)
          }
        }

      def children: UIO[Iterable[Fiber[Any, Any]]] = UIO(Nil)

      def getRef[A](ref: FiberRef[A]): UIO[A] = UIO(ref.initial)

      def interruptAs(id: Fiber.Id): UIO[Exit[Throwable, A]] = join.fold(Exit.fail, Exit.succeed)

      def inheritRefs: UIO[Unit] = UIO.unit
    }
  }
}
