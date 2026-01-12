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

import zio.Semaphore.{SemaphoreState, Job, Stats}
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
   * Returns the number of tasks currently waiting for permits. The default
   * implementation returns 0.
   */
  def awaiting(implicit trace: Trace): UIO[Long] = ZIO.succeed(0L)

  /**
   * Returns the number of available permits and the number of tasks currently
   * waiting for permits.
   */
  def stats(implicit trace: Trace): UIO[Semaphore.Stats]

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

  private[zio] final case class Job(promise: Promise[Nothing, Unit], permits: Long)

  private[zio] sealed trait SemaphoreState
  private[zio] object SemaphoreState {
    final case class JobQueue(queue: ScalaQueue[Job]) extends SemaphoreState {
      @inline def dequeueOption: Option[(Job, ScalaQueue[Job])] = queue.dequeueOption
      @inline def enqueue(req: Job): JobQueue                   = JobQueue(queue.enqueue(req))
      @inline def size: Int                                     = queue.size
    }
    final case class FreePermits(permits: Long) extends SemaphoreState {
      @inline def -(n: Long): FreePermits = FreePermits(permits - n)
      @inline def +(n: Long): FreePermits = FreePermits(permits + n)
      @inline def >=(n: Long): Boolean    = permits >= n
    }

    object JobQueue {
      def apply(list: List[Job]): JobQueue = JobQueue(ScalaQueue.from(list))
      def apply(elem: Job): JobQueue       = JobQueue(ScalaQueue(elem))
    }
  }

  /**
   * Creates a new `Semaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore = new SemaphoreLive(permits)
  }

  final case class Stats(available: Long, awaiting: Long)
}

private[zio] final class SemaphoreLive(permits: Long)(implicit unsafe: Unsafe) extends Semaphore {
  private val ref: Ref.Atomic[SemaphoreState] =
    Ref.unsafe.make[SemaphoreState](SemaphoreState.FreePermits(permits))

  def available(implicit trace: Trace): UIO[Long] =
    ref.get.map {
      case p: SemaphoreState.FreePermits => p.permits
      case _                             => 0L
    }

  override def awaiting(implicit trace: Trace): UIO[Long] =
    ref.get.map {
      case q: SemaphoreState.JobQueue => q.queue.size.toLong
      case _                          => 0L
    }

  override def stats(implicit trace: Trace): UIO[Stats] =
    ref.get.map {
      case p: SemaphoreState.FreePermits => Stats(available = p.permits, awaiting = 0L)
      case q: SemaphoreState.JobQueue    => Stats(available = 0L, awaiting = q.queue.size.toLong)
    }

  override def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    withPermits(1L)(zio)

  override def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    withPermitsScoped(1L)

  override def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(reserve(n))(_.release)(_.acquire *> zio)

  override def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(reserve(n))(_.release).flatMap(_.acquire)

  override def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.acquireReleaseWith(tryReserve(n)) {
      case Some(reservation) => reservation.release
      case _                 => Exit.unit
    } {
      case _: Some[?] => zio.asSome
      case _          => Exit.none
    }

  final case class Reservation(acquire: UIO[Unit], release: UIO[Any])
  object Reservation {
    private[zio] val zero = Reservation(acquire = ZIO.unit, release = ZIO.unit)
  }

  private def tryReserve(n: Long)(implicit trace: Trace): UIO[Option[Reservation]] =
    if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
    else if (n == 0L) ZIO.succeed(Some(Reservation.zero))
    else
      ref.modify {
        case permits: SemaphoreState.FreePermits if permits >= n =>
          val reservation = Reservation(acquire = ZIO.unit, release = releaseN(n))
          val newEntry    = permits - n

          Some(reservation) -> newEntry
        case other => None -> other
      }

  private def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] =
    if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
    else if (n == 0L) ZIO.succeed(Reservation.zero)
    else
      ZIO.fiberIdWith { fiberId =>
        Exit.succeed {
          val promise = Promise.unsafe.make[Nothing, Unit](fiberId)

          ref.unsafe.modify {
            case permits: SemaphoreState.FreePermits if permits >= n =>
              val reservation = Reservation(acquire = ZIO.unit, release = releaseN(n))
              val newEntry    = permits - n

              reservation -> newEntry
            case SemaphoreState.FreePermits(permits) =>
              val reservation = Reservation(acquire = promise.await, release = restore(promise, n))
              val newEntry    = SemaphoreState.JobQueue(Job(promise = promise, permits = n - permits))

              reservation -> newEntry
            case queue: SemaphoreState.JobQueue =>
              val reservation = Reservation(acquire = promise.await, release = restore(promise, n))
              val newEntry    = queue.enqueue(Job(promise = promise, permits = n))

              reservation -> newEntry
          }
        }
      }

  private def restore(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
    ZIO.suspendSucceed {
      ref.unsafe.modify {
        case permits: SemaphoreState.FreePermits => Exit.unit -> (permits + n)
        case queueEntry @ SemaphoreState.JobQueue(queue) =>
          val iterator = queue.iterator
          val others   = List.newBuilder[Job]
          others.sizeHint(queue.size - 1)
          var found = false
          while (iterator.hasNext) {
            val next    = iterator.next()
            val similar = next.promise == promise
            found = found || similar
            if (!similar) others += next
          }

          if (found)
            releaseN(n - permits) -> SemaphoreState.JobQueue(others.result())
          else
            releaseN(n) -> queueEntry
      }
    }

  private def releaseN(n: Long)(implicit trace: Trace): UIO[Any] = {

    @tailrec
    def loop(
      n0: Long,
      state: SemaphoreState,
      acc: UIO[Any]
    ): (UIO[Any], SemaphoreState) =
      state match {
        case permits: SemaphoreState.FreePermits => acc -> (permits + n0)
        case SemaphoreState.JobQueue(queue) =>
          queue.dequeueOption match {
            case None => acc -> SemaphoreState.FreePermits(n0)
            case Some((releaseRequest, queue0)) =>
              val rest = n0 - permits
              if (rest > 0L) {
                val newState = SemaphoreState.JobQueue(queue0)
                val newAcc   = acc *> releaseRequest.promise.succeedUnit

                loop(rest, newState, newAcc)
              } else if (n0 == permits)
                (acc *> releaseRequest.promise.succeedUnit) -> SemaphoreState.JobQueue(queue0)
              else {
                val newQueue = Job(promise = releaseRequest.promise, permits = permits - n0) +: queue0

                acc -> SemaphoreState.JobQueue(newQueue)
              }
          }
      }

    ZIO.suspendSucceed(ref.unsafe.modify(loop(n, _, ZIO.unit)))
  }
}
