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
sealed abstract class Queue[A] extends Dequeue.Internal[A] with Enqueue.Internal[A] {

  /**
   * Shuts down the queue with the specified cause. Pending and future
   * operations will fail with the winning cause and the winning caller receives
   * the values that were still buffered in the queue.
   */
  def shutdownCause(cause: Cause[Nothing])(implicit trace: Trace): UIO[Chunk[A]]

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
  private val DefaultShutdown = new AnyRef
  private val interruptAsNone = ZIO.interruptAs(FiberId.None)(Trace.empty)

  private[zio] abstract class Internal[A] extends Queue[A]

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
    strategy: Strategy[A],
    fiberId: FiberId
  )(implicit unsafe: Unsafe): Queue[A] = {
    val p = Promise.unsafe.make[Nothing, Unit](fiberId)
    unsafeCreate(
      queue,
      new ConcurrentDeque[Promise[Nothing, A]],
      p,
      new AtomicReference[AnyRef](null),
      strategy
    )
  }

  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownState: AtomicReference[AnyRef],
    strategy: Strategy[A]
  ): Queue[A] = new QueueImpl[A](queue, takers, shutdownHook, shutdownState, strategy)

  private final class QueueImpl[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownState: AtomicReference[AnyRef],
    strategy: Strategy[A]
  ) extends Queue[A] {

    private def shutdownEffect(state: AnyRef)(implicit trace: Trace): UIO[Nothing] =
      if (state eq DefaultShutdown) ZIO.interrupt
      else ZIO.refailCause(state.asInstanceOf[Cause[Nothing]])

    private def shutdownCauseOrNull: Cause[Nothing] =
      shutdownState.get() match {
        case null | DefaultShutdown => null
        case cause                  => cause.asInstanceOf[Cause[Nothing]]
      }

    private def shutdownStateOrNull: AnyRef =
      shutdownState.get()

    override def capacity: Int = queue.capacity

    override def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null) shutdownEffect(state)
        else {
          if (tryOffer(a)) Exit.`true`
          else strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownState)
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
        strategy.unsafeCompleteTakers(queue, takers, shutdownState)
        true
      } else false
    }

    override def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null) shutdownEffect(state)
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
              strategy.unsafeCompleteTakers(queue, takers, shutdownState)
              Exit.emptyChunk
            } else
              strategy.handleSurplus(surplus, queue, takers, shutdownState).map { offered =>
                if (offered) Chunk.empty else surplus
              }
          }
        }
      }

    override def awaitShutdown(implicit trace: Trace): UIO[Unit] = shutdownHook.await

    override def size(implicit trace: Trace): UIO[Int] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null)
          shutdownEffect(state)
        else
          Exit.succeed(queue.size() - takers.size() + strategy.surplusSize)
      }

    override def shutdown(implicit trace: Trace): UIO[Unit] =
      ZIO.fiberIdWith { fiberId =>
        if (shutdownState.compareAndSet(null, DefaultShutdown)) {
          implicit val unsafe: Unsafe = Unsafe
          shutdownHook.unsafe.succeedUnit

          val takerIterator = unsafePollAll(takers).iterator
          while (takerIterator.hasNext) {
            takerIterator.next().unsafe.interruptAs(fiberId)
          }

          strategy.shutdown(Cause.interrupt(fiberId))
          Exit.unit
        } else {
          val state = shutdownStateOrNull
          if (state eq DefaultShutdown) Exit.unit
          else shutdownEffect(state)
        }
      }.uninterruptible

    override def shutdownCause(cause: Cause[Nothing])(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        val existing = shutdownStateOrNull
        if (existing ne null) {
          shutdownEffect(existing)
        } else if (shutdownState.compareAndSet(null, cause)) {
          implicit val unsafe: Unsafe = Unsafe
          shutdownHook.unsafe.succeedUnit

          val takerIterator = unsafePollAll(takers).iterator
          while (takerIterator.hasNext) {
            takerIterator.next().unsafe.refailCause(cause)
          }

          val surplus  = strategy.shutdown(cause)
          val buffered = unsafePollAll(queue)

          Exit.succeed(buffered ++ surplus)
        } else {
          shutdownEffect(shutdownStateOrNull)
        }
      }.uninterruptible

    override def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(shutdownStateOrNull ne null)

    override def take(implicit trace: Trace): UIO[A] =
      ZIO.uninterruptibleMask { restore =>
        ZIO.fiberIdWith { fiberId =>
          val state = shutdownStateOrNull
          if (state ne null) shutdownEffect(state)
          else {
            queue.poll(null.asInstanceOf[A]) match {
              case null =>
                // add the promise to takers, then:
                // - try take again in case a value was added since
                // - wait for the promise to be completed
                // - clean up resources in case of interruption
                val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe)

                takers.offer(p)
                strategy.unsafeCompleteTakers(queue, takers, shutdownState)
                restore(p.await).catchAllCause { c =>
                  val removed = p.unsafe.completeWith(interruptAsNone)(Unsafe)
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
                strategy.unsafeOnQueueEmptySpace(queue, takers, shutdownState)
                Exit.succeed(item)
            }
          }
        }
      }

    override def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null)
          shutdownEffect(state)
        else {
          val as = unsafePollAll(queue)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers, shutdownState)
            Exit.succeed(as)
          } else {
            Exit.emptyChunk
          }
        }
      }

    override def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null)
          shutdownEffect(state)
        else {
          val as = unsafePollN(queue, max)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers, shutdownState)
            Exit.succeed(as)
          } else {
            Exit.emptyChunk
          }
        }
      }

    override def poll(implicit trace: Trace): UIO[Option[A]] =
      ZIO.suspendSucceed {
        val state = shutdownStateOrNull
        if (state ne null)
          shutdownEffect(state)
        else {
          queue.poll(null.asInstanceOf[A]) match {
            case null => Exit.none
            case v =>
              strategy.unsafeOnQueueEmptySpace(queue, takers, shutdownState)
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
      takers: ConcurrentDeque[Promise[Nothing, A]],
      shutdownState: AtomicReference[AnyRef]
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[Nothing, A]],
      shutdownState: AtomicReference[AnyRef]
    ): Unit

    def surplusSize: Int

    def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Chunk[A]

    @tailrec
    final def unsafeCompleteTakers(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[Nothing, A]],
      shutdownState: AtomicReference[AnyRef]
    ): Unit =
      if ((shutdownState.get eq null) && !takers.isEmpty && draining.compareAndSet(false, true)) {
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
          if (notifyEmptySpace) unsafeOnQueueEmptySpace(queue, takers, shutdownState)
        } finally {
          draining.set(false)
        }

        // We need to check in case someone added a putter or pulled from the queue since our last check
        // while we were still holding the lock
        if ((shutdownState.get eq null) && !queue.isEmpty()) unsafeCompleteTakers(queue, takers, shutdownState)
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
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)

          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers, shutdownState)
            unsafeCompleteTakers(queue, takers, shutdownState)
            shutdownState.get() match {
              case null            => p.await
              case DefaultShutdown => ZIO.interrupt
              case cause           => ZIO.refailCause(cause.asInstanceOf[Cause[Nothing]])
            }
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
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
      ): Unit = {
        val putters0 = putters
        if ((shutdownState.get eq null) && !putters0.isEmpty && notifying.compareAndSet(false, true)) {
          var keepPolling = !queue.isFull()

          try {
            while (keepPolling && (shutdownState.get eq null)) {
              val putter = putters0.poll()
              if (putter eq null) {
                keepPolling = false
                unsafeCompleteTakers(queue, takers, shutdownState)
              } else if (shutdownState.get ne null) {
                putters0.addFirst(putter)
                keepPolling = false
              } else {
                val offered = queue.offer(putter._1)
                if (offered && putter._3)
                  putter._2.unsafe.done(Exit.`true`)(Unsafe.unsafe)
                else if (!offered) {
                  putters0.addFirst(putter)
                }
                if (!offered || queue.isFull()) {
                  unsafeCompleteTakers(queue, takers, shutdownState)
                  keepPolling = !queue.isFull()
                }
              }
            }
          } finally {
            notifying.set(false)
          }

          // We need to check in case someone added a putter or pulled from the queue since our last check
          // while we were still holding the lock
          if ((shutdownState.get eq null) && !queue.isFull()) unsafeOnQueueEmptySpace(queue, takers, shutdownState)
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Chunk[A] = {
        val items = ChunkBuilder.make[A](putters.size())
        var next  = putters.poll()
        while (next ne null) {
          val (value, promise, isLast) = next
          items.addOne(value)
          if (isLast) promise.unsafe.refailCause(cause)
          next = putters.poll()
        }
        items.result()
      }
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
      )(implicit trace: Trace): UIO[Boolean] = Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Chunk[A] = Chunk.empty
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
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
          unsafeCompleteTakers(queue, takers, shutdownState)
          true
        }
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        shutdownState: AtomicReference[AnyRef]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Chunk[A] = Chunk.empty
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Boolean =
    p.unsafe.completeWith(Exit.succeed(a))(Unsafe)

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
