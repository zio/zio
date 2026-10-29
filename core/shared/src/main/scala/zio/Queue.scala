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

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
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

  /**
   * Shuts down the queue with the specified cause and returns any items
   * currently buffered in the queue. Any fibers that are suspended on `offer`
   * or `take` will be failed with the specified cause. Future calls to `offer*`
   * and `take*` will fail immediately with the specified cause.
   *
   * Note: If `offerAll` fails with `cause` during a concurrent `shutdownCause`,
   * any prefix of elements already enqueued before shutdown completed may be
   * returned by `shutdownCause` (or consumed by pre-existing takers); callers
   * must not assume no element was accepted when `offerAll` fails.
   */
  def shutdownCause(cause: Cause[Nothing])(implicit trace: Trace): UIO[Chunk[A]] =
    shutdown.as(Chunk.empty)
}

object Queue extends QueuePlatformSpecific {
  private val interruptAsNone = ZIO.interruptAs(FiberId.None)(Trace.empty)

  private[zio] abstract class Internal[A] extends Queue[A]

  private final class PollHazard extends AtomicReference[ShutdownState](null) {
    var p1: Long  = 0L
    var p2: Long  = 0L
    var p3: Long  = 0L
    var p4: Long  = 0L
    var p5: Long  = 0L
    var p6: Long  = 0L
    var p7: Long  = 0L
    var p8: Long  = 0L
    var p9: Long  = 0L
    var p10: Long = 0L
    var p11: Long = 0L
    var p12: Long = 0L
    var p13: Long = 0L
    var p14: Long = 0L
    var p15: Long = 0L
  }

  private val registeredHazards =
    new ConcurrentLinkedQueue[WeakReference[PollHazard]]()

  private val localPollHazard: ThreadLocal[PollHazard] =
    new ThreadLocal[PollHazard] {
      override def initialValue(): PollHazard = {
        val h = new PollHazard
        registeredHazards.add(new WeakReference(h))
        h
      }
    }

  private final class ShutdownState extends AtomicBoolean(false) {
    @volatile var cause: Cause[Nothing] = null
    private var drainFinished: Boolean  = false
    private var stragglers: List[Any]   = Nil

    def enterPoll(): PollHazard =
      if (get()) null
      else {
        val h = localPollHazard.get()
        h.set(this)
        if (!get()) h
        else {
          h.lazySet(null)
          null
        }
      }

    def beginShutdown(cause0: Cause[Nothing]): Boolean =
      synchronized {
        if (cause eq null) {
          cause = cause0
          set(true)
          true
        } else false
      }

    def completeDrain[A](queue: MutableConcurrentQueue[A], force: Boolean): Chunk[A] = {
      if (!force) {
        val it = registeredHazards.iterator()
        while (it.hasNext) {
          val ref = it.next()
          val h   = ref.get()
          if (h eq null) {
            it.remove()
          } else {
            var spins = 0
            while ((h.get() eq this) && spins < 16) {
              spins += 1
            }
            if (h.get() eq this) return null
          }
        }
      }
      synchronized {
        val items = unsafePollAll(queue)
        drainFinished = true
        items
      }
    }

    private def isSameStraggler(x: Any, y: Any): Boolean =
      (x.asInstanceOf[AnyRef] eq y.asInstanceOf[AnyRef]) || (
        x match {
          case _: java.lang.Number | _: java.lang.Boolean | _: java.lang.Character => x == y
          case _                                                                   => false
        }
      )

    private def removeStragglerItem(a: Any): Boolean = {
      var prev: List[Any] = Nil
      var curr: List[Any] = stragglers
      var found           = false
      while (!found && curr.nonEmpty) {
        if (isSameStraggler(curr.head, a)) {
          found = true
          stragglers = prev.reverse ::: curr.tail
        } else {
          prev = curr.head :: prev
          curr = curr.tail
        }
      }
      found
    }

    def isStraggler[A](a: A, queue: MutableConcurrentQueue[A]): Boolean =
      synchronized {
        if (!drainFinished) false
        else {
          val leftover = unsafePollAll(queue)
          if (leftover.nonEmpty) {
            stragglers = stragglers ++ leftover.toList
          }
          removeStragglerItem(a)
        }
      }

    def isAnyStraggler[A](as: Iterable[A], queue: MutableConcurrentQueue[A]): Boolean =
      synchronized {
        if (!drainFinished) false
        else {
          val leftover = unsafePollAll(queue)
          if (leftover.nonEmpty) {
            stragglers = stragglers ++ leftover.toList
          }
          var anyRemoved = false
          val it         = as.iterator
          while (it.hasNext) {
            if (removeStragglerItem(it.next())) anyRemoved = true
          }
          anyRemoved
        }
      }
  }

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
      new ShutdownState,
      strategy
    )
  }

  private def unsafeCreate[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Queue[A] = new QueueImpl[A](queue, takers, shutdownHook, shutdownFlag, strategy)

  private final class QueueImpl[A](
    queue: MutableConcurrentQueue[A],
    takers: ConcurrentDeque[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ) extends Queue[A] {

    private val shutdownState: ShutdownState = shutdownFlag.asInstanceOf[ShutdownState]

    private def shutdownFailure(implicit trace: Trace): UIO[Nothing] = {
      val cause = shutdownState.cause
      if (cause ne null) Exit.failCause(cause) else ZIO.interrupt
    }

    override def capacity: Int = queue.capacity

    override def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get) shutdownFailure
        else {
          val noRemaining =
            if (queue.isEmpty()) offerToTaker(a)
            else false

          if (noRemaining) Exit.`true`
          else if (queue.offer(a)) {
            strategy.unsafeCompleteTakers(queue, takers)
            if (!shutdownFlag.get || !shutdownState.isStraggler(a, queue)) Exit.`true`
            else shutdownFailure
          } else strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownFlag)
        }
      }

    @tailrec
    private[this] def offerToTaker(a: A): Boolean = {
      val taker = takers.poll()
      if (taker eq null) false
      else if (unsafeCompletePromise(taker, a)) true
      else offerToTaker(a)
    }

    override def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
      ZIO.suspendSucceed {
        if (shutdownFlag.get) shutdownFailure
        else {
          val remaining = if (queue.isEmpty()) as.dropWhile(offerToTaker) else as
          if (remaining.isEmpty) Exit.emptyChunk
          else {
            val surplus = unsafeOfferAll(queue, remaining)
            if (surplus.isEmpty) {
              strategy.unsafeCompleteTakers(queue, takers)
              if (shutdownFlag.get && shutdownState.isAnyStraggler(remaining, queue)) shutdownFailure
              else Exit.emptyChunk
            } else {
              if (shutdownFlag.get) {
                val enqueuedCount = remaining.size - surplus.length
                if (enqueuedCount > 0) {
                  val _ = shutdownState.isAnyStraggler(remaining.take(enqueuedCount), queue)
                }
              }
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
          shutdownFailure
        else
          Exit.succeed(queue.size() - takers.size() + strategy.surplusSize)
      }

    override def shutdown(implicit trace: Trace): UIO[Unit] =
      ZIO.fiberIdWith(fiberId => shutdownCause(Cause.interrupt(fiberId)).unit).uninterruptible

    override def shutdownCause(cause: Cause[Nothing])(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        if (shutdownState.beginShutdown(cause)) {
          implicit val unsafe: Unsafe = Unsafe
          val failExit                = Exit.failCause(cause)
          strategy.shutdown(cause)
          def drainTakers(): Unit = {
            val it = unsafePollAll(takers).iterator
            while (it.hasNext) {
              it.next().unsafe.done(failExit)
            }
          }
          drainTakers()
          def finishShutdown(retriesLeft: Int): UIO[Chunk[A]] = {
            val items = shutdownState.completeDrain(queue, force = retriesLeft <= 0)
            if (items ne null) {
              drainTakers()
              shutdownHook.unsafe.succeedUnit
              Exit.succeed(items)
            } else {
              ZIO.yieldNow *> ZIO.suspendSucceed {
                drainTakers()
                finishShutdown(retriesLeft - 1)
              }
            }
          }
          finishShutdown(1024)
        } else {
          awaitShutdown.as(Chunk.empty)
        }
      }.uninterruptible

    override def isShutdown(implicit trace: Trace): UIO[Boolean] = ZIO.succeed(shutdownFlag.get)

    private def completeShutdownTaker(p: Promise[Nothing, A], fiberId: FiberId): Unit = {
      val cause = shutdownState.cause
      val exit  = if (cause ne null) Exit.failCause(cause) else Exit.interrupt(fiberId)
      p.unsafe.done(exit)(Unsafe)
      takers.remove(p)
      ()
    }

    private def suspendTake(restore: ZIO.InterruptibilityRestorer, fiberId: FiberId)(implicit trace: Trace): UIO[A] =
      if (shutdownFlag.get) shutdownFailure
      else {
        // add the promise to takers, then:
        // - try take again in case a value was added since
        // - wait for the promise to be completed
        // - clean up resources in case of interruption
        val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe)

        takers.offer(p)
        strategy.unsafeCompleteTakers(queue, takers)
        if (shutdownFlag.get) completeShutdownTaker(p, fiberId)
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
      }

    override def take(implicit trace: Trace): UIO[A] =
      ZIO.uninterruptibleMask { restore =>
        ZIO.fiberIdWith { fiberId =>
          val h = shutdownState.enterPoll()
          if (h eq null) shutdownFailure
          else {
            val item =
              try queue.poll(null.asInstanceOf[A])
              finally h.lazySet(null)
            if (item != null) {
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              Exit.succeed(item)
            } else suspendTake(restore, fiberId)
          }
        }
      }

    override def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        val h = shutdownState.enterPoll()
        if (h eq null)
          shutdownFailure
        else {
          val as =
            try unsafePollAll(queue)
            finally h.lazySet(null)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          } else if (shutdownFlag.get) shutdownFailure
          else Exit.emptyChunk
        }
      }

    override def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
      ZIO.suspendSucceed {
        val h = shutdownState.enterPoll()
        if (h eq null)
          shutdownFailure
        else {
          val as =
            try unsafePollN(queue, max)
            finally h.lazySet(null)
          if (!as.isEmpty) {
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          } else if (shutdownFlag.get) shutdownFailure
          else Exit.emptyChunk
        }
      }

    override def poll(implicit trace: Trace): UIO[Option[A]] =
      ZIO.suspendSucceed {
        val h = shutdownState.enterPoll()
        if (h eq null)
          shutdownFailure
        else {
          val v =
            try queue.poll(null.asInstanceOf[A])
            finally h.lazySet(null)
          if (v != null) {
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(Some(v))
          } else if (shutdownFlag.get) shutdownFailure
          else Exit.none
        }
      }
  }

  private sealed abstract class Strategy[A] {
    private[this] val draining = new AtomicBoolean(false)

    protected final def lockDraining(): Unit =
      while (!draining.compareAndSet(false, true)) {}

    def handleSurplus(
      as: Iterable[A],
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[Nothing, A]],
      isShutdown: AtomicBoolean
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[Nothing, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(fiberId: FiberId)(implicit trace: Trace, unsafe: Unsafe): Unit =
      shutdown(Cause.interrupt(fiberId))

    def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Unit

    @tailrec
    final def unsafeCompleteTakers(
      queue: MutableConcurrentQueue[A],
      takers: ConcurrentDeque[Promise[Nothing, A]]
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

    final case class BackPressure[A]() extends Strategy[A] {
      private[this] val notifying = new AtomicBoolean(false)

      // A is an item to add
      // Promise[Nothing, Boolean] is the promise completing the whole offerAll
      // Boolean indicates if it's the last item to offer (promise should be completed once this item is added)
      private val putters = new ConcurrentDeque[(A, Promise[Nothing, Boolean], Boolean)]

      private def unsafeRemove(p: Promise[Nothing, Boolean]): Unit =
        putters.removeIf(_._2 eq p)

      private def completeShutdownPutter(
        p: Promise[Nothing, Boolean],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
        val shutdownState = isShutdown.asInstanceOf[ShutdownState]
        val cause         = shutdownState.cause
        val exit          = if (cause ne null) Exit.failCause(cause) else Exit.interrupt(FiberId.None)
        unsafeRemove(p)
        p.unsafe.done(exit)(Unsafe.unsafe)
        p.await
      }

      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)

          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(queue, takers)
            if (isShutdown.get) completeShutdownPutter(p, isShutdown)
            else p.await
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
        takers: ConcurrentDeque[Promise[Nothing, A]]
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
              } else if (!putter._2.unsafe.isDone(Unsafe.unsafe)) {
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

      private def drainPutters(failExit: Exit[Nothing, Boolean])(implicit unsafe: Unsafe): Unit = {
        var next = putters.poll()
        while (next ne null) {
          val (_, promise, _) = next
          promise.unsafe.done(failExit)
          next = putters.poll()
        }
      }

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Unit = {
        val failExit = Exit.failCause(cause)
        drainPutters(failExit)
        while (!notifying.compareAndSet(false, true)) {
          drainPutters(failExit)
        }
        drainPutters(failExit)
        lockDraining()
        drainPutters(failExit)
      }
    }

    final case class Dropping[A]() extends Strategy[A] {
      // do nothing, drop the surplus
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        if (isShutdown.get) {
          val cause = isShutdown.asInstanceOf[ShutdownState].cause
          if (cause ne null) Exit.failCause(cause) else ZIO.interrupt
        } else Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Unit =
        lockDraining()
    }

    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(
        as: Iterable[A],
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]],
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

        ZIO.suspendSucceed {
          val shutdownState = isShutdown.asInstanceOf[ShutdownState]
          val h             = shutdownState.enterPoll()
          if (h eq null) {
            val cause = shutdownState.cause
            if (cause ne null) Exit.failCause(cause) else ZIO.interrupt
          } else {
            try unsafeSlidingOffer(as)
            finally h.lazySet(null)
            unsafeCompleteTakers(queue, takers)
            if (!shutdownState.get || !shutdownState.isAnyStraggler(as, queue)) Exit.`true`
            else {
              val cause = shutdownState.cause
              if (cause ne null) Exit.failCause(cause) else ZIO.interrupt
            }
          }
        }
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: ConcurrentDeque[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(cause: Cause[Nothing])(implicit trace: Trace, unsafe: Unsafe): Unit =
        lockDraining()
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

}
