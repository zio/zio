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
            else {
              if (queue.offer(a)) {
                unsafeCompleteTakers(strategy, queue, takers)
                Exit.`true`
              } else strategy.handleSurplus(Chunk.single(a), queue, takers, shutdownFlag)
            }
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
            if (shutdownFlag.compareAndSet(false, true)) {
              val hook = shutdownHook.getAndSet(ZIO.unit)
              unsafeCompletePromises(takers)
              strategy.shutdown
              hook
            } else ZIO.unit
          }
        }

      def size(implicit trace: Trace): UIO[Int] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else ZIO.succeed(queue.size() - takers.size() + strategy.surplusSize)
        }

      def take(implicit trace: Trace): UIO[A] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.suspendSucceed {
            if (shutdownFlag.get()) interruptAsNone
            else {
              unsafePollQueue(queue) match {
                case Some(a) =>
                  strategy.unsafeOnQueueEmptySpace(queue, takers)
                  ZIO.succeed(a)
                case None =>
                  val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)
                  ZIO.suspendSucceed {
                    takers.offer(p)
                    strategy.unsafeOnQueueEmptySpace(queue, takers)
                    unsafeCompleteTakers(strategy, queue, takers)
                    if (shutdownFlag.get()) {
                      unsafeRemove(takers, p)
                      interruptAsNone
                    } else p.await
                  }
              }
            }
          }
        }

      def takeAll(implicit trace: Trace): UIO[Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val as = unsafePollAll(queue)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            ZIO.succeed(as)
          }
        }

      def takeUpTo(max: Int)(implicit trace: Trace): UIO[Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val as = unsafePollN(queue, max)
            strategy.unsafeOnQueueEmptySpace(queue, takers)
            ZIO.succeed(as)
          }
        }
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Boolean =
    p.unsafe.done(Exit.succeed(a))(Unsafe.unsafe)

  private def unsafeCompletePromises[A](takers: MutableConcurrentQueue[Promise[Nothing, A]]): Unit = {
    var taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
    while (taker ne null) {
      taker.unsafe.done(Exit.interrupt(FiberId.None))(Unsafe.unsafe)
      taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
    }
  }

  private def unsafeCompleteTakers[A](
    strategy: Strategy[A],
    queue: MutableConcurrentQueue[A],
    takers: MutableConcurrentQueue[Promise[Nothing, A]]
  ): Unit =
    strategy.unsafeCompleteTakers(queue, takers)

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
    var n       = 0
    while (n < max) {
      val a = queue.poll(null.asInstanceOf[A])
      if (a eq null) n = max
      else {
        builder += a
        n += 1
      }
    }
    builder.result()
  }

  private def unsafeOfferAll[A](queue: MutableConcurrentQueue[A], as: Iterable[A]): Chunk[A] = {
    val builder  = ChunkBuilder.make[A]()
    val iterator = as.iterator
    while (iterator.hasNext) {
      val a = iterator.next()
      if (!queue.offer(a)) {
        builder += a
        while (iterator.hasNext) builder += iterator.next()
      }
    }
    builder.result()
  }

  private def unsafePollQueue[A](queue: MutableConcurrentQueue[A]): Option[A] = {
    val a = queue.poll(null.asInstanceOf[A])
    if (a eq null) None else Some(a)
  }

  private def unsafeRemove[A](queue: MutableConcurrentQueue[A], a: A): Unit = {
    unsafePollAll(queue).foreach { elem =>
      if (elem != a) queue.offer(elem)
    }
  }

  private[zio] sealed abstract class Strategy[A] {
    def handleSurplus(
      as: Chunk[A],
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]],
      shutdownFlag: AtomicBoolean
    )(implicit trace: Trace): UIO[Boolean]

    def unsafeCompleteTakers(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def surplusSize: Int

    def shutdown(implicit trace: Trace): UIO[Unit]
  }

  private[zio] object Strategy {

    final case class BackPressure[A]() extends Strategy[A] {
      private val putters = MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]

      private def unsafeRemove(p: Promise[Nothing, Boolean]): Unit = {
        val as = unsafePollAll(putters)
        as.foreach { case (a, promise, last) =>
          if (promise ne p) putters.offer((a, promise, last))
        }
      }

      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        shutdownFlag: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.suspendSucceed {
            val p = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe.unsafe)

            ZIO.suspendSucceed {
              unsafeOffer(as, p)
              unsafeOnQueueEmptySpace(queue, takers)
              unsafeCompleteTakers(queue, takers)
              if (shutdownFlag.get()) ZIO.interrupt
              else p.await
            }.onInterrupt(ZIO.succeed(unsafeRemove(p)))
          }
        }

      private def unsafeOffer(as: Chunk[A], p: Promise[Nothing, Boolean]): Unit = {
        val iterator = as.iterator
        while (iterator.hasNext) {
          val a    = iterator.next()
          val last = !iterator.hasNext
          putters.offer((a, p, last))
        }
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        var keepPolling = true
        while (keepPolling && !queue.isFull()) {
          val putter = putters.poll(null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)])
          if (putter eq null) keepPolling = false
          else {
            val offered = queue.offer(putter._1)
            if (offered && putter._3) putter._2.unsafe.done(Exit.succeed(true))(Unsafe.unsafe)
            else if (!offered) {
              unsafeOfferAll(putters, Chunk(putter) ++ unsafePollAll(putters))
              keepPolling = false
            }
          }
        }
      }

      def unsafeCompleteTakers(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        var keepPolling = true
        while (keepPolling && !queue.isEmpty()) {
          val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
          if (taker eq null) keepPolling = false
          else {
            unsafePollQueue(queue).foreach(unsafeCompletePromise(taker, _))
            unsafeOnQueueEmptySpace(queue, takers)
          }
        }
      }

      def surplusSize: Int = putters.size()

      def shutdown(implicit trace: Trace): UIO[Unit] = {
        val as = unsafePollAll(putters)
        ZIO.foreachDiscard(as) { case (_, p, last) =>
          if (last) p.interrupt else ZIO.unit
        }
      }
    }

    final case class Dropping[A]() extends Strategy[A] {
      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        shutdownFlag: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        Exit.`false`

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def unsafeCompleteTakers(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        var keepPolling = true
        while (keepPolling && !queue.isEmpty()) {
          val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
          if (taker eq null) keepPolling = false
          else unsafePollQueue(queue).foreach(unsafeCompletePromise(taker, _))
        }
      }

      def surplusSize: Int = 0

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }

    final case class Sliding[A]() extends Strategy[A] {
      private def unsafeSlidingOffer(queue: MutableConcurrentQueue[A], as: Chunk[A]): Unit = {
        val iterator = as.iterator
        while (iterator.hasNext) {
          val a = iterator.next()
          if (!queue.offer(a)) {
            val _ = queue.poll(null.asInstanceOf[A])
            queue.offer(a)
          }
        }
      }

      def handleSurplus(
        as: Chunk[A],
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]],
        shutdownFlag: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
        unsafeSlidingOffer(queue, as)
        unsafeCompleteTakers(queue, takers)
        Exit.`true`
      }

      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def unsafeCompleteTakers(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = {
        var keepPolling = true
        while (keepPolling && !queue.isEmpty()) {
          val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
          if (taker eq null) keepPolling = false
          else unsafePollQueue(queue).foreach(unsafeCompletePromise(taker, _))
        }
      }

      def surplusSize: Int = 0

      def shutdown(implicit trace: Trace): UIO[Unit] = ZIO.unit
    }
  }
}
