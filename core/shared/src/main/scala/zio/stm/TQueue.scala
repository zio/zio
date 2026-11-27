/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

package zio.stm

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.immutable.{Queue => ScalaQueue}

/**
 * A `TQueue` is a transactional first-in, first-out queue. Its operations are
 * STM transactions, so an offer or a take can be combined with changes to other
 * transactional data structures and commit together. Use it instead of `Queue`
 * when queue operations are part of a larger transaction.
 *
 * A take on an empty queue retries the transaction, so the fiber suspends until
 * a value arrives. What an offer does on a full queue depends on the
 * constructor: `bounded` waits for room, `dropping` discards the new value, and
 * `sliding` discards the oldest value.
 *
 * {{{
 * import zio._
 *
 * val program: UIO[(Int, Chunk[Int])] =
 *   for {
 *     queue <- TQueue.bounded[Int](4).commit
 *     _     <- queue.offerAll(List(1, 2, 3)).commit
 *     first <- queue.take.commit
 *     rest  <- queue.takeAll.commit
 *   } yield (first, rest)
 * // (1,Chunk(2,3))
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stm/tqueue/ TQueue]]
 */
sealed trait TQueue[A] extends TDequeue.Internal[A] with TEnqueue.Internal[A] {

  override final def awaitShutdown: USTM[Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Checks if the queue is empty.
   */
  override final def isEmpty: USTM[Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the queue is at capacity.
   */
  override final def isFull: USTM[Boolean] =
    size.map(_ == capacity)

  /**
   * Views all elements in the queue, oldest first, without removing them.
   */
  def peekAll: ZSTM[Any, Nothing, Chunk[A]] = takeAll.tap(offerAll(_))

}

/**
 * Constructors for [[TQueue]]: `bounded`, `dropping`, `sliding` and
 * `unbounded`.
 */
object TQueue {

  /**
   * Creates a bounded queue with the back pressure strategy. The queue keeps
   * values until they are taken. When it is full, `offer` retries, so the
   * offering fiber suspends until a take makes room. The capacity is not
   * checked: with a capacity of 0 or less, `offer` never completes.
   */
  def bounded[A](requestedCapacity: => Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.BackPressure)

  /**
   * Creates a bounded queue with the dropping strategy. When the queue is full,
   * `offer` discards the new value and succeeds with `false`.
   */
  def dropping[A](requestedCapacity: => Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.Dropping)

  /**
   * Creates a bounded queue with the sliding strategy. When the queue is full,
   * `offer` drops the oldest value, adds the new one and succeeds with `true`.
   * With a capacity of 0 or less, `offer` discards the new value and succeeds
   * with `true`.
   */
  def sliding[A](requestedCapacity: => Int): USTM[TQueue[A]] =
    makeQueue(requestedCapacity, Strategy.Sliding)

  /**
   * Creates an unbounded queue: its capacity is `Int.MaxValue`, so `offer` does
   * not wait for room.
   */
  def unbounded[A]: USTM[TQueue[A]] =
    makeQueue(Int.MaxValue, Strategy.Dropping)

  /**
   * Creates a queue with the specified strategy.
   */
  private def makeQueue[A](requestedCapacity: => Int, strategy: => Strategy): USTM[TQueue[A]] =
    TRef.make[ScalaQueue[A]](ScalaQueue.empty).map { ref =>
      unsafeMakeQueue(ref, requestedCapacity, strategy)
    }

  /**
   * Unsafely creates a queue with the specified strategy.
   */
  private def unsafeMakeQueue[A](
    ref: TRef[ScalaQueue[A]],
    requestedCapacity: Int,
    strategy: Strategy
  ): TQueue[A] =
    new TQueue[A] {
      val capacity: Int =
        requestedCapacity
      val isShutdown: USTM[Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val queue = ref.unsafeGet(journal)
          queue eq null
        }
      def offer(a: A): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else if (queue.size < capacity) {
            ref.unsafeSet(journal, queue.enqueue(a))
            true
          } else
            strategy match {
              case Strategy.BackPressure => throw ZSTM.RetryException
              case Strategy.Dropping     => false
              case Strategy.Sliding =>
                queue.dequeueOption match {
                  case Some((_, queue)) =>
                    ref.unsafeSet(journal, queue.enqueue(a))
                    true
                  case None =>
                    true
                }
            }
        }
      def offerAll(as: Iterable[A]): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else if (queue.size + as.size <= capacity) {
            ref.unsafeSet(journal, queue ++ as)
            true
          } else
            strategy match {
              case Strategy.BackPressure => throw ZSTM.RetryException
              case Strategy.Dropping =>
                val forQueue = as.take(capacity - queue.size)
                ref.unsafeSet(journal, queue ++ forQueue)
                false
              case Strategy.Sliding =>
                val forQueue = as.take(capacity)
                val toDrop   = queue.size + forQueue.size - capacity
                ref.unsafeSet(journal, queue.drop(toDrop) ++ forQueue)
                true
            }
        }
      override val peekAll: USTM[Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else Chunk.fromIterable(queue)
        }
      val peek: USTM[A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else
            queue.headOption match {
              case Some(a) => a
              case None    => throw ZSTM.RetryException
            }
        }
      val peekOption: USTM[Option[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else queue.headOption
        }
      val shutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          ref.unsafeSet(journal, null)
        }
      val size: USTM[Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else queue.size
        }
      val take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else
            queue.dequeueOption match {
              case Some((a, queue)) =>
                ref.unsafeSet(journal, queue)
                a
              case None => throw ZSTM.RetryException
            }
        }
      val takeAll: ZSTM[Any, Nothing, zio.Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else {
            ref.unsafeSet(journal, ScalaQueue.empty)
            Chunk.fromIterable(queue)
          }
        }
      def takeUpTo(max: Int): ZSTM[Any, Nothing, Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val queue = ref.unsafeGet(journal)
          if (queue eq null) throw ZSTM.InterruptException(fiberId)
          else {
            val (toTake, remaining) = queue.splitAt(max)
            ref.unsafeSet(journal, remaining)
            Chunk.fromIterable(toTake)
          }
        }
    }

  /**
   * A `Strategy` describes how the queue will handle values if the queue is at
   * capacity.
   */
  private sealed trait Strategy

  private object Strategy {

    /**
     * A strategy that retries if the queue is at capacity.
     */
    case object BackPressure extends Strategy

    /**
     * A strategy that drops new values if the queue is at capacity.
     */
    case object Dropping extends Strategy

    /**
     * A strategy that drops old values if the queue is at capacity.
     */
    case object Sliding extends Strategy
  }
}
