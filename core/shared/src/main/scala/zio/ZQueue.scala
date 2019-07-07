/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

/**
 * A `ZQueue[RA, EA, RB, EB, A, B]` is a lightweight, asynchronous queue into which values of
 * type `A` can be enqueued and of which elements of type `B` can be dequeued. The queue's
 * enqueueing operations may utilize an environment of type `RA` and may fail with errors of
 * type `EA`. The dequeueing operations may utilize an environment of type `RB` and may fail
 * with errors of type `EB`.
 */
trait ZQueue[-RA, +EA, -RB, +EB, -A, +B] extends Serializable { self =>

  /**
   * Waits until the queue is shutdown.
   * The `IO` returned by this method will not resume until the queue has been shutdown.
   * If the queue is already shutdown, the `IO` will resume right away.
   */
  def awaitShutdown: UIO[Unit]

  /**
   * How many elements can hold in the queue
   */
  def capacity: Int

  /**
   * `true` if `shutdown` has been called.
   */
  def isShutdown: UIO[Boolean]

  /**
   * Places one value in the queue.
   */
  def offer(a: A): ZIO[RA, EA, Boolean]

  /**
   * For Bounded Queue: uses the `BackPressure` Strategy, places the values in the queue and returns always true
   * If the queue has reached capacity, then
   * the fiber performing the `offerAll` will be suspended until there is room in
   * the queue.
   *
   * For Unbounded Queue:
   * Places all values in the queue and returns true.
   *
   * For Sliding Queue: uses `Sliding` Strategy
   * If there is a room in the queue, it places the values and returns true otherwise it removed the old elements and
   * enqueues the new ones
   *
   * For Dropping Queue: uses `Dropping` Strategy,
   * It places the values in the queue but if there is no room it will not enqueue them and returns false
   *
   */
  def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean]

  /**
   * Interrupts any fibers that are suspended on `offer` or `take`.
   * Future calls to `offer*` and `take*` will be interrupted immediately.
   */
  def shutdown: UIO[Unit]

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  def size: UIO[Int]

  /**
   * Removes the oldest value in the queue. If the queue is empty, this will
   * return a computation that resumes when an item has been added to the queue.
   */
  def take: ZIO[RB, EB, B]

  /**
   * Removes all the values in the queue and returns the list of the values. If the queue
   * is empty returns empty list.
   */
  def takeAll: ZIO[RB, EB, List[B]]

  /**
   * Takes up to max number of values in the queue.
   */
  def takeUpTo(max: Int): ZIO[RB, EB, List[B]]

  /**
   * Alias for `both`.
   */
  def &&[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: ZQueue[RA1, EA1, RB1, EB1, A1, C]
  ): ZQueue[RA1, EA1, RB1, EB1, A1, (B, C)] =
    both(that)

  /**
   * Like `bothWith`, but tuples the elements instead of applying a function.
   */
  def both[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: ZQueue[RA1, EA1, RB1, EB1, A1, C]
  ): ZQueue[RA1, EA1, RB1, EB1, A1, (B, C)] =
    bothWith(that)((_, _))

  /**
   * Like `bothWithM`, but uses a pure function.
   */
  def bothWith[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, D](
    that: ZQueue[RA1, EA1, RB1, EB1, A1, C]
  )(f: (B, C) => D): ZQueue[RA1, EA1, RB1, EB1, A1, D] =
    bothWithM(that)((a, b) => IO.succeed(f(a, b)))

  /**
   * Creates a new queue from this queue and another. Offering to the composite queue
   * will broadcast the elements to both queues; taking from the composite queue
   * will dequeue elements from both queues and apply the function point-wise.
   *
   * Note that using queues with different strategies may result in surprising behavior.
   * For example, a dropping queue and a bounded queue composed together may apply `f`
   * to different elements.
   */
  def bothWithM[RA1 <: RA, EA1 >: EA, A1 <: A, RB1 <: RB, EB1 >: EB, C, R3 <: RB1, E3 >: EB1, D](
    that: ZQueue[RA1, EA1, RB1, EB1, A1, C]
  )(f: (B, C) => ZIO[R3, E3, D]): ZQueue[RA1, EA1, R3, E3, A1, D] =
    new ZQueue[RA1, EA1, R3, E3, A1, D] {
      def capacity: Int = math.min(self.capacity, that.capacity)

      def offer(a: A1): ZIO[RA1, EA1, Boolean]               = self.offer(a).zipWithPar(that.offer(a))(_ && _)
      def offerAll(as: Iterable[A1]): ZIO[RA1, EA1, Boolean] = self.offerAll(as).zipWithPar(that.offerAll(as))(_ && _)

      def awaitShutdown: UIO[Unit] = self.awaitShutdown *> that.awaitShutdown
      def size: UIO[Int]           = self.size.zipWithPar(that.size)(math.max)
      def shutdown: UIO[Unit]      = self.shutdown.zipWithPar(that.shutdown)((_, _) => ())
      def isShutdown: UIO[Boolean] = self.isShutdown
      def take: ZIO[R3, E3, D]     = self.take.zipPar(that.take).flatMap(f.tupled)

      def takeAll: ZIO[R3, E3, List[D]] =
        self.takeAll.zipPar(that.takeAll).flatMap {
          case (bs, cs) =>
            val bsIt = bs.iterator
            val csIt = cs.iterator

            ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }

      def takeUpTo(max: Int): ZIO[R3, E3, List[D]] =
        self.takeUpTo(max).zipPar(that.takeUpTo(max)).flatMap {
          case (bs, cs) =>
            val bsIt = bs.iterator
            val csIt = cs.iterator

            ZIO.foreach(bsIt.zip(csIt).toList)(f.tupled)
        }
    }

  /**
   * Transforms elements enqueued into this queue with a pure function.
   */
  def contramap[C](f: C => A): ZQueue[RA, EA, RB, EB, C, B] =
    new ZQueue[RA, EA, RB, EB, C, B] {
      def capacity: Int = self.capacity

      def offer(c: C): ZIO[RA, EA, Boolean] =
        self.offer(f(c))

      def offerAll(as: Iterable[C]): ZIO[RA, EA, Boolean] = self.offerAll(as.map(f))

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /**
   * Transforms elements enqueued into this queue with an effectful function.
   */
  def contramapM[RA2 <: RA, EA2 >: EA, C](f: C => ZIO[RA2, EA2, A]): ZQueue[RA2, EA2, RB, EB, C, B] =
    new ZQueue[RA2, EA2, RB, EB, C, B] {
      def capacity: Int = self.capacity

      def offer(c: C): ZIO[RA2, EA2, Boolean] =
        f(c).flatMap(self.offer)

      def offerAll(as: Iterable[C]): ZIO[RA2, EA2, Boolean] =
        ZIO.foreach(as)(f).flatMap(self.offerAll)

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /**
   * Applies a filter to elements enqueued into this queue. Elements that do not
   * pass the filter will be immediately dropped.
   */
  def filterInput[A1 <: A](f: A1 => Boolean): ZQueue[RA, EA, RB, EB, A1, B] =
    new ZQueue[RA, EA, RB, EB, A1, B] {
      def capacity: Int = self.capacity

      def offer(a: A1): ZIO[RA, EA, Boolean] =
        if (f(a)) self.offer(a)
        else IO.succeed(false)

      def offerAll(as: Iterable[A1]): ZIO[RA, EA, Boolean] = {
        val filtered = as filter f

        if (filtered.isEmpty) ZIO.succeed(false)
        else self.offerAll(filtered)
      }

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /**
   * Like `filterInput`, but uses an effectful function to filter the elements.
   */
  def filterInputM[R2 <: RA, E2 >: EA, A1 <: A](f: A1 => ZIO[R2, E2, Boolean]): ZQueue[R2, E2, RB, EB, A1, B] =
    new ZQueue[R2, E2, RB, EB, A1, B] {
      def capacity: Int = self.capacity

      def offer(a: A1): ZIO[R2, E2, Boolean] =
        f(a) flatMap {
          if (_) self.offer(a)
          else IO.succeed(false)
        }

      def offerAll(as: Iterable[A1]): ZIO[R2, E2, Boolean] =
        ZIO.foreach(as)(a => f(a).map(if (_) Some(a) else None)).flatMap { maybeAs =>
          val filtered = maybeAs.flatten
          if (filtered.isEmpty) ZIO.succeed(false)
          else self.offerAll(filtered)
        }

      def awaitShutdown: UIO[Unit]                 = self.awaitShutdown
      def size: UIO[Int]                           = self.size
      def shutdown: UIO[Unit]                      = self.shutdown
      def isShutdown: UIO[Boolean]                 = self.isShutdown
      def take: ZIO[RB, EB, B]                     = self.take
      def takeAll: ZIO[RB, EB, List[B]]            = self.takeAll
      def takeUpTo(max: Int): ZIO[RB, EB, List[B]] = self.takeUpTo(max)
    }

  /*
   * Transforms elements dequeued from this queue with a function.
   */
  def map[C](f: B => C): ZQueue[RA, EA, RB, EB, A, C] =
    new ZQueue[RA, EA, RB, EB, A, C] {
      def capacity: Int                                   = self.capacity
      def offer(a: A): ZIO[RA, EA, Boolean]               = self.offer(a)
      def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] = self.offerAll(as)
      def awaitShutdown: UIO[Unit]                        = self.awaitShutdown
      def size: UIO[Int]                                  = self.size
      def shutdown: UIO[Unit]                             = self.shutdown
      def isShutdown: UIO[Boolean]                        = self.isShutdown
      def take: ZIO[RB, EB, C]                            = self.take.map(f)
      def takeAll: ZIO[RB, EB, List[C]]                   = self.takeAll.map(_.map(f))
      def takeUpTo(max: Int): ZIO[RB, EB, List[C]]        = self.takeUpTo(max).map(_.map(f))
    }

  /**
   * Transforms elements dequeued from this queue with an effectful function.
   */
  def mapM[R2 <: RB, E2 >: EB, C](f: B => ZIO[R2, E2, C]): ZQueue[RA, EA, R2, E2, A, C] =
    new ZQueue[RA, EA, R2, E2, A, C] {
      def capacity: Int                                   = self.capacity
      def offer(a: A): ZIO[RA, EA, Boolean]               = self.offer(a)
      def offerAll(as: Iterable[A]): ZIO[RA, EA, Boolean] = self.offerAll(as)
      def awaitShutdown: UIO[Unit]                        = self.awaitShutdown
      def size: UIO[Int]                                  = self.size
      def shutdown: UIO[Unit]                             = self.shutdown
      def isShutdown: UIO[Boolean]                        = self.isShutdown
      def take: ZIO[R2, E2, C]                            = self.take.flatMap(f)
      def takeAll: ZIO[R2, E2, List[C]]                   = self.takeAll.flatMap(ZIO.foreach(_)(f))
      def takeUpTo(max: Int): ZIO[R2, E2, List[C]]        = self.takeUpTo(max).flatMap(ZIO.foreach(_)(f))
    }

  /**
   * Take the head option of values in the queue.
   */
  final def poll: ZIO[RB, EB, Option[B]] =
    takeUpTo(1).map(_.headOption)

}
