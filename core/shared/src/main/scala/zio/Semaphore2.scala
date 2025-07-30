/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import org.jctools.queues.MpscUnboundedArrayQueue
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec

sealed trait Semaphore2 extends Serializable {

  def available(implicit trace: Trace): UIO[Long]

  def awaiting(implicit trace: Trace): UIO[Long]

  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit]
}

object Semaphore2 {

  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore2] =
    ZIO.succeed(new Semaphore2 {
      private val permits0 = new AtomicLong(permits)
      private val waiters  = new MpscUnboundedArrayQueue[Promise[Nothing, Unit]](16)

      def available(implicit trace: Trace): UIO[Long] =
        ZIO.succeed(permits0.get())

      def awaiting(implicit trace: Trace): UIO[Long] =
        ZIO.succeed(waiters.size().toLong)

      def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        withPermits(1L)(zio)

      def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
        withPermitsScoped(1L)

      def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        ZIO.acquireReleaseWith(acquireN(n))(_ => releaseN(n))(_ => zio)

      def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
        ZIO.acquireRelease(acquireN(n))(_ => releaseN(n)).unit

      private def acquireN(n: Long)(implicit trace: Trace): UIO[Unit] =
        ZIO.asyncInterrupt[Any, Nothing, Unit] { k =>
          @tailrec
          def loop(): Either[UIO[Unit], Unit] = {
            val current = permits0.get()
            if (current >= n) {
              if (permits0.compareAndSet(current, current - n)) {
                Right(k(ZIO.unit))
              } else {
                loop()
              }
            } else {
              val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)(Unsafe.unsafe)
              waiters.add(promise)
              Left(promise.await.onInterrupt(releaseN(n)).as(k(ZIO.unit)))
            }
          }
          loop() match {
            case Left(zio) => Left(zio)
            case Right(_)  => Right(ZIO.unit)
          }
        }

      private def releaseN(n: Long)(implicit trace: Trace): UIO[Unit] =
        ZIO.succeed {
          permits0.addAndGet(n)
          @tailrec
          def loop(): Unit = {
            val p = waiters.peek()
            if (p != null && permits0.get() > 0) {
              if (permits0.decrementAndGet() >= 0) {
                val promise = waiters.poll()
                if (promise != null) {
                  promise.unsafe.succeed(())(trace, Unsafe.unsafe)
                  loop()
                }
              }
            }
          }
          loop()
        }
    })
}
