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
    val takers       = MutableConcurrentQueue.unbounded[Promise[Nothing, A]]
    val putters      = MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]
    val shutdownFlag = new AtomicBoolean(false)
    val shutdownHook = new AtomicReference[UIO[Unit]](ZIO.unit)

    new Internal[A] {
      def awaitShutdown(implicit trace: Trace): UIO[Unit] =
        Promise.make[Nothing, Unit].flatMap { p =>
          ZIO.suspendSucceed {
            if (shutdownFlag.get()) ZIO.unit
            else
              shutdownHook.updateAndGet(hook =>
                hook.zipRight(p.succeed(()).unit)
              ) *> p.await
          }
        }

      def capacity: Int = queue.capacity

      def isShutdown(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed(shutdownFlag.get())

      def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val noRemaining =
              if (queue.isEmpty()) {
                val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
                if (taker ne null) {
                  unsafeCompletePromise(taker, a)
                  true
                } else false
              } else false

            if (noRemaining) Exit.`true`
            else if (queue.offer(a)) {
              unsafeCompleteTakers(strategy, queue, takers)
              Exit.`true`
            } else
              strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownFlag)
          }
        }

      def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else Chunk.empty
            val (forTakers, remaining) = as.splitAt(pTakers.size)
            (pTakers zip forTakers).foreach { case (taker, item) =>
              unsafeCompletePromise(taker, item)
            }

            if (remaining.isEmpty) Exit.emptyChunk
            else {
              val surplus = unsafeOfferAll(queue, remaining)

              unsafeCompleteTakers(strategy, queue, takers)

              if (surplus.isEmpty) Exit.emptyChunk
              else
                strategy.handleSurplus(surplus, queue, takers, shutdownFlag).map { offered =>
                  if (offered) Chunk.empty else surplus
                }
            }
          }
        }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.suspendSucceed {
            shutdownFlag.set(true)

            val hook = shutdownHook.getAndSet(ZIO.unit)

            ZIO
              .foreachDiscard(unsafePollAll(takers)) { p =>
                ZIO.succeed(p.unsafe.done(Exit.interrupt(fiberId))(Unsafe.unsafe))
              }
              .zipRight(
                ZIO.foreachDiscard(unsafePollAll(putters)) { case (_, p, lastItem) =>
                  if (lastItem) ZIO.succeed(p.unsafe.done(Exit.interrupt(fiberId))(Unsafe.unsafe))
                  else ZIO.unit
                }
              )
              .zipRight(strategy.shutdown)
              .zipRight(hook)
          }
        }

      def size(implicit trace: Trace): UIO[Int] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) ZIO.interrupt
          else
            ZIO.succeed(
              queue.size() - takers.size() + strategy.surplusSize
            )
        }

      def take(implicit trace: Trace): UIO[A] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.suspendSucceed {
            if (shutdownFlag.get()) ZIO.interrupt
            else {
              val item = queue.poll(null.asInstanceOf[A])
              if (item ne null) {
                strategy.unsafeOnQueueEmptySpace(queue, takers)
                Exit.succeed(item)
              } else {
                val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)
                ZIO.suspendSucceed {
                  takers.offer(p)
                  unsafeCompleteTakers(strategy, queue, takers)
                  if (shutdownFlag.get()) ZIO.interrupt else p.await
                }.onInterrupt(ZIO.succeed(takers.remove(p)))
              }
            }
          }
        }

      def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) ZIO.interrupt
          else {
            val as = unsafePollAll(queue)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          }
        }

      def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) ZIO.interrupt
          else {
            val as = unsafePollN(queue, max)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            Exit.succeed(as)
          }
        }
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Boolean =
    p.unsafe.done(Exit.succeed(a))(Unsafe.unsafe)

  private def unsafeCompleteTakers[A](
    strategy: Strategy[A],
    queue: MutableConcurrentQueue[A],
    takers: MutableConcurrentQueue[Promise[Nothing, A]]
  ): Unit = {
    var keepPolling = true
    while (keepPolling && !queue.isEmpty()) {
      val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
      if (taker eq null) keepPolling = false
      else {
        val a = queue.poll(null.asInstanceOf[A])
        if (a ne null) {
          unsafeCompletePromise(taker, a)
          strategy.unsafeOnQueueEmptySpace(queue, takers)
        } else {
          unsafeOfferAll(takers, Chunk.single(taker))
          keepPolling = false
        }
      }
    }
  }

  private def unsafeOfferAll[A](queue: MutableConcurrentQueue[A], as: Iterable[A]): Chunk[A] = {
    val bs = Chunk.fromIterable(as)
    @tailrec def offerAll(i: Int): Chunk[A] =
      if (i >= bs.size) Chunk.empty
      else if (queue.offer(bs(i))) offerAll(i + 1)
      else bs.drop(i)
    offerAll(0)
  }

  private def unsafePollAll[A](queue: MutableConcurrentQueue[A]): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    var a       = queue.poll(null.asInstanceOf[A])
    while (a ne null) {
      builder += a
      a = queue.poll(null.asInstanceOf[A])
    }
    builder.result()
  }

  private def unsafePollN[A](queue: MutableConcurrentQueue[A], max: Int): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    var i       = 0
    while (i < max) {
      val a = queue.poll(null.asInstanceOf[A])
      if (a ne null) {
        builder += a
        i += 1
      } else i = max
    }
    builder.result()
  }

  sealed abstract class Strategy[A] {
    def handleSurplus(
      as: Chunk[A],
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]],
      isShutdown: AtomicBoolean
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(implicit trace: Trace): UIO[Unit]
  }

  object Strategy {

    final class BackPressure[A] extends Strategy[A] {
      // A deque of waiters, each with an item to add and a promise to complete.
      private val putters =
        MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]

      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)
          ZIO.suspendSucceed {
            unsafeOffer(as, p)
            unsafeOnQueueEmptySpace(queue, takers)
            unsafeCompleteTakers(this, queue, takers)
            if (isShutdown.get()) ZIO.interrupt else p.await
          }.onInterrupt(ZIO.succeed(unsafeRemove(p)))
        }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        var keepPolling = true
        while (keepPolling) {
          val putter = putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)])
          if (putter eq null) keepPolling = false
          else {
            val offered = queue.offer(putter._1)
            if (offered || putter._3) {
              unsafeCompletePromise(putter._2, offered)
            } else if (!offered) {
              unsafeOfferAll(putters, Chunk.single(putter))
              keepPolling = false
            }
          }
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.succeed {
            var putter = putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)])
            while (putter ne null) {
              putter._2.unsafe.done(Exit.interrupt(fiberId))(Unsafe.unsafe)
              putter = putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)])
            }
          }
        }

      private def unsafeOffer(as: Chunk[A], p: Promise[Nothing, Boolean]): Unit = {
        val iterator = as.iterator
        var hasNext  = iterator.hasNext
        while (hasNext) {
          val a = iterator.next()
          hasNext = iterator.hasNext
          putters.offer((a, p, !hasNext))
        }
      }

      private def unsafeRemove(p: Promise[Nothing, Boolean]): Unit = {
        unsafeOfferAll(
          putters,
          unsafePollAll(putters).filter(_._2 ne p)
        )
        ()
      }
    }

    final class Dropping[A] extends Strategy[A] {
      // Drops the item. Whatever we were offering is not queued anywhere, so we just
      // lose it when the queue is full.
      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }

    final class Sliding[A] extends Strategy[A] {
      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
        @tailrec def unsafeSlidingOffer(as: Chunk[A]): Unit =
          if (as.isEmpty) ()
          else if (queue.capacity == 0) ()
          else if (queue.offer(as.head)) unsafeSlidingOffer(as.tail)
          else {
            queue.poll(null.asInstanceOf[A])
            unsafeSlidingOffer(as)
          }

        unsafeSlidingOffer(as)
        unsafeCompleteTakers(this, queue, takers)
        Exit.`true`
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def surplusSize: Int = 0

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }
}
