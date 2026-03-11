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
    val takers  = MutableConcurrentQueue.unbounded[Promise[Nothing, A]]
    val putters = MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]
    val shutdownFlag    = new AtomicBoolean(false)
    val shutdownHook    = new AtomicReference(UIO.unit)

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

      def offer(a: A)(implicit trace: Trace): IO[Nothing, Boolean] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val noRemaining =
              if (queue.offer(a)) {
                val taker = takers.poll(null.asInstanceOf[Promise[Nothing, A]])
                if (taker ne null) unsafePollQueue(queue).foreach(unsafeCompletePromise(taker, _))
                true
              } else false

            if (noRemaining) Exit.`true`
            else
              strategy match {
                case _: Strategy.BackPressure[_] =>
                  for {
                    p <- Promise.make[Nothing, Boolean]
                    _ <- ZIO.succeed {
                           putters.offer((a, p, false))
                           unsafeCompleteTakers(strategy, queue, takers)
                         }
                    _ <- ZIO.yieldNow
                    b <- p.await
                  } yield b
                case _: Strategy.Dropping[_] => Exit.`false`
                case _: Strategy.Sliding[_] =>
                  ZIO.succeed {
                    val _ = queue.offer(a)
                    true
                  }
              }
          }
        }

      def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): IO[Nothing, Chunk[A1]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            val pTakers                = if (queue.isEmpty()) unsafePollN(takers, as.size) else Chunk.empty
            val (forTakers, remaining) = as.splitAt(pTakers.size)
            (pTakers zip forTakers).foreach { case (taker, elem) =>
              unsafeCompletePromise(taker, elem)
            }

            if (remaining.isEmpty) Exit.emptyChunk
            else {
              val surplus = unsafeOfferAll(queue, remaining.toList)
              unsafeCompleteTakers(strategy, queue, takers)
              if (surplus.isEmpty) Exit.emptyChunk
              else
                strategy match {
                  case _: Strategy.BackPressure[_] =>
                    for {
                      p <- Promise.make[Nothing, Boolean]
                      _ <- ZIO.succeed {
                             surplus.foreach(putters.offer((_, p, false)))
                             unsafeCompleteTakers(strategy, queue, takers)
                           }
                      _ <- ZIO.yieldNow
                      _ <- p.await
                    } yield Chunk.fromIterable(surplus)
                  case _: Strategy.Dropping[_] => Exit.succeed(Chunk.fromIterable(surplus))
                  case _: Strategy.Sliding[_]  => Exit.emptyChunk
                }
            }
          }
        }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberId.flatMap { fiberId =>
          ZIO.suspendSucceed {
            shutdownFlag.set(true)

            ZIO
              .whenZIO(ZIO.succeed(shutdownHook.getAndSet(ZIO.unit) ne ZIO.unit))(
                shutdownHook.get()
              )
              .unit *>
              ZIO.foreachDiscard(unsafePollAll(takers))(_.interruptAs(fiberId)) *>
              strategy.shutdown *>
              ZIO.foreachDiscard(unsafePollAll(putters)) { case (_, p, _) =>
                p.interruptAs(fiberId)
              }
          }
        }

      def size(implicit trace: Trace): UIO[Int] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else
            ZIO.succeed(queue.size() - takers.size() + putters.size())
        }

      def take(implicit trace: Trace): IO[Nothing, A] =
        ZIO.fiberId.flatMap { fiberId =>
          ZIO.suspendSucceed {
            if (shutdownFlag.get()) interruptAsNone
            else {
              queue.poll(null.asInstanceOf[A]) match {
                case null =>
                  val p = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe.unsafe)
                  ZIO.suspendSucceed {
                    takers.offer(p)
                    unsafeCompleteTakers(strategy, queue, takers)
                    if (shutdownFlag.get()) interruptAsNone
                    else
                      p.await
                  }
                case polled =>
                  strategy.unsafeOnQueueEmptySpace(queue, takers)
                  Exit.succeed(polled)
              }
            }
          }
        }

      def takeAll(implicit trace: Trace): IO[Nothing, Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            ZIO.succeed {
              val as = unsafePollAll(queue)
              strategy.unsafeOnQueueEmptySpace(queue, takers)
              as
            }
          }
        }

      def takeBetween(min: Int, max: Int)(implicit trace: Trace): IO[Nothing, Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            @tailrec
            def takeRemaining(acc: Chunk[A], max: Int): ZIO[Any, Nothing, Chunk[A]] =
              if (max <= 0) ZIO.succeed(acc)
              else
                queue.poll(null.asInstanceOf[A]) match {
                  case null => ZIO.succeed(acc)
                  case a    => takeRemaining(acc :+ a, max - 1)
                }

            val as = unsafePollN(queue, max)

            if (as.size >= min) ZIO.succeed(as)
            else {
              def takeRest(acc: Chunk[A]): ZIO[Any, Nothing, Chunk[A]] =
                if (acc.size >= min) ZIO.succeed(acc)
                else {
                  val p = Promise.unsafe.make[Nothing, A](FiberId.None)(Unsafe.unsafe)
                  ZIO.suspendSucceed {
                    takers.offer(p)
                    unsafeCompleteTakers(strategy, queue, takers)
                    if (shutdownFlag.get()) interruptAsNone
                    else p.await.flatMap(a => takeRest(acc :+ a))
                  }
                }

              takeRest(as)
            }
          }
        }

      def takeUpTo(max: Int)(implicit trace: Trace): IO[Nothing, Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get()) interruptAsNone
          else {
            queue.poll(null.asInstanceOf[A]) match {
              case null => Exit.none
              case polled =>
                strategy.unsafeOnQueueEmptySpace(queue, takers)
                Exit.succeed(Some(polled))
            }
          }
        }
    }
  }

  private def unsafeCompletePromise[A](p: Promise[Nothing, A], a: A): Unit = {
    val _ = p.unsafe.done(Exit.succeed(a))(Unsafe.unsafe)
  }

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
        unsafePollQueue(queue).foreach(unsafeCompletePromise(taker, _))
        strategy.unsafeOnQueueEmptySpace(queue, takers)
      }
    }
  }

  private def unsafeOfferAll[A](queue: MutableConcurrentQueue[A], as: List[A]): List[A] = {
    @tailrec
    def go(as: List[A]): List[A] =
      as match {
        case Nil          => Nil
        case head :: tail => if (queue.offer(head)) go(tail) else as
      }

    go(as)
  }

  private def unsafePollAll[A](queue: MutableConcurrentQueue[A]): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    var polling = true

    while (polling) {
      val elem = queue.poll(null.asInstanceOf[A])
      if (elem eq null) polling = false
      else builder += elem
    }

    builder.result()
  }

  private def unsafePollN[A](queue: MutableConcurrentQueue[A], max: Int): Chunk[A] = {
    val builder = ChunkBuilder.make[A]()
    var i       = 0

    while (i < max) {
      val elem = queue.poll(null.asInstanceOf[A])
      if (elem eq null) i = max
      else {
        builder += elem
        i += 1
      }
    }

    builder.result()
  }

  private def unsafePollQueue[A](queue: MutableConcurrentQueue[A]): Option[A] = {
    val elem = queue.poll(null.asInstanceOf[A])
    if (elem eq null) None else Some(elem)
  }

  private sealed abstract class Strategy[A] {
    def unsafeOnQueueEmptySpace(
      queue: MutableConcurrentQueue[A],
      takers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit

    def shutdown: UIO[Unit]
  }

  private object Strategy {
    final class BackPressure[A] extends Strategy[A] {
      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def shutdown: UIO[Unit] = ZIO.unit
    }

    object BackPressure {
      def apply[A](): BackPressure[A] = new BackPressure[A]
    }

    final class Dropping[A] extends Strategy[A] {
      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def shutdown: UIO[Unit] = ZIO.unit
    }

    object Dropping {
      def apply[A](): Dropping[A] = new Dropping[A]
    }

    final class Sliding[A] extends Strategy[A] {
      def unsafeOnQueueEmptySpace(
        queue: MutableConcurrentQueue[A],
        takers: MutableConcurrentQueue[Promise[Nothing, A]]
      ): Unit = ()

      def shutdown: UIO[Unit] = ZIO.unit
    }

    object Sliding {
      def apply[A](): Sliding[A] = new Sliding[A]
    }
  }

}
