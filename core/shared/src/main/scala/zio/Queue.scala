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

import zio.internal.MutableConcurrentQueue
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec

/**
 * A `Queue` is a lightweight, asynchronous queue into which values can be
 * enqueued and of which elements can be dequeued.
 */
sealed abstract class Queue[A] extends Dequeue.Internal[A] with Enqueue.Internal[A] {

  /**
   * Checks whether the queue is currently empty.
   */
  override final def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ <= 0)

  /**
   * Checks whether the queue is currently full.
   */
  override final def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ >= capacity)
}

object Queue extends QueuePlatformSpecific {
  import java.util.concurrent.atomic.AtomicBoolean

  // Wrapper for Promise with atomic claim flag
  private final case class Taker[A](promise: Promise[Nothing, A], claimed: AtomicBoolean)

  def unbounded[A](implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberIdWith { fiberId =>
      ZIO.succeedUnsafe(implicit unsafe => Queue.unsafe.unbounded[A](fiberId))
    }

  def bounded[A](requestedCapacity: Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberIdWith { fiberId =>
      ZIO.succeedUnsafe(implicit unsafe => Queue.unsafe.bounded[A](requestedCapacity, fiberId))
    }

  def dropping[A](requestedCapacity: Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberIdWith { fiberId =>
      ZIO.succeedUnsafe(implicit unsafe => Queue.unsafe.dropping[A](requestedCapacity, fiberId))
    }

  def sliding[A](requestedCapacity: Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberIdWith { fiberId =>
      ZIO.succeedUnsafe(implicit unsafe => Queue.unsafe.sliding[A](requestedCapacity, fiberId))
    }

  object unsafe {

    def bounded[A](requestedCapacity: Int, fiberId: FiberId)(implicit unsafe: Unsafe): Queue[A] =
      createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), Strategy.BackPressure(), fiberId)

    def dropping[A](requestedCapacity: Int, fiberId: FiberId)(implicit unsafe: Unsafe): Queue[A] =
      createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), Strategy.Dropping(), fiberId)

    def sliding[A](requestedCapacity: Int, fiberId: FiberId)(implicit unsafe: Unsafe): Queue[A] =
      createQueue(MutableConcurrentQueue.bounded[A](requestedCapacity), Strategy.Sliding(), fiberId)

    def unbounded[A](fiberId: FiberId)(implicit unsafe: Unsafe): Queue[A] =
      createQueue(MutableConcurrentQueue.unbounded[A], Strategy.Dropping(), fiberId)

  }

  private def createQueue[A](
    queue: MutableConcurrentQueue[A],
    strategy: Strategy[A],
    fiberId: FiberId
  )(implicit unsafe: Unsafe): Queue[A] = {
    val p = Promise.unsafe.make[Nothing, Unit](fiberId)
    unsafeCreate(
      queue,
      new ConcurrentDeque[Taker[A]],
      p,
      new AtomicBoolean(false),
      strategy
    )
  }

  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Taker[A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Queue[A] = new QueueImpl[A](queue, takers, shutdownHook, shutdownFlag, strategy)

  private final class QueueImpl[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Taker[A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ) extends Queue[A] {

    private def removeTaker(taker: Promise[Nothing, A])(implicit trace: Trace): UIO[Unit] =
      ZIO.succeed(takers.remove(taker))

    override def capacity: Int = queue.capacity

    override def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
      ZIO.uninterruptibleMask { restore =>
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val noRemaining =
              if (queue.isEmpty()) {
                var completed = false
                var done      = false
                while (!done) {
                  val taker = takers.poll()
                  if (taker eq null) done = true
                  else {
                    // Only complete if not already claimed (fixes race condition)
                    if (taker.claimed.compareAndSet(false, true)) {
                      // Always complete the promise immediately after claiming
                      unsafeCompletePromise(taker.promise, a)
                      completed = true
                      done = true
                    } else {
                      // Taker was already claimed (interrupted), try next
                    }
                  }
                }
                completed
              } else false

            if (noRemaining) Exit.`true`
            else {
              // not enough takers, offer to the queue
              val succeeded = queue.offer(a)

              if (succeeded) {
                strategy.unsafeCompleteTakers(queue, takers)
                Exit.`true`
              } else
                strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownFlag)
            }
          }
        }
      }

    override def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
      ZIO.uninterruptibleMask { restore =>
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val takerChunk             = if (queue.isEmpty()) unsafePollNDeque(takers, as.size) else Chunk.empty
            val (forTakers, remaining) = as.splitAt(takerChunk.size)

            // Only complete takers that haven't been claimed (fixes race condition)
            var completedCount = 0
            (takerChunk zip forTakers).foreach { case (taker, item) =>
              if (taker.claimed.compareAndSet(false, true)) {
                // Always complete the promise immediately after claiming
                unsafeCompletePromise(taker.promise, item)
                completedCount += 1
              } else {
                // Taker was already claimed (interrupted), try next
              }
            }

            // Adjust remaining based on actual completions
            val actualRemaining = as.drop(completedCount)

            if (actualRemaining.isEmpty) Exit.emptyChunk
            else {
              // not enough takers, offer to the queue
              val surplus = unsafeOfferAll(queue, actualRemaining)

              if (surplus.isEmpty) {
                strategy.unsafeCompleteTakers(queue, takers)
                Exit.emptyChunk
              } else
                strategy.handleSurplus(surplus, queue, takers, shutdownFlag).map { offered =>
                  if (offered) Chunk.empty else surplus
                }
            }
          }
        }
      }

    override def awaitShutdown(implicit trace: Trace): UIO[Unit] = shutdownHook.await

    override def size(implicit trace: Trace): UIO[Int] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else
          Exit.succeed(queue.size() - takers.size() + strategy.surplusSize)
      }

    override def shutdown(implicit trace: Trace): UIO[Unit] =
      ZIO.fiberIdWith { fiberId =>
        if (shutdownFlag.compareAndSet(false, true)) {
          implicit val unsafe: Unsafe = Unsafe
          shutdownHook.unsafe.succeedUnit
          val it = unsafePollAllDeque(takers).iterator
          while (it.hasNext) {
            it.next().promise.unsafe.interruptAs(fiberId)
          }
          strategy.shutdown(fiberId)
        }
        Exit.unit
      }.uninterruptible

    override def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(shutdownFlag.get)

    override def take(implicit trace: Trace): UIO[A] =
      ZIO.fiberIdWith { fiberId =>
        if (shutdownFlag.get) ZIO.interrupt
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null =>
              val p     = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)
              val taker = Taker(p, new AtomicBoolean(false))
              ZIO.uninterruptibleMask { restore =>
                ZIO.suspendSucceed {
                  takers.offer(taker)
                  strategy.unsafeCompleteTakers(queue, takers)
                  if (shutdownFlag.get) ZIO.interrupt else restore(p.await)
                }.onInterrupt(
                  ZIO.succeed {
                    // Only remove if not claimed: this prevents the race where
                    // a taker is interrupted after being matched, which would otherwise lose the item.
                    if (!taker.claimed.get()) {
                      takers.remove(taker)
                    }
                    // If claimed, do not remove; let the offerer complete the promise
                    // The promise completion is uninterruptible, so it will always complete
                    // This ensures that items are never lost
                  }
                )
              }
            case item =>
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              Exit.succeed(item)
          }
        }
      }

    override def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else {
          val as = unsafePollAll(queue)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          } else {
            Exit.emptyChunk
          }
        }
      }

    override def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else {
          val as = unsafePollN(queue, max)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          } else {
            Exit.emptyChunk
          }
        }
      }

    override def poll(implicit trace: Trace): UIO[Option[A]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get)
          ZIO.interrupt
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null => Exit.none
            case v =>
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              Exit.succeed(Some(v))
          }
        }
      }
  }

  private sealed abstract class Strategy[A] {
    private[this] val draining = new AtomicBoolean(false)

    def handleSurplus(
      as: Iterable[A],
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Taker[A]],
      isShutdown: AtomicBoolean
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Taker[A]]
    ): Unit

    def surplusSize: Int

    def shutdown(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Unit

    @tailrec
    final def unsafeCompleteTakers(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Taker[A]]
    ): Unit =
      if (!takers.isEmpty && draining.compareAndSet(false, true)) {
        try {
          var keepPolling      = !queue.isEmpty()
          val empty            = null.asInstanceOf[A]
          var notifyEmptySpace = false
          while (keepPolling) {
            val taker = takers.poll()
            if (taker eq null) keepPolling = false
            else {
              queue.poll(empty) match {
                case null =>
                  // Only put back if the taker hasn't been claimed (interrupted)
                  if (!taker.claimed.get()) {
                    takers.addFirst(taker)
                  }
                  keepPolling = false
                case a =>
                  // Only complete if not already claimed
                  if (taker.claimed.compareAndSet(false, true)) {
                    unsafeCompletePromise(taker.promise, a)
                    notifyEmptySpace = true
                  }
              }
            }
          }
          if (notifyEmptySpace) unsafeOnQueueEmptySpace(queue, takers)
        } finally {
          draining.set(false)
        }

        // We need to check in case someone added a putter or pulled from the queue since our last check
        // while we were still holding the lock
        if (!queue.isEmpty()) unsafeCompleteTakers(queue, takers)
      }

  }

  private object Strategy {

    final case class BackPressure[A]() extends Strategy[A] {
      private[this] val notifying = new AtomicBoolean(false)

      // A is an item to add
      // Promise[Nothing, Boolean] is the promise completing the whole offerAll
      // Boolean indicates if it's the last item to offer (promise should be completed once this item is added)
      private val putters = new ConcurrentDeque[(A, Promise[Nothing, Boolean], Boolean)]

      private def unsafeRemove(p: Promise[Nothing, Boolean]): Unit =
        putters.removeIf(_._2 eq p)

      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)

          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(queue, takers)
            if (isShutdown.get) ZIO.interrupt else p.await
          }.onInterrupt(ZIO.succeed(unsafeRemove(p)))
        }

      private def unsafeOffer(as: Iterable[A], p: Promise[Nothing, Boolean]): Unit = {
        val iterator = as.iterator
        var hasNext  = iterator.hasNext
        while (hasNext) {
          val a = iterator.next()
          hasNext = iterator.hasNext
          putters.offer((a, p, !hasNext))
        }
      }

      @tailrec
      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]]
      ): Unit = {
        val putters0 = putters
        if (!putters0.isEmpty && notifying.compareAndSet(false, true)) {
          var keepPolling = !queue.isFull()

          try {
            while (keepPolling) {
              val putter = putters0.poll()
              if (putter eq null) {
                keepPolling = false
                unsafeCompleteTakers(queue, takers)
              } else {
                val offered = queue.offer(putter._1)
                if (offered && putter._3)
                  putter._2.unsafe.done(Exit.`true`)(Unsafe.unsafe)
                else if (!offered) {
                  putters0.addFirst(putter)
                }
                if (!offered || queue.isFull()) {
                  unsafeCompleteTakers(queue, takers)
                  keepPolling = !queue.isFull()
                }
              }
            }
          } finally {
            notifying.set(false)
          }

          // We need to check in case someone added a putter or pulled from the queue since our last check
          // while we were still holding the lock
          if (!queue.isFull()) unsafeOnQueueEmptySpace(queue, takers)
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Unit = {
        var next = putters.poll()
        while (next ne null) {
          val (_, promise, isLast) = next
          if (isLast) promise.unsafe.interruptAs(fiberId)
          next = putters.poll()
        }
      }
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Unit = ()
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
        def unsafeSlidingOffer(as: Iterable[A]): Unit =
          if (!as.isEmpty && queue.capacity > 0) {
            val iterator = as.iterator
            var a        = iterator.next()
            var loop     = true
            val empty    = null.asInstanceOf[A]
            while (loop) {
              queue.poll(empty)
              val offered = queue.offer(a)
              if (offered && iterator.hasNext) {
                a = iterator.next()
              } else if (offered && !iterator.hasNext) {
                loop = false
              }
            }
          }

        ZIO.succeed {
          unsafeSlidingOffer(as)
          unsafeCompleteTakers(queue, takers)
          true
        }
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Taker[A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Unit = ()
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit =
    // Make promise completion uninterruptible to ensure it always completes
    p.unsafe.done(Exit.succeed(a))(Unsafe.unsafe)

  /**
   * Offer items to the queue
   */
  private def unsafeOfferAll[A, B <: A](q: MutableConcurrentQueue[A], as: Iterable[B]): Chunk[B] =
    q.offerAll(as)

  /**
   * Poll all items from the queue
   */
  private def unsafePollAll[A](q: MutableConcurrentQueue[A]): Chunk[A] =
    q.pollUpTo(Int.MaxValue)

  private def unsafePollAll[A <: AnyRef](q: ConcurrentDeque[Taker[A]]): Chunk[Taker[A]] = {
    val cb   = ChunkBuilder.make[Taker[A]](q.size)
    var loop = true
    while (loop) {
      val taker = q.poll()
      if (taker eq null) loop = false
      else cb.addOne(taker)
    }
    cb.result()
  }

  /**
   * Poll n items from the queue
   */
  private def unsafePollN[A](q: MutableConcurrentQueue[A], max: Int): Chunk[A] =
    q.pollUpTo(max)

  // Add these private helpers to disambiguate overloads
  private def unsafePollNDeque[A](q: ConcurrentDeque[Taker[A]], max: Int): Chunk[Taker[A]] = {
    val cb = ChunkBuilder.make[Taker[A]]()
    var i  = 0
    while (i < max) {
      val taker = q.poll()
      if (taker eq null) i = max
      else {
        cb.addOne(taker)
        i += 1
      }
    }
    cb.result()
  }
  private def unsafePollAllDeque[A](q: ConcurrentDeque[Taker[A]]): Chunk[Taker[A]] = {
    val cb   = ChunkBuilder.make[Taker[A]](q.size)
    var loop = true
    while (loop) {
      val taker = q.poll()
      if (taker eq null) loop = false
      else cb.addOne(taker)
    }
    cb.result()
  }

}
