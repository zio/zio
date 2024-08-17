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

import _root_.java.util.concurrent.Future
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait FiberPlatformSpecific {

  def fromFutureJava[A](thunk: => Future[A]): Fiber[Throwable, A] = {
    lazy val ftr: Future[A] = thunk

    new Fiber.Synthetic.Internal[Throwable, A] {
      def await(implicit trace: Trace): UIO[Exit[Throwable, A]] =
        ZIO
          .attempt(ftr.get())
          .foldCauseZIO(
            cause => ZIO.succeed(Exit.failCause(cause)),
            value => ZIO.succeed(Exit.succeed(value))
          )

      def children(implicit trace: Trace): UIO[Chunk[Fiber.Runtime[_, _]]] =
        ZIO.succeed(Chunk.empty)

      def poll(implicit trace: Trace): UIO[Option[Exit[Throwable, A]]] =
        ZIO.suspendSucceed {
          if (ftr.isDone) {
            ZIO
              .attempt(ftr.get())
              .fold(
                throwable => Some(Exit.fail(throwable)),
                value => Some(Exit.succeed(value))
              )
          } else {
            ZIO.none
          }
        }

      def id: FiberId = FiberId.None

      def interruptAsFork(id: FiberId)(implicit trace: Trace): UIO[Unit] =
        ZIO.succeed(ftr.cancel(false)).unit

      def inheritAll(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }
}
