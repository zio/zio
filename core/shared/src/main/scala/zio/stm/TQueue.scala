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

import zio.{Chunk, FiberId}
import zio.stm.ZSTM.internal.Journal
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.collection.immutable.{Queue => ScalaQueue}

/**
 * A `TQueue` is a transactional queue. Offerors can offer values to the queue
 * and takers can take values from the queue.
 */
sealed trait TQueue[A, +E] extends TDequeue.Internal[A, E] with TEnqueue.Internal[A, E] {
  override private[zio] def checkShutdown(journal: Journal, fiberId: FiberId): Unit

  override final def awaitShutdown: ZSTM[Any, E, Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Checks if the queue is empty.
   */
  override final def isEmpty: ZSTM[Any, E, Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the queue is at capacity.
   */
  override final def isFull: ZSTM[Any, E, Boolean] =
    size.map(_ == capacity)

  /**
   * Views all elements in the queue without removing them
   */
  def peekAll: ZSTM[Any, E, Chunk[A]] = takeAll.tap(offerAll(_))

}

object TQueue {

  /**
   * Creates a bounded queue with the back pressure strategy. The queue will
   * retain values until they have been taken, applying back pressure to
   * offerors if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[A](requestedCapacity: => Int): USTM[TQueue[A, Nothing]] =
    makeQueue(requestedCapacity, Strategy.BackPressure)

  /**
   * Creates a bounded queue with the dropping strategy. The queue will drop new
   * values if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[A](requestedCapacity: => Int): USTM[TQueue[A, Nothing]] =
    makeQueue(requestedCapacity, Strategy.Dropping)

  /**
   * Creates a bounded queue with the sliding strategy. The queue will add new
   * values and drop old values if the queue is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[A](requestedCapacity: => Int): USTM[TQueue[A, Nothing]] =
    makeQueue(requestedCapacity, Strategy.Sliding)

  /**
   * Creates an unbounded queue.
   */
  def unbounded[A]: USTM[TQueue[A, Nothing]] =
    makeQueue(Int.MaxValue, Strategy.Dropping)

  /**
   * Creates a queue with the specified strategy.
   */
  private def makeQueue[A](requestedCapacity: => Int, strategy: => Strategy): USTM[TQueue[A, Nothing]] =
    for {
      ref         <- TRef.make[ScalaQueue[A]](ScalaQueue.empty)
      shutdownRef <- TRef.make[Option[Any]](None)
    } yield unsafeMakeQueue[A, Nothing](ref, shutdownRef, requestedCapacity, strategy)

  /**
   * Unsafely creates a queue with the specified strategy.
   */
  private def unsafeMakeQueue[A, E](
    ref: TRef[ScalaQueue[A]],
    shutdownRef: TRef[Option[Any]],
    requestedCapacity: Int,
    strategy: Strategy
  ): TQueue[A, E] =
    new TQueue[A, E] with TEnqueue.Internal[A, E] with TDequeue.Internal[A, E] {
      val capacity: Int =
        requestedCapacity
      val isShutdown: ZSTM[Any, E, Boolean] =
        shutdownRef.get.map(_.isDefined)
      override private[zio] def checkShutdown(journal: Journal, fiberId: FiberId): Unit = {
        val error = shutdownRef.unsafeGet(journal).asInstanceOf[Option[E]]
        if (error.isDefined) {
          val e = error.get
          if (e != null) throw ZSTM.FailException(e)
          else throw ZSTM.InterruptException(fiberId)
        }
      }
      def offer(a: A): ZSTM[Any, E, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          if (queue.size < capacity) {
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
      def offerAll(as: Iterable[A]): ZSTM[Any, E, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          if (queue.size + as.size <= capacity) {
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
      override val peekAll: ZSTM[Any, E, Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          Chunk.fromIterable(queue)
        }
      val peek: ZSTM[Any, E, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          queue.headOption match {
            case Some(a) => a
            case None    => throw ZSTM.RetryException
          }
        }
      val peekOption: ZSTM[Any, E, Option[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          queue.headOption
        }
      val shutdown: ZSTM[Any, E, Unit] =
        ZSTM.Effect((journal, _, _) => shutdownRef.unsafeSet(journal, Some(null.asInstanceOf[Any])))
      def shutdown[E1 >: E](e1: E1): ZSTM[Any, E1, Unit] =
        ZSTM.Effect((journal, _, _) => shutdownRef.unsafeSet(journal, Some(e1.asInstanceOf[Any])))
      val size: ZSTM[Any, E, Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          queue.size
        }
      val take: ZSTM[Any, E, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          queue.dequeueOption match {
            case Some((a, queue)) =>
              ref.unsafeSet(journal, queue)
              a
            case None => throw ZSTM.RetryException
          }
        }
      val takeAll: ZSTM[Any, E, zio.Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue = ref.unsafeGet(journal)
          ref.unsafeSet(journal, ScalaQueue.empty)
          Chunk.fromIterable(queue)
        }
      def takeUpTo(max: Int): ZSTM[Any, E, Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          checkShutdown(journal, fiberId)
          val queue               = ref.unsafeGet(journal)
          val (toTake, remaining) = queue.splitAt(max)
          ref.unsafeSet(journal, remaining)
          Chunk.fromIterable(toTake)
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
