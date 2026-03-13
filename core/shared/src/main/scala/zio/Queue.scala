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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.annotation.tailrec

/**
 * A `Queue` is a lightweight, asynchronous queue into which values can be
 * enqueued and of which elements can be dequeued.
 */
sealed abstract class ZQueue[E, A] extends ZDequeue.Internal[E, A] with ZEnqueue.Internal[E, A] {

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
  private val interruptAsNone = ZIO.interruptAs(FiberId.None)(Trace.empty)

  private[zio] abstract class Internal[E, A] extends ZQueue[E, A]

  /**
   * Makes a new bounded queue. When the capacity of the queue is reached, any
   * additional calls to `offer` will be suspended until there is more room in
   * the queue.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def bounded[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberId.map(unsafe.bounded(requestedCapacity, _)(Unsafe.unsafe))

  /**
   * Makes a new bounded queue with the dropping strategy. When the capacity of
   * the queue is reached, new elements will be dropped.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def dropping[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberId.map(unsafe.dropping(requestedCapacity, _)(Unsafe.unsafe))

  /**
   * Makes a new bounded queue with sliding strategy. When the capacity of the
   * queue is reached, new elements will be added and the old elements will be
   * dropped.
   *
   * @note
   *   when possible use only power of 2 capacities; this will provide better
   *   performance by utilising an optimised version of the underlying
   *   [[zio.internal.RingBuffer]].
   *
   * @param requestedCapacity
   *   capacity of the `Queue`
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def sliding[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberId.map(unsafe.sliding(requestedCapacity, _)(Unsafe.unsafe))

  /**
   * Makes a new unbounded queue.
   *
   * @tparam A
   *   type of the `Queue`
   * @return
   *   `UIO[Queue[A]]`
   */
  def unbounded[A](implicit trace: Trace): UIO[Queue[A]] =
    ZIO.fiberId.map(unsafe.unbounded(_)(Unsafe.unsafe))

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
    strategy: Strategy[Nothing, A],
    fiberId: FiberId
  )(implicit unsafe: Unsafe): Queue[A] = {
    val p = Promise.unsafe.make[Nothing, Unit](fiberId)
    unsafeCreate(
      queue,
      new ConcurrentDeque[Promise[Nothing, A]],
      p,
      new AtomicReference[Cause[Nothing]](null),
      strategy
    )
  }

  private def unsafeCreate[E, A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[E, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownCause: AtomicReference[Cause[E]],
    strategy: Strategy[E, A]
  ): ZQueue[E, A] = new QueueImpl[E, A](queue, takers, shutdownHook, shutdownCause, strategy)

  private final class QueueImpl[E, A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[E, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownCause: AtomicReference[Cause[E]],
    strategy: Strategy[E, A]
  ) extends ZQueue[E, A] {

    override def capacity: Int = queue.capacity

    override def offer(a: A)(implicit trace: Trace): IO[E, Boolean] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null) ZIO.failCause(cause)
        else {
          if (tryOffer(a)) Exit.`true`
          else strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownCause)
        }
      }

    private def tryOffer(a: A): Boolean = {
      @tailrec def offeredToTaker(): Boolean = {
        val taker = takers.poll()
        if (taker eq null) false
        else if (unsafeCompletePromise(taker, a)) true
        else offeredToTaker()
      }

      val noRemaining = if (queue.isEmpty()) offeredToTaker() else false

      if (noRemaining) true
      else if (queue.offer(a)) {
        strategy.unsafeCompleteTakers(queue, takers)
        true
      } else false
    }

    override def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): IO[E, Chunk[A1]] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null) ZIO.failCause(cause)
        else {
          val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else Chunk.empty
          val (forTakers, remaining) = as.splitAt(pTakers.size)
          (pTakers zip forTakers).foreach { case (taker, item) =>
            unsafeCompletePromise(taker, item)
          }

          if (remaining.isEmpty) Exit.emptyChunk
          else {
            // not enough takers, offer to the queue
            val surplus = unsafeOfferAll(queue, remaining)

            if (surplus.isEmpty) {
              strategy.unsafeCompleteTakers(queue, takers)
              Exit.emptyChunk
            } else
              strategy.handleSurplus(surplus, queue, takers, shutdownCause).map { offered =>
                if (offered) Chunk.empty else surplus
              }
          }
        }
      }

    override def awaitShutdown(implicit trace: Trace): UIO[Unit] = shutdownHook.await

    override def size(implicit trace: Trace): UIO[Int] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null)
          ZIO.failCause(cause)
        else
          Exit.succeed(queue.size() - takers.size() + strategy.surplusSize)
      }

    override def shutdown(implicit trace: Trace): UIO[Unit] =
      ZIO.fiberIdWith { fiberId =>
        shutdownCause(Cause.interrupt(fiberId)).unit
      }.uninterruptible

    override def shutdownCause(cause: Cause[E])(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.fiberIdWith { fiberId =>
        if (shutdownCause.compareAndSet(null.asInstanceOf[Cause[E]], cause)) {
          implicit val unsafe: Unsafe = Unsafe
          shutdownHook.unsafe.succeedUnit
          val it = unsafePollAll(takers).iterator
          while (it.hasNext) {
            it.next().unsafe.done(Exit.failCause(cause))
          }
          strategy.shutdown(cause)
          Exit.succeed(unsafePollAll(queue))
        } else {
          Exit.emptyChunk
        }
      }.uninterruptible

    override def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(shutdownCause.get ne null)

    override def take(implicit trace: Trace): IO[E, A] =
      ZIO.uninterruptibleMask { restore =>
        ZIO.fiberIdWith { fiberId =>
          val cause = shutdownCause.get
          if (cause ne null) ZIO.failCause(cause)
          else {
            queue.poll(null.asInstanceOf[A]) match {
              case null =>
                // add the promise to takers, then:
                // - try take again in case a value was added since
                // - wait for the promise to be completed
                // - clean up resources in case of interruption
                val p = Promise.unsafe.make[E, A](fiberId)(Unsafe)

                takers.offer(p)
                strategy.unsafeCompleteTakers(queue, takers)
                restore(p.await).catchAllCause { c =>
                  val removed = p.unsafe.completeWith(ZIO.failCause(c))(Unsafe)
                  takers.remove(p)
                  if (removed) Exit.failCause(c)
                  else {
                    // The promise was already completed, so if we interrupt here we'll drop the item
                    // This is not ideal but instead of interrupting we recover temporarily.
                    // Interruption will resume at the next point where it's enabled
                    p.await
                  }
                }
              case item =>
                strategy.unsafeOnQueueEmptySpace(queue, takers)
                Exit.succeed(item)
            }
          }
        }
      }

    override def takeAll(implicit trace: Trace): IO[E, Chunk[A]] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null)
          ZIO.failCause(cause)
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

    override def takeUpTo(max: Int)(implicit trace: Trace): IO[E, Chunk[A]] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null)
          ZIO.failCause(cause)
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

    override def poll(implicit trace: Trace): IO[E, Option[A]] =
      ZIO.suspendSucceed {
        val cause = shutdownCause.get
        if (cause ne null)
          ZIO.failCause(cause)
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

  private sealed abstract class Strategy[E, A] {
    private[this] val draining = new AtomicBoolean(false)

    def handleSurplus(
      as: Iterable[A],
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[E, A]],
      shutdownCause: AtomicReference[Cause[E]]
    )(implicit trace: Trace): IO[E, Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[E, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(cause: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Unit

    @tailrec
    final def unsafeCompleteTakers(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[E, A]]
    ): Unit =
      if (!takers.isEmpty && draining.compareAndSet(false, true)) {
        try {
          var keepPolling      = !queue.isEmpty()
          val empty            = null.asInstanceOf[A]
          var notifyEmptySpace = false
          var currentItem      = empty
          while (keepPolling) {
            val taker = takers.poll()
            if (taker eq null) {
              keepPolling = false
              if (currentItem != null) queue.offer(currentItem)
            } else if (!taker.unsafe.isDone(Unsafe)) {
              if (currentItem == null) currentItem = queue.poll(empty)
              currentItem match {
                case null =>
                  takers.addFirst(taker)
                  keepPolling = false
                case a =>
                  if (unsafeCompletePromise(taker, a)) {
                    notifyEmptySpace = true
                    currentItem = empty
                  } else {
                    currentItem = a
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

    final case class BackPressure[E, A]() extends Strategy[E, A] {
      private[this] val notifying = new AtomicBoolean(false)

      // A is an item to add
      // Promise[E, Boolean] is the promise completing the whole offerAll
      // Boolean indicates if it's the last item to offer (promise should be completed once this item is added)
      private val putters = new ConcurrentDeque[(A, Promise[E, Boolean], Boolean)]

      private def unsafeRemove(p: Promise[E, Boolean]): Unit =
        putters.removeIf(_._2 eq p)

      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[E, A]],
        shutdownCause: AtomicReference[Cause[E]]
      )(implicit trace: Trace): IO[E, Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[E, Boolean](fiberId)(Unsafe.unsafe)

          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(queue, takers)
            val cause = shutdownCause.get
            if (cause ne null) ZIO.failCause(cause) else p.await
          }.onInterrupt(ZIO.succeed(unsafeRemove(p)))
        }

      private def unsafeOffer(as: Iterable[A], p: Promise[E, Boolean]): Unit = {
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
        takers: ConcurrentDeque[Promise[E, A]]
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

      def shutdown(cause: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Unit = {
        var next = putters.poll()
        while (next ne null) {
          val (_, promise, isLast) = next
          if (isLast) promise.unsafe.done(Exit.failCause(cause))
          next = putters.poll()
        }
      }
    }

    final case class Dropping[E, A]() extends Strategy[E, A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[E, A]],
        shutdownCause: AtomicReference[Cause[E]]
      )(implicit trace: Trace): IO[E, Boolean] = Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[E, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Unit = ()
    }

    final case class Sliding[E, A]() extends Strategy[E, A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[E, A]],
        shutdownCause: AtomicReference[Cause[E]]
      )(implicit trace: Trace): IO[E, Boolean] = {
        def unsafeSlidingOffer(as: Iterable[A]): Unit =
          if (!as.isEmpty && queue.capacity > 0) {
            val iterator = as.iterator
            while (iterator.hasNext) {
              val a = iterator.next()
              queue.poll(null.asInstanceOf[A])
              queue.offer(a)
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
        takers: ConcurrentDeque[Promise[E, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[E])(implicit trace: Trace, unsafe: Unsafe): Unit = ()
    }
  }

  private def unsafeCompletePromise[E, A](p: Promise[E, A], a: A): Boolean =
    p.unsafe.done(Exit.succeed(a))(Unsafe)

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

  private def unsafePollAll[A <: AnyRef](q: ConcurrentDeque[A]): Chunk[A] = {
    val cb   = ChunkBuilder.make[A](q.size)
    var loop = true
    while (loop) {
      val a = q.poll()
      if (a eq null) loop = false
      else cb.addOne(a)
    }
    cb.result()
  }

  /**
   * Poll n items from the queue
   */
  private def unsafePollN[A](q: MutableConcurrentQueue[A], max: Int): Chunk[A] =
    q.pollUpTo(max)

  /**
   * Poll n items from the queue
   */
  private def unsafePollN[A <: AnyRef](q: ConcurrentDeque[A], max: Int): Chunk[A] = {
    val cb = ChunkBuilder.make[A]()
    var i  = 0
    while (i < max) {
      val a = q.poll()
      if (a eq null) i = max
      else {
        cb.addOne(a)
        i += 1
      }
    }
    cb.result()
  }

}
