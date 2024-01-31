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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stm.TSemaphore

import scala.annotation.tailrec
import scala.collection.immutable.{Queue => ScalaQueue}

/**
 * An asynchronous semaphore, which is a generalization of a mutex. Semaphores
 * have a certain number of permits, which can be held and released concurrently
 * by different parties. Attempts to acquire more permits than available result
 * in the acquiring fiber being suspended until the specified number of permits
 * become available.
 *
 * If you need functionality that `Semaphore` doesnt' provide, use a
 * [[TSemaphore]] and define it in a [[zio.stm.ZSTM]] transaction.
 */
sealed trait Semaphore extends Serializable {

  /**
   * Returns the number of available permits.
   */
  def available(implicit trace: Trace): UIO[Long]

  /**
   * Executes the specified workflow, acquiring a permit immediately before the
   * workflow begins execution and releasing it immediately after the workflow
   * completes execution, whether by success, failure, or interruption.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring a permit as the
   * `acquire` action and releasing it as the `release` action.
   */
  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit]

  /**
   * Executes the specified workflow, acquiring the specified number of permits
   * immediately before the workflow begins execution and releasing them
   * immediately after the workflow completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Returns a scoped workflow that describes acquiring the specified number of
   * permits and releasing them when the scope is closed.
   */
  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit]
}

object Semaphore {

  /**
   * Creates a new `Semaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Semaphore {
        val ref = Ref.unsafe.make[SemaphoreState](Available(permits))

        def available(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case Exhausted(_)                => 0L
            case Available(availablePermits) => availablePermits
          }

        def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          withPermits(1L)(zio)

        def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          withPermitsScoped(1L)

        def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          ZIO.acquireReleaseWith(reservePermits(n))(_.releasePermits)(_.awaitPermits *> zio)

        def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          ZIO.acquireRelease(reservePermits(n))(_.releasePermits).flatMap(_.awaitPermits)

        def reservePermits(n: Long)(implicit trace: Trace): UIO[Reservation] =
          if (n < 0)
            ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L)
            ZIO.succeedNow(Reservation(ZIO.unit, ZIO.unit))
          else
            Promise.make[Nothing, Unit].flatMap { promise =>
              ref.modify {
                case Available(availablePermits) if availablePermits >= n =>
                  Reservation(ZIO.unit, releasePermits(n)) -> Available(availablePermits - n)
                case Available(availablePermits) =>
                  Reservation(promise.await, restorePermits(promise, n)) -> Exhausted(promise, n - availablePermits)
                case e @ Exhausted(_) =>
                  Reservation(promise.await, restorePermits(promise, n)) -> e.enqueue(promise, n)
              }
            }

        def restorePermits(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
          ref.modify {
            case e @ Exhausted(blocked) =>
              blocked
                .find(_.promise eq promise)
                .fold(releasePermits(n) -> e) { case Blocked(_, neededPermits) =>
                  releasePermits(n - neededPermits) -> Exhausted(blocked.filter(_.promise ne promise))
                }
            case Available(availablePermits) =>
              ZIO.unit -> Available(availablePermits + n)
          }.flatten

        def releasePermits(releasedPermits: Long)(implicit trace: Trace): UIO[Any] = {
          @tailrec
          def loop(releasedPermits: Long, state: SemaphoreState, acc: UIO[Any]): (UIO[Any], SemaphoreState) =
            state match {
              case Available(availablePermits) =>
                acc -> Available(availablePermits + releasedPermits)
              case Exhausted(blocked) =>
                blocked.dequeueOption match {
                  case None =>
                    acc -> Available(releasedPermits)
                  case Some((Blocked(promise, neededPermits), remainingBlocked)) =>
                    if (releasedPermits > neededPermits)
                      loop(releasedPermits - neededPermits, Exhausted(remainingBlocked), acc *> promise.succeed(()))
                    else if (releasedPermits == neededPermits)
                      (acc *> promise.succeed(())) -> Exhausted(remainingBlocked)
                    else
                      acc -> Exhausted(Blocked(promise, neededPermits - releasedPermits) +: remainingBlocked)
                }
            }

          ref.modify(loop(releasedPermits, _, ZIO.unit)).flatten
        }
      }

    private trait SemaphoreState
    private final case class Available(availablePermits: Long) extends SemaphoreState
    private final case class Exhausted(blocked: ScalaQueue[Blocked]) extends SemaphoreState {
      def enqueue(promise: Promise[Nothing, Unit], neededPermits: Long): SemaphoreState =
        Exhausted(blocked.enqueue(Blocked(promise, neededPermits)))
    }
    private object Exhausted {
      def apply(promise: Promise[Nothing, Unit], neededPermits: Long): Exhausted =
        Exhausted(ScalaQueue(Blocked(promise, neededPermits)))
    }
    private final case class Blocked(promise: Promise[Nothing, Unit], neededPermits: Long)
    private final case class Reservation(awaitPermits: UIO[Unit], releasePermits: UIO[Any])
  }
}
