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

import java.util.concurrent.atomic.AtomicReference
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
   * Returns the number of tasks currently waiting for permits. The default
   * implementation returns 0.
   */
  def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(0L)

  /**
   * Executes the effect, acquiring a permit if available and releasing it after
   * execution. Returns `None` if no permits were available.
   */
  final def tryWithPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    tryWithPermits(1L)(zio)

  /**
   * Executes the effect, acquiring `n` permits if available and releasing them
   * after execution. Returns `None` if no permits were available.
   */
  def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.none

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

  private type WaitQueue = ScalaQueue[(Promise[Nothing, Unit], Long)]
  private type State     = Either[WaitQueue, Long]

  private val RightZero: State = Right(0L)
  private val RightOne: State  = Right(1L)

  @inline private def rightPermits(n: Long): State =
    if (n == 0L) RightZero
    else if (n == 1L) RightOne
    else Right(n)

  /**
   * Creates a new `Semaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Semaphore {
        private val state = new AtomicReference[State](rightPermits(permits))

        private val releaseOne: UIO[Unit] =
          ZIO.succeed(releaseNSync(1L))(Trace.empty)

        def available(implicit trace: Trace): UIO[Long] =
          ZIO.succeed {
            state.get() match {
              case Left(_)        => 0L
              case Right(permits) => permits
            }
          }

        override def awaiting(implicit trace: Trace): UIO[Long] =
          ZIO.succeed {
            state.get() match {
              case Left(queue) => queue.size.toLong
              case Right(_)    => 0L
            }
          }

        def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          withPermits(1L)(zio)

        def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          withPermitsScoped(1L)

        def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
          if (n < 0L)
            ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L)
            zio
          else
            ZIO.uninterruptibleMask { restore =>
              if (tryAcquireSync(n))
                restore(zio).ensuring(releaseN(n))
              else {
                val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)(Unsafe.unsafe)
                if (acquireOrEnqueue(n, promise))
                  restore(zio).ensuring(releaseN(n))
                else
                  restore(promise.await).onError(_ => restore(promise, n)) *>
                    restore(zio).ensuring(releaseN(n))
              }
            }

        def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
          ZIO.acquireRelease(reserve(n))(_.release).flatMap(_.acquire)

        override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
          if (n < 0L)
            ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L)
            zio.asSome
          else
            ZIO.uninterruptibleMask { restore =>
              if (tryAcquireSync(n))
                restore(zio).asSome.ensuring(releaseN(n))
              else
                Exit.none
            }

        case class Reservation(acquire: UIO[Unit], release: UIO[Any])
        object Reservation {
          private[zio] val zero = Reservation(Exit.unit, Exit.unit)
        }

        @tailrec
        private def tryAcquireSync(n: Long): Boolean =
          state.get() match {
            case current @ Right(permits) if permits >= n =>
              if (state.compareAndSet(current, rightPermits(permits - n))) true
              else tryAcquireSync(n)
            case _ =>
              false
          }

        @tailrec
        private def acquireOrEnqueue(n: Long, promise: Promise[Nothing, Unit]): Boolean =
          state.get() match {
            case current @ Right(permits) =>
              if (permits >= n) {
                if (state.compareAndSet(current, rightPermits(permits - n))) true
                else acquireOrEnqueue(n, promise)
              } else {
                if (state.compareAndSet(current, Left(ScalaQueue(promise -> (n - permits))))) false
                else acquireOrEnqueue(n, promise)
              }
            case current @ Left(queue) =>
              if (state.compareAndSet(current, Left(queue.enqueue(promise -> n)))) false
              else acquireOrEnqueue(n, promise)
          }

        def tryReserve(n: Long)(implicit trace: Trace): UIO[Option[Reservation]] =
          if (n < 0L) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L) ZIO.succeed(Some(Reservation.zero))
          else
            ZIO.succeed {
              if (tryAcquireSync(n)) Some(Reservation(Exit.unit, releaseN(n)))
              else None
            }

        def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] =
          if (n < 0L)
            ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L)
            ZIO.succeed(Reservation.zero)
          else
            ZIO.succeed {
              if (tryAcquireSync(n))
                Reservation(Exit.unit, releaseN(n))
              else {
                val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)(Unsafe.unsafe)
                if (acquireOrEnqueue(n, promise))
                  Reservation(Exit.unit, releaseN(n))
                else
                  Reservation(promise.await, restore(promise, n))
              }
            }

        def restore(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
          ZIO.succeed(restoreSync(promise, n))

        @tailrec
        private def restoreSync(promise: Promise[Nothing, Unit], n: Long): Unit =
          state.get() match {
            case current @ Right(permits) =>
              if (!state.compareAndSet(current, rightPermits(permits + n)))
                restoreSync(promise, n)
            case current @ Left(queue) =>
              queue.find(_._1 eq promise) match {
                case Some((_, permits)) =>
                  val filtered  = queue.filterNot(_._1 eq promise)
                  val toRelease = n - permits
                  val (toComplete, nextState) =
                    releaseLoop(toRelease, if (filtered.isEmpty) RightZero else Left(filtered), Nil)
                  if (state.compareAndSet(current, nextState))
                    completeAll(toComplete)
                  else
                    restoreSync(promise, n)
                case None =>
                  val (toComplete, nextState) = releaseLoop(n, current, Nil)
                  if (state.compareAndSet(current, nextState))
                    completeAll(toComplete)
                  else
                    restoreSync(promise, n)
              }
          }

        def releaseN(n: Long)(implicit trace: Trace): UIO[Any] =
          if (n == 1L) releaseOne
          else if (n <= 0L) Exit.unit
          else ZIO.succeed(releaseNSync(n))

        @tailrec
        private def releaseNSync(n: Long): Unit =
          if (n > 0L) {
            state.get() match {
              case current @ Right(permits) =>
                if (!state.compareAndSet(current, rightPermits(permits + n)))
                  releaseNSync(n)
              case current @ Left(_) =>
                val (toComplete, nextState) = releaseLoop(n, current, Nil)
                if (state.compareAndSet(current, nextState))
                  completeAll(toComplete)
                else
                  releaseNSync(n)
            }
          }

        @tailrec
        private def releaseLoop(
          n: Long,
          currentState: State,
          acc: List[Promise[Nothing, Unit]]
        ): (List[Promise[Nothing, Unit]], State) =
          currentState match {
            case Right(permits) =>
              (acc, rightPermits(permits + n))
            case Left(queue) =>
              queue.dequeueOption match {
                case None =>
                  (acc, rightPermits(n))
                case Some(((promise, permits), tail)) =>
                  if (n > permits)
                    releaseLoop(n - permits, Left(tail), promise :: acc)
                  else if (n == permits)
                    (promise :: acc, if (tail.isEmpty) RightZero else Left(tail))
                  else
                    (acc, Left((promise -> (permits - n)) +: tail))
              }
          }

        private def completeAll(promises: List[Promise[Nothing, Unit]]): Unit =
          promises match {
            case Nil => ()
            case p :: Nil =>
              p.unsafe.done(Exit.unit)(Unsafe.unsafe)
            case _ =>
              promises.reverse.foreach(_.unsafe.done(Exit.unit)(Unsafe.unsafe))
          }
      }
  }
}
