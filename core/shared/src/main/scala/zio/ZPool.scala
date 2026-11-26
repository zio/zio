/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

import zio.ZIO.InterruptibilityRestorer
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `ZPool[E, A]` is a pool of items of type `A`, each of which may be
 * associated with the acquisition and release of resources. An attempt to get
 * an item `A` from a pool may fail with an error of type `E`.
 *
 * @see
 *   [[https://zio.dev/reference/resource/zpool/ ZPool]]
 */
trait ZPool[+Error, Item] {

  /**
   * Retrieves an item from the pool in a scoped effect. Note that if
   * acquisition fails, then the returned effect will fail for that same reason.
   * Retrying a failed acquisition attempt will repeat the acquisition attempt.
   *
   * The item returns to the pool when the scope closes, or is released when it
   * was invalidated. When every item is in use and the pool has reached its
   * maximum size, `get` waits until an item is returned. After the pool is shut
   * down, `get` is interrupted.
   */
  def get(implicit trace: Trace): ZIO[Scope, Error, Item]

  /**
   * Invalidates the specified item. The pool does not release it at once: it
   * releases the item when the item is returned, or when a later `get` takes it
   * from the pool. It then acquires a replacement when the pool held no more
   * than its minimum number of items or a `get` is waiting; otherwise the pool
   * shrinks by one item.
   */
  def invalidate(item: Item)(implicit trace: Trace): UIO[Unit]
}

/**
 * Constructors of pools: `make` for a fixed size or a size range with a time to
 * live, and `fromIterable` for items that already exist.
 */
object ZPool {

  /**
   * Creates a pool from a fixed number of pre-allocated items. This method
   * should only be used when there is no cleanup or release operation
   * associated with items in the pool. If cleanup or release is required, then
   * the `make` constructor should be used instead.
   *
   * The pool cannot create new items: after `invalidate`, a later `get` can die
   * with a `java.lang.IllegalArgumentException` ("No items in list!") when the
   * pool tries to replace the invalidated item. Creating the pool from an empty
   * `iterable` dies with a `java.lang.IllegalArgumentException`.
   */
  def fromIterable[A](iterable: => Iterable[A])(implicit trace: Trace): ZIO[Scope, Nothing, ZPool[Nothing, A]] =
    for {
      iterable <- ZIO.succeed(iterable)
      source   <- Ref.make(iterable.toList)
      get = if (iterable.isEmpty) ZIO.never
            else
              source.modify {
                case head :: tail => (head, tail)
                case Nil          => throw new IllegalArgumentException("No items in list!")
              }
      pool <- ZPool.make(get, iterable.size)
    } yield pool

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Scope`, which governs the lifetime of the pool. When the pool is shutdown
   * because the `Scope` is closed, the individual items allocated by the pool
   * will be released in some unspecified order.
   *
   * The pool acquires all `size` items when it is created. Closing its scope
   * waits until the items in use are returned.
   */
  def make[R, E, A](get: => ZIO[R, E, A], size: => Int)(implicit
    trace: Trace
  ): ZIO[R with Scope, Nothing, ZPool[E, A]] =
    for {
      size <- ZIO.succeed(size)
      pool <- makeWith(get, size to size)(Strategy.None)
    } yield pool

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Scope`, which governs
   * the lifetime of the pool. When the pool is shutdown because the `Scope` is
   * closed, the individual items allocated by the pool will be released in some
   * unspecified order.
   *
   * The pool acquires `range.start` items when it is created, and more on
   * demand up to `range.end`. When no item has been created or returned to the
   * pool for at least `timeToLive`, the pool releases idle items one at a time
   * while it holds more idle items than `range.start`. So it shrinks to
   * `range.start` items when none is in use, and keeps up to `range.start` idle
   * items besides the ones in use.
   *
   * {{{
   * final case class Connection(id: Int)
   *
   * def acquireDbConnection(ids: Ref[Int]): ZIO[Scope, Nothing, Connection] =
   *   ZIO.acquireRelease(ids.updateAndGet(_ + 1).map(Connection(_)))(_ => ZIO.unit)
   *
   * def useConnection(connection: Connection): UIO[Unit] =
   *   ZIO.debug(s"querying with connection \${connection.id}")
   *
   * val program: UIO[Unit] =
   *   ZIO.scoped {
   *     Ref.make(0).flatMap { ids =>
   *       ZPool.make(acquireDbConnection(ids), 10 to 20, 60.seconds).flatMap { pool =>
   *         ZIO.scoped {
   *           pool.get.flatMap { connection =>
   *             useConnection(connection)
   *           }
   *         }
   *       }
   *     }
   *   }
   * }}}
   */
  def make[R, E, A](get: => ZIO[R, E, A], range: => Range, timeToLive: => Duration)(implicit
    trace: Trace
  ): ZIO[R with Scope, Nothing, ZPool[E, A]] =
    makeWith[R, Any, E, A](get, range)(Strategy.TimeToLive(timeToLive))

  /**
   * A more powerful variant of `make` that allows specifying a `Strategy` that
   * describes how a pool whose excess items are not being used will be shrunk
   * down to the minimum size.
   */
  private def makeWith[R, R1, E, A](get: => ZIO[R, E, A], range: => Range)(strategy: => Strategy[R1, E, A])(implicit
    trace: Trace
  ): ZIO[R with R1 with Scope, Nothing, ZPool[E, A]] =
    ZIO.uninterruptibleMask { restore =>
      for {
        get      <- ZIO.succeed(get)
        range    <- ZIO.succeed(range)
        strategy <- ZIO.succeed(strategy)
        env      <- ZIO.environment[R]
        down     <- Ref.make(false)
        state    <- Ref.make(State(0, 0))
        items    <- Queue.bounded[Attempted[E, A]](range.end)
        alloc    <- Ref.make(Set.empty[A])
        initial  <- strategy.initial
        pool = DefaultPool(
                 get.provideSomeEnvironment[Scope](env.union[Scope](_)),
                 range,
                 down,
                 state,
                 items,
                 alloc,
                 strategy.track(initial)
               )
        _ <- restore(pool.initialize).foldCauseZIO(
               pool.shutdown *> Exit.failCause(_),
               _ => Exit.unit
             )
        shrink <- strategy.run(initial, pool.excess, pool.shrink).interruptible.forkDaemon
        _      <- ZIO.addFinalizer(pool.shutdown *> shrink.interrupt)
      } yield pool
    }

  private case class Attempted[+E, +A](result: Exit[E, A], finalizer: UIO[Any]) {
    def isFailure: Boolean = result.isFailure

    def forEach[R, E2](f: A => ZIO[R, E2, Any]): ZIO[R, E2, Any] =
      result match {
        case Exit.Failure(_) => ZIO.unit
        case Exit.Success(a) => f(a)
      }

    def toZIO(implicit trace: Trace): ZIO[Any, E, A] =
      ZIO.suspendSucceed(result)
  }

  private final case class DefaultPool[E, A](
    creator: ZIO[Scope, E, A],
    range: Range,
    isShuttingDown: Ref[Boolean],
    state: Ref[State],
    items: Queue[Attempted[E, A]],
    allocated: Ref[Set[A]],
    track: Exit[E, A] => UIO[Any]
  ) extends ZPool[E, A] {

    private def allocate(implicit restore: InterruptibilityRestorer, trace: Trace): UIO[Any] =
      for {
        scope     <- Scope.make
        exit      <- scope.extend(restore(creator)).exit
        attempted <- ZIO.succeed(Attempted(exit, scope.close(exit)))
        _         <- attempted.forEach(a => allocated.update(_ + a))
        _         <- items.offer(attempted)
        _         <- track(attempted.result)
        _         <- getAndShutdown.whenZIO(isShuttingDown.get)
      } yield attempted

    /**
     * Returns the number of free items in excess of the minimum size, capped at
     * the number of items in the pool: `(free - range.start) min size`. It is
     * zero or negative while the pool holds no more than `range.start` free
     * items, even when the pool holds more than `range.start` items.
     */
    def excess(implicit trace: Trace): UIO[Int] =
      state.get.map { case State(free, size) => size - range.start min free }

    def get(implicit trace: Trace): ZIO[Scope, E, A] =
      ZIO.InterruptibilityRestorer.make.flatMap { implicit restore =>
        def acquire: UIO[Attempted[E, A]] =
          isShuttingDown.get.flatMap { down =>
            if (down) ZIO.interrupt
            else
              state.modify { case State(size, free) =>
                if (free > 0 || size >= range.end)
                  (
                    items.take.flatMap { attempted =>
                      attempted.result match {
                        case Exit.Success(item) =>
                          allocated.get.flatMap { set =>
                            if (set.contains(item)) Exit.succeed(attempted)
                            else finalizeInvalid(attempted) *> acquire
                          }
                        case _ =>
                          state.modify { case State(size, free) =>
                            if (size <= range.start)
                              attempted.finalizer *> allocate -> State(size, free + 1)
                            else
                              attempted.finalizer -> State(size - 1, free)
                          }.flatten *> Exit.succeed(attempted)
                      }
                    },
                    State(size, free - 1)
                  )
                else if (size >= 0)
                  allocate *> acquire -> State(size + 1, free + 1)
                else
                  ZIO.interrupt -> State(size, free)
              }.flatten
          }

        def release(attempted: Attempted[E, A]): UIO[Any] =
          attempted.result match {
            case Exit.Success(item) =>
              allocated.get.flatMap { set =>
                if (set.contains(item))
                  state.update(state => state.copy(free = state.free + 1)) *>
                    items.offer(attempted) *>
                    track(attempted.result) *>
                    getAndShutdown.whenZIO(isShuttingDown.get)
                else finalizeInvalid(attempted)
              }
            case _ =>
              Exit.unit // Handled during acquire
          }

        def finalizeInvalid(attempted: Attempted[E, A]): UIO[Any] =
          attempted.finalizer *>
            state.modify { case State(size, free) =>
              if (size <= range.start || free < 0)
                allocate -> State(size, free + 1)
              else
                ZIO.unit -> State(size - 1, free)
            }.flatten

        ZIO.acquireRelease(acquire)(release).flatMap(_.result).disconnect
      }

    /**
     * Begins pre-allocating pool entries based on minimum pool size.
     */
    def initialize(implicit trace: Trace): UIO[Unit] =
      ZIO.uninterruptibleMask { implicit restore =>
        ZIO.replicateZIODiscard(range.start) {
          state.modify { case State(size, free) =>
            if (size < range.start && size >= 0)
              allocate -> State(size + 1, free + 1)
            else
              ZIO.unit -> State(size, free)
          }.flatten
        }
      }

    def invalidate(item: A)(implicit trace: zio.Trace): UIO[Unit] =
      allocated.update(_ - item)

    /**
     * Shrinks the pool down, but never to less than the minimum size.
     */
    def shrink(implicit trace: Trace): UIO[Any] =
      ZIO.uninterruptible {
        state.modify { case State(size, free) =>
          if (size > range.start && free > 0)
            (
              items.take.flatMap { attempted =>
                attempted.forEach(a => allocated.update(_ - a)) *>
                  attempted.finalizer *>
                  state.update(state => state.copy(size = state.size - 1))
              },
              State(size, free - 1)
            )
          else
            ZIO.unit -> State(size, free)
        }.flatten
      }

    /**
     * Gets items from the pool and shuts them down as long as there are items
     * free, signalling shutdown of the pool if the pool is empty.
     */
    private def getAndShutdown(implicit trace: Trace): UIO[Unit] =
      state.modify { case State(size, free) =>
        if (free > 0)
          (
            items.take.foldCauseZIO(
              _ => ZIO.unit,
              attempted =>
                attempted.forEach(a => allocated.update(_ - a)) *>
                  attempted.finalizer *>
                  state.update(state => state.copy(size = state.size - 1)) *>
                  getAndShutdown
            ),
            State(size, free - 1)
          )
        else if (size > 0)
          ZIO.unit -> State(size, free)
        else
          items.shutdown -> State(size - 1, free)
      }.flatten

    final def shutdown(implicit trace: Trace): UIO[Unit] =
      isShuttingDown.modify { down =>
        if (down)
          items.awaitShutdown -> true
        else
          getAndShutdown *> items.awaitShutdown -> true
      }.flatten
  }

  private case class State(size: Int, free: Int)

  /**
   * A `Strategy` describes the protocol for how a pool whose excess items are
   * not being used should shrink down to the minimum pool size.
   */
  private trait Strategy[-Environment, -Error, -Item] {

    /**
     * Describes the type of the state maintained by the strategy.
     */
    type State

    /**
     * Describes how the initial state of the strategy should be allocated.
     */
    def initial(implicit trace: Trace): URIO[Environment, State]

    /**
     * Describes how the state of the strategy should be updated when an item is
     * added to the pool or returned to the pool.
     */
    def track(state: State)(item: Exit[Error, Item])(implicit trace: Trace): UIO[Unit]

    /**
     * Describes how excess items that are not being used should shrink down.
     */
    def run(state: State, getExcess: UIO[Int], shrink: UIO[Any])(implicit trace: Trace): UIO[Unit]
  }

  private object Strategy {

    /**
     * A strategy that does nothing to shrink excess items. This is useful when
     * the minimum size of the pool is equal to its maximum size and so there is
     * nothing to do.
     */
    case object None extends Strategy[Any, Any, Any] {
      type State = Any
      def initial(implicit trace: Trace): UIO[Any] =
        ZIO.unit
      def track(state: Any)(attempted: Exit[Any, Any])(implicit trace: Trace): UIO[Unit] =
        ZIO.unit
      def run(state: Any, getExcess: UIO[Int], shrink: UIO[Any])(implicit trace: Trace): UIO[Unit] =
        ZIO.unit
    }

    /**
     * A strategy that, when no item has been created or returned to the pool
     * for the specified duration, shrinks the pool one free item at a time
     * while `excess` is positive.
     */
    final case class TimeToLive(timeToLive: Duration) extends Strategy[Any, Any, Any] {
      type State = (Clock, Ref[java.time.Instant])
      def initial(implicit trace: Trace): UIO[State] =
        for {
          clock <- ZIO.clock
          now   <- Clock.instant
          ref   <- Ref.make(now)
        } yield (clock, ref)
      def track(state: (Clock, Ref[java.time.Instant]))(attempted: Exit[Any, Any])(implicit
        trace: Trace
      ): UIO[Unit] = {
        val (clock, ref) = state
        for {
          now <- clock.instant
          _   <- ref.set(now)
        } yield ()
      }
      def run(state: (Clock, Ref[java.time.Instant]), getExcess: UIO[Int], shrink: UIO[Any])(implicit
        trace: Trace
      ): UIO[Unit] = {
        val (clock, ref) = state
        getExcess.flatMap { excess =>
          if (excess <= 0)
            clock.sleep(timeToLive) *> run(state, getExcess, shrink)
          else
            ref.get.zip(clock.instant).flatMap { case (start, end) =>
              val duration = java.time.Duration.between(start, end)
              if (duration >= timeToLive) shrink *> run(state, getExcess, shrink)
              else clock.sleep(timeToLive) *> run(state, getExcess, shrink)
            }
        }
      }
    }
  }

}
