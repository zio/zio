package zio

import zio.clock.Clock
import zio.duration.Duration

/**
 * A `ZPool[E, A]` is a pool of items of type `A`, each of which may be
 * associated with the acquisition and release of resources. An attempt to get
 * an item `A` from a pool may fail with an error of type `E`.
 */
trait ZPool[+E, Item] {

  /**
   * Retrieves an item from the pool in a `Managed` effect. Note that if
   * acquisition fails, then the returned effect will fail for that same reason.
   * Retrying a failed acquisition attempt will repeat the acquisition attempt.
   */
  def get: ZManaged[Any, E, Item]

  /**
   * Invalidates the specified item. This will cause the pool to eventually
   * reallocate the item, although this reallocation may occur lazily rather
   * than eagerly.
   */
  def invalidate(item: Item): UIO[Unit]
}
object ZPool {

  implicit private class ZioOps[-R, +E, +A](private val self: ZIO[R, E, A]) extends AnyVal {
    final def exit: URIO[R, Exit[E, A]] =
      new ZIO.Fold[R, E, Nothing, A, Exit[E, A]](
        self,
        cause => ZIO.succeedNow(Exit.halt(cause)),
        success => ZIO.succeedNow(Exit.succeed(success))
      )
  }

  /**
   * Creates a pool from a fixed number of pre-allocated items. This method
   * should only be used when there is no cleanup or release operation
   * associated with items in the pool. If cleanup or release is required, then
   * the `make` constructor should be used instead.
   */
  def fromIterable[A](iterable: => Iterable[A]): UManaged[ZPool[Nothing, A]] =
    for {
      iterable <- ZManaged.succeed(iterable)
      source   <- Ref.make(iterable.toList).toManaged_
      get = if (iterable.isEmpty) ZIO.never
            else
              source.modify {
                case head :: tail => (head, tail)
                case Nil          => throw new IllegalArgumentException("No items in list!")
              }
      pool <- ZPool.make(ZManaged.fromEffect(get), iterable.size)
    } yield pool

  /**
   * Makes a new pool of the specified fixed size. The pool is returned in a
   * `Managed`, which governs the lifetime of the pool. When the pool is
   * shutdown because the `Managed` is used, the individual items allocated by
   * the pool will be released in some unspecified order.
   */
  def make[R, E, A](get: ZManaged[R, E, A], size: => Int): URManaged[R, ZPool[E, A]] =
    for {
      size <- ZManaged.succeed(size)
      pool <- makeWith(get, size to size)(Strategy.None)
    } yield pool

  /**
   * Makes a new pool with the specified minimum and maximum sizes and time to
   * live before a pool whose excess items are not being used will be shrunk
   * down to the minimum size. The pool is returned in a `Managed`, which
   * governs the lifetime of the pool. When the pool is shutdown because the
   * `Managed` is used, the individual items allocated by the pool will be
   * released in some unspecified order.
   * {{{
   * ZPool.make(acquireDbConnection, 10 to 20, 60.seconds).use { pool =>
   *   pool.get.use {
   *     connection => useConnection(connection)
   *   }
   * }
   * }}}
   */
  def make[R, E, A](
    get: ZManaged[R, E, A],
    range: => Range,
    timeToLive: => Duration
  ): ZManaged[R with Clock, Nothing, ZPool[E, A]] =
    makeWith(get, range)(Strategy.TimeToLive(timeToLive))

  /**
   * A more powerful variant of `make` that allows specifying a `Strategy` that
   * describes how a pool whose excess items are not being used will be shrunk
   * down to the minimum size.
   */
  private def makeWith[R, R1, E, A](get: ZManaged[R, E, A], range: => Range)(
    strategy: => Strategy[R1, E, A]
  ): ZManaged[R with R1, Nothing, ZPool[E, A]] =
    for {
      get      <- ZManaged.succeed(get)
      range    <- ZManaged.succeed(range)
      strategy <- ZManaged.succeed(strategy)
      env      <- ZManaged.environment[R]
      down     <- Ref.make(false).toManaged_
      state    <- Ref.make(State(0, 0)).toManaged_
      items    <- Queue.bounded[Attempted[E, A]](range.end).toManaged_
      inv      <- Ref.make(Set.empty[A]).toManaged_
      initial  <- strategy.initial.toManaged_
      pool      = DefaultPool(get.provide(env), range, down, state, items, inv, strategy.track(initial))
      fiber    <- pool.initialize.forkDaemon.toManaged_
      shrink   <- strategy.run(initial, pool.excess, pool.shrink).forkDaemon.toManaged_
      _        <- ZManaged.finalizer(fiber.interrupt *> shrink.interrupt *> pool.shutdown)
    } yield pool

  private case class Attempted[+E, +A](result: Exit[E, A], finalizer: UIO[Any]) {
    def isFailure: Boolean = !result.succeeded

    def forEach[R, E2](f: A => ZIO[R, E2, Any]): ZIO[R, E2, Any] =
      result match {
        case Exit.Failure(_) => ZIO.unit
        case Exit.Success(a) => f(a)
      }

    def toManaged: ZManaged[Any, E, A] =
      ZIO.done(result).toManaged_
  }

  private case class DefaultPool[R, E, A, S](
    creator: ZManaged[Any, E, A],
    range: Range,
    isShuttingDown: Ref[Boolean],
    state: Ref[State],
    items: Queue[Attempted[E, A]],
    invalidated: Ref[Set[A]],
    track: Exit[E, A] => UIO[Any]
  ) extends ZPool[E, A] {

    /**
     * Returns the number of items in the pool in excess of the minimum size.
     */
    def excess: UIO[Int] =
      state.get.map { case State(free, size) => size - range.start min free }

    override def get: ZManaged[Any, E, A] = {

      def acquire: UIO[Attempted[E, A]] =
        isShuttingDown.get.flatMap { down =>
          if (down) ZIO.interrupt
          else
            state.modify { case State(size, free) =>
              if (free > 0 || size >= range.end)
                (
                  items.take.flatMap { acquired =>
                    acquired.result match {
                      case Exit.Success(item) =>
                        invalidated.get.flatMap { set =>
                          if (set.contains(item))
                            state.update(state => state.copy(free = state.free + 1)) *>
                              allocate *>
                              acquire
                          else
                            ZIO.succeedNow(acquired)
                        }
                      case _ =>
                        ZIO.succeedNow(acquired)
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
        if (attempted.isFailure)
          state.modify { case State(size, free) =>
            if (size <= range.start)
              allocate -> State(size, free + 1)
            else
              ZIO.unit -> State(size - 1, free)
          }.flatten
        else
          state.update(state => state.copy(free = state.free + 1)) *>
            items.offer(attempted) *>
            track(attempted.result) *>
            getAndShutdown.whenM(isShuttingDown.get)

      ZManaged.make(acquire)(release(_)).flatMap(_.toManaged)
    }

    /**
     * Begins pre-allocating pool entries based on minimum pool size.
     */
    final def initialize: UIO[Unit] =
      ZIO.replicateM_(range.start) {
        ZIO.uninterruptibleMask { restore =>
          state.modify { case State(size, free) =>
            if (size < range.start && size >= 0)
              (
                for {
                  reservation <- creator.reserve
                  exit        <- restore(reservation.acquire).exit
                  attempted   <- ZIO.succeed(Attempted(exit, reservation.release(Exit.succeed(()))))
                  _           <- items.offer(attempted)
                  _           <- track(attempted.result)
                  _           <- getAndShutdown.whenM(isShuttingDown.get)
                } yield attempted,
                State(size + 1, free + 1)
              )
            else
              ZIO.unit -> State(size, free)
          }.flatten
        }
      }

    override def invalidate(item: A): UIO[Unit] =
      invalidated.update(_ + item)

    /**
     * Shrinks the pool down, but never to less than the minimum size.
     */
    def shrink: UIO[Any] =
      ZIO.uninterruptible {
        state.modify { case State(size, free) =>
          if (size > range.start && free > 0)
            (
              items.take.flatMap { attempted =>
                attempted.forEach(a => invalidated.update(_ - a)) *>
                  attempted.finalizer *>
                  state.update(state => state.copy(size = state.size - 1))
              },
              State(size, free - 1)
            )
          else
            ZIO.unit -> State(size, free)
        }.flatten
      }

    private def allocate: UIO[Any] =
      ZIO.uninterruptibleMask { restore =>
        for {
          reservation <- creator.reserve
          exit        <- restore(reservation.acquire).exit
          attempted   <- ZIO.succeed(Attempted(exit, reservation.release(Exit.succeed(()))))
          _           <- items.offer(attempted)
          _           <- track(attempted.result)
          _           <- getAndShutdown.whenM(isShuttingDown.get)
        } yield attempted
      }

    /**
     * Gets items from the pool and shuts them down as long as there are items
     * free, signalling shutdown of the pool if the pool is empty.
     */
    private def getAndShutdown: UIO[Unit] =
      state.modify { case State(size, free) =>
        if (free > 0)
          (
            items.take.foldCauseM(
              _ => ZIO.unit,
              attempted =>
                attempted.forEach(a => invalidated.update(_ - a)) *>
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

    final def shutdown: UIO[Unit] =
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
  private trait Strategy[-Environment, -E, -Item] {

    /**
     * Describes the type of the state maintained by the strategy.
     */
    type State

    /**
     * Describes how the initial state of the strategy should be allocated.
     */
    def initial: URIO[Environment, State]

    /**
     * Describes how the state of the strategy should be updated when an item is
     * added to the pool or returned to the pool.
     */
    def track(state: State)(item: Exit[E, Item]): UIO[Unit]

    /**
     * Describes how excess items that are not being used should shrink down.
     */
    def run(state: State, getExcess: UIO[Int], shrink: UIO[Any]): UIO[Unit]
  }

  private object Strategy {

    /**
     * A strategy that does nothing to shrink excess items. This is useful when
     * the minimum size of the pool is equal to its maximum size and so there is
     * nothing to do.
     */
    case object None extends Strategy[Any, Any, Any] {
      type State = Unit
      override def initial: URIO[Any, Unit] =
        ZIO.unit
      override def track(state: Unit)(attempted: Exit[Any, Any]): UIO[Unit] =
        ZIO.unit
      override def run(state: Unit, getExcess: UIO[Int], shrink: UIO[Any]): UIO[Unit] =
        ZIO.unit
    }

    /**
     * A strategy that shrinks the pool down to its minimum size if items in the
     * pool have not been used for the specified duration.
     */
    final case class TimeToLive(timeToLive: Duration) extends Strategy[Clock, Any, Any] {
      type State = (Clock.Service, Ref[java.time.Instant])

      override def initial: URIO[Clock, State] =
        for {
          clock <- ZIO.service[Clock.Service]
          now   <- clock.instant
          ref   <- Ref.make(now)
        } yield (clock, ref)

      override def track(state: (Clock.Service, Ref[java.time.Instant]))(attempted: Exit[Any, Any]): UIO[Unit] = {
        val (clock, ref) = state
        for {
          now <- clock.instant
          _   <- ref.set(now)
        } yield ()
      }

      override def run(
        state: (Clock.Service, Ref[java.time.Instant]),
        getExcess: UIO[Int],
        shrink: UIO[Any]
      ): UIO[Unit] = {
        import duration._

        val (clock, ref) = state
        getExcess.flatMap { excess =>
          if (excess <= 0)
            clock.sleep(timeToLive) *> run(state, getExcess, shrink)
          else
            ref.get.zip(clock.instant).flatMap { case (start, end) =>
              val duration: Duration = java.time.Duration.between(start, end)
              if (duration >= timeToLive) shrink *> run(state, getExcess, shrink)
              else clock.sleep(timeToLive) *> run(state, getExcess, shrink)
            }
        }
      }
    }
  }

}
