package zio

/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

trait ZPool[+Error, Item] {

  /**
   * Retrieves an item from the pool in a `Managed` effect. Note that if acquisition fails, then
   * the returned effect will fail for that same reason. Retrying a failed acquisition attempt
   * will repeat the acquisition attempt.
   */
  def get: ZManaged[Any, Error, Item]

  /**
   * Invalidates the specified item. This will cause the pool to eventually reallocate the item,
   * although this reallocation may occur lazily rather than eagerly.
   */
  def invalidate(item: Item): UIO[Unit]
}
object ZPool {

  /**
   * Creates a pool from a fixed number of pre-allocated items. This method
   * should only be used when there is no cleanup or release operation
   * associated with items in the pool. If cleanup or release is required,
   * then the `make` constructor should be used instead.
   */
  def fromIterable[A](iterable: => Iterable[A]): UManaged[ZPool[Nothing, A]] =
    for {
      source <- Ref.make(iterable.toList).toManaged
      get = source.modify { case head :: tail =>
              (head, tail)
            }
      pool <- ZPool.make(ZManaged.fromZIO(get), iterable.size to iterable.size)
    } yield pool

  def make[E, A](get: ZManaged[Any, E, A], min: Int): UManaged[ZPool[E, A]] =
    make(get, min to min)

  /**
   * {{{
   * for {
   *   pool <- ZPool.make(acquireDbConnection, 10 to 20)
   *   _    <- pool.get.use { connection => useConnection(connection) }
   * } yield ()
   * }}}
   */
  def make[E, A](get: ZManaged[Any, E, A], range: Range): UManaged[ZPool[E, A]] =
    for {
      down  <- Ref.make(false).toManaged
      size  <- Ref.make(0).toManaged
      free  <- Queue.bounded[Attempted[E, A]](range.start).toManaged
      alloc <- Ref.make(0).toManaged
      inv   <- Ref.make(Set.empty[A]).toManaged
      pool   = DefaultPool(get, range, down, size, free, alloc, inv)
      fiber <- pool.initialize.forkDaemon.toManaged
      _     <- ZManaged.finalizer(fiber.interrupt *> pool.shutdown)
    } yield pool

  private case class Attempted[+E, +A](result: Exit[E, A], finalizer: UIO[Any]) {
    def isFailure: Boolean = result.isFailure

    def forEach[R, E2](f: A => ZIO[R, E2, Any]): ZIO[R, E2, Any] =
      result match {
        case Exit.Failure(_) => ZIO.unit
        case Exit.Success(a) => f(a)
      }

    lazy val toManaged: ZManaged[Any, E, A] = ZIO.done(result).toManaged
  }

  private case class DefaultPool[E, A](
    creator: ZManaged[Any, E, A],
    range: Range,
    isShuttingDown: Ref[Boolean],
    size: Ref[Int],
    free: Queue[Attempted[E, A]],
    allocating: Ref[Int],
    invalidated: Ref[Set[A]]
  ) extends ZPool[E, A] {

    /**
     * Triggers a single allocation in the background. Updates the data
     * structures to ensure consistency.
     */
    private def allocate: UIO[Any] =
      ZIO.unlessZIO(isShuttingDown.get) {
        ZIO.uninterruptibleMask { restore =>
          (for {
            _           <- allocating.update(_ + 1)
            reservation <- creator.reserve
            exit        <- restore(reservation.acquire).exit
            attempted   <- ZIO.succeed(Attempted(exit, reservation.release(Exit.succeed(()))))
            _           <- free.offer(attempted) *> size.update(_ + 1)
          } yield attempted).ensuring(allocating.update(_ - 1))
        }
      }

    final def get: ZManaged[Any, E, A] = {

      /**
       * If the attempted item has been invalidated, we have to reallocate and
       * try again. Otherwise, we take the attempted item, whether or not the
       * acquisition attempt was successful.
       */
      def acquire: UIO[Attempted[E, A]] =
        free.take.flatMap { acquired =>
          acquired.result match {
            case Exit.Success(item) =>
              invalidated.get.flatMap { set =>
                if (set.contains(item)) size.update(_ - 1) *> allocate *> acquire
                else ZIO.succeed(acquired)
              }
            case _ => ZIO.succeed(acquired)
          }
        }

      /**
       * If a failure is released, we try to reallocate a new result, rather
       * than putting the failure back into the queue (which would cause a
       * secondary failure). This allows failures to propagate to `get`, but
       * only one time, allowing retry behavior on `get` to behave as expected.
       */
      def release(attempted: Attempted[E, A]): UIO[Any] =
        if (attempted.isFailure) size.update(_ - 1) *> allocate
        else free.offer(attempted)

      ZManaged.acquireReleaseWith(acquire)(release(_)).flatMap(_.toManaged)
    }

    /**
     * Gets an item from the pool and shuts it down, returning `true` if this
     * was successful, or `false` if the pool was empty.
     */
    private def getAndShutdown: UIO[Boolean] =
      size.get.map(_ > 0).tap { more =>
        ZIO.when(more) {
          free.take.flatMap { attempted =>
            attempted.forEach(a => invalidated.update(_ - a)) *>
              size.update(_ - 1) *>
              attempted.finalizer
          }
        }
      }

    /**
     * Begins pre-allocating pool entries based on minimum pool size.
     */
    final def initialize: UIO[Unit] = ZIO.replicateZIODiscard(range.start)(allocate)

    final def invalidate(a: A): UIO[Unit] = invalidated.update(_ + a)

    final def shutdown: UIO[Unit] =
      isShuttingDown.set(true) *>
        getAndShutdown.repeatWhile(_ == true) *>
        free.shutdown *>
        free.awaitShutdown
  }
}
