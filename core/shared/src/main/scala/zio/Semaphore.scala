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

    /**
     * A FIFO queue of waiting jobs with O(1) lookup and removal by promise.
     *
     * Uses a dual data structure:
     *   - `jobs`: Map for O(1) lookup/removal by promise identity
     *   - `order`: Vector maintaining FIFO insertion order
     *
     * When a job is cancelled (via `remove`), it's removed from `jobs` but
     * remains in `order` as a "tombstone". Tombstones are cleaned lazily during
     * `dequeueOrNull`.
     *
     * Complexity (where n = number of jobs, t = number of tombstones):
     *   - enqueue: O(1) effectively constant (eC)
     *   - prepend: O(1) effectively constant (eC)
     *   - remove: O(1) effectively constant (eC)
     *   - dequeueOrNull: O(1) amortized (skips t tombstones, each cleaned
     *     exactly once)
     *   - size: O(1)
     */
    final case class JobQueue(
      jobs: Map[Promise[Nothing, Unit], Job],
      order: Vector[Promise[Nothing, Unit]]
    ) extends SemaphoreState {

      /** O(1) eC - appends to both map and order vector */
      def enqueue(job: Job): JobQueue =
        JobQueue(jobs.updated(job.promise, job), order :+ job.promise)

      /** O(1) eC - prepends to both map and order vector */
      def prepend(job: Job): JobQueue =
        JobQueue(jobs.updated(job.promise, job), job.promise +: order)

      /**
       * O(1) eC - removes from map only; order cleaned lazily during dequeue
       */
      def remove(promise: Promise[Nothing, Unit]): (Job, JobQueue) = {
        val job = jobs.getOrElse(promise, null)
        if (job eq null) (null, this) // avoid Map and JobQueue allocation
        else (job, JobQueue(jobs - promise, order))
      }

      /**
       * O(1) amortized - skips tombstones (promises removed from map but still
       * in order)
       */
      def dequeueOrNull: (Job, JobQueue) = {
        @tailrec
        def loop(order0: Vector[Promise[Nothing, Unit]]): (Job, JobQueue) =
          if (order0.isEmpty) null
          else {
            val head = order0.head
            val tail = order0.tail
            val job  = jobs.getOrElse(head, null)
            if (job ne null) (job, JobQueue(jobs - head, tail))
            else loop(tail) // skip tombstone, no JobQueue allocation
          }
        loop(order)
      }

      /** O(1) - returns count of active jobs (excludes tombstones) */
      def size: Int = jobs.size
    }
    final case class FreePermits(permits: Long) extends SemaphoreState {
      def -(n: Long): FreePermits = FreePermits(permits - n)
      def +(n: Long): FreePermits = FreePermits(permits + n)
      def >=(n: Long): Boolean    = permits >= n
    }

    object JobQueue {
      def apply(list: List[Job]): JobQueue =
        JobQueue(
          list.iterator.map(j => j.promise -> j).toMap,
          list.iterator.map(_.promise).toVector
        )

      def apply(elem: Job): JobQueue =
        JobQueue(Map(elem.promise -> elem), Vector(elem.promise))
    }
  }

  /**
   * Creates a new `Semaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): UIO[Semaphore] =
    ZIO.succeed(unsafe.make(permits)(Unsafe.unsafe))

  object unsafe {
    def make(permits: Long)(implicit unsafe: Unsafe): Semaphore =
      new Semaphore {
        private val ref: Ref.Atomic[SemaphoreState] =
          Ref.unsafe.make[SemaphoreState](SemaphoreState.FreePermits(permits))

        override def available(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case p: SemaphoreState.FreePermits => p.permits
            case _                             => 0L
          }

        override def awaiting(implicit trace: Trace): UIO[Long] =
          ref.get.map {
            case queue: SemaphoreState.JobQueue => queue.size.toLong
            case _                              => 0L
          }

        override def stats(implicit trace: Trace): UIO[Stats] =
          ref.get.map {
            case p: SemaphoreState.FreePermits  => Stats(available = p.permits, awaiting = 0L)
            case queue: SemaphoreState.JobQueue => Stats(available = 0L, awaiting = queue.size.toLong)
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

        private final case class Reservation(acquire: UIO[Unit], release: UIO[Any])
        private object Reservation {
          val zero = Reservation(acquire = Exit.unit, release = Exit.unit)
        }

        private def tryReserve(n: Long)(implicit trace: Trace): UIO[Option[Reservation]] =
          if (n < 0) ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L) Exit.succeed(Some(Reservation.zero))
          else
            ref.modify {
              case permits: SemaphoreState.FreePermits if permits >= n =>
                val reservation = Reservation(acquire = Exit.unit, release = releaseN(n))
                val newEntry    = permits - n

                Some(reservation) -> newEntry
              case other => None -> other
            }

        private def reserve(n: Long)(implicit trace: Trace): UIO[Reservation] =
          if (n < 0)
            ZIO.die(new IllegalArgumentException(s"Unexpected negative `$n` permits requested."))
          else if (n == 0L)
            Exit.succeed(Reservation.zero)
          else
            ZIO.fiberIdWith { fiberId =>
              Exit.succeed {
                // Lazy promise creation: only allocated on slow path, reused across retries
                var cachedPromise: Promise[Nothing, Unit] = null

                def getOrCreatePromise(): Promise[Nothing, Unit] = {
                  if (cachedPromise eq null) cachedPromise = Promise.unsafe.make[Nothing, Unit](fiberId)
                  cachedPromise
                }

                ref.unsafe.modify {
                  case permits: SemaphoreState.FreePermits if permits >= n =>
                    val reservation = Reservation(acquire = Exit.unit, release = releaseN(n))
                    val newEntry    = permits - n

                    reservation -> newEntry
                  case SemaphoreState.FreePermits(permits) =>
                    val promise     = getOrCreatePromise()
                    val reservation = Reservation(acquire = promise.await, release = restore(promise, n))
                    val newEntry    = SemaphoreState.JobQueue(Job(promise = promise, permits = n - permits))

                    reservation -> newEntry
                  case queue: SemaphoreState.JobQueue =>
                    val promise     = getOrCreatePromise()
                    val reservation = Reservation(acquire = promise.await, release = restore(promise, n))
                    val newEntry    = queue.enqueue(Job(promise = promise, permits = n))

                    reservation -> newEntry
                }
              }
            }

        /**
         * Called when a fiber is interrupted before acquiring permits. Removes
         * the job from the queue and releases any partial permits.
         *
         * Complexity: O(1) eC for the remove operation (previously O(n) with
         * Queue)
         */
        private def restore(promise: Promise[Nothing, Unit], n: Long)(implicit trace: Trace): UIO[Any] =
          ZIO.suspendSucceed {
            ref.unsafe.modify {
              case permits: SemaphoreState.FreePermits => Exit.unit -> (permits + n)
              case queue: SemaphoreState.JobQueue =>
                val (foundJob, newQueue) = queue.remove(promise)
                if (foundJob ne null)
                  releaseN(n - foundJob.permits) -> newQueue
                else
                  releaseN(n) -> queue
            }
          }

        /**
         * Releases n permits, waking up waiting fibers in FIFO order.
         *
         * Complexity: O(k) amortized where k = number of fibers that can be
         * woken with n permits. Each dequeue is O(1) amortized.
         */
        private def releaseN(n: Long)(implicit trace: Trace): UIO[Any] =
          if (n <= 0L) Exit.unit
          else {

            @tailrec
            def loop(
              n0: Long,
              state: SemaphoreState,
              acc: ChunkBuilder[Promise[Nothing, Unit]]
            ): (ChunkBuilder[Promise[Nothing, Unit]], SemaphoreState) =
              state match {
                case permits: SemaphoreState.FreePermits => acc -> (permits + n0)
                case queue: SemaphoreState.JobQueue =>
                  queue.dequeueOrNull match {
                    case null => acc -> SemaphoreState.FreePermits(n0)
                    case (job, queue0) =>
                      val promise   = job.promise
                      val permits   = job.permits
                      val available = n0 - permits
                      if (available > 0L) {
                        acc += promise
                        loop(available, queue0, acc)
                      } else if (available == 0L) {
                        acc += promise
                        acc -> queue0
                      } else {
                        val newQueue = queue0.prepend(Job(promise = promise, permits = permits - n0))
                        acc -> newQueue
                      }
                  }
              }

            ZIO.suspendSucceed {
              val promises = ref.unsafe.modify(loop(n, _, ChunkBuilder.make[Promise[Nothing, Unit]]())).result()
              promises.size match {
                case 0 => Exit.unit
                case 1 => promises(0).succeedUnit
                case _ => ZIO.foreachDiscard(promises)(_.succeedUnit)
              }
            }
          }
      }
  }

  final case class Stats(available: Long, awaiting: Long)
}
