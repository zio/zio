/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.nowarn

/**
 * A `Queue` is a lightweight, high-performance data structure for coordinating
 * producers and consumers of data. Queues are appropriate for producer-consumer
 * situations in which a potentially unbounded number of producers offer values
 * to a potentially unbounded number of consumers, with backpressure applied to
 * producers when consumers are unable to keep up.
 *
 * Queues do not allow null values to be published.
 */
trait Queue[+A] extends QueueOffer[Nothing] with QueueTake[A] with QueuePoll[A] {

  /**
   * Retrieves the size of the queue, which is equal to the number of elements
   * in the queue. This may be negative if fibers are suspended waiting for
   * elements to be added to the queue.
   */
  def size: UIO[Int]

  /**
   * Returns true if the queue is empty, false otherwise.
   */
  def isEmpty: UIO[Boolean]

  /**
   * Returns true if the queue is full, false otherwise.
   */
  def isFull: UIO[Boolean]

  /**
   * Returns the number of elements the queue has capacity for.
   */
  def capacity: Int

  /**
   * Returns the maximum number of elements the queue can hold.
   */
  def maxCapacity: Long

  /**
   * Returns the number of elements that can be added to the queue without
   * blocking.
   */
  def available: UIO[Int]

  /**
   * Interrupts any fibers that are waiting on the queue.
   */
  def shutdown: UIO[Unit]

  /**
   * Interrupts any fibers that are waiting on the queue with the specified
   * cause.
   */
  def shutdownCause(cause: Cause[Nothing]): UIO[Chunk[A]]

  /**
   * Returns a promise that will be completed when the queue is shutdown.
   * This can be used to coordinate actions that must wait until the queue
   * is fully shutdown and all pending operations have completed.
   */
  def awaitShutdown: UIO[Unit]

  /**
   * Returns whether the queue has been shut down.
   */
  def isShutdown: UIO[Boolean]

  /**
   * Unsafely offers an element to the queue.
   */
  def unsafeOffer(a: A): Boolean

  /**
   * Unsafely takes an element from the queue.
   */
  def unsafeTake(): A

  override def toString: String =
    "Queue"
}

trait QueueOffer[-A] {
  def offer(a: A): UIO[Boolean]
  def offerAll(as: Iterable[A]): UIO[Boolean]
  def zipWithLatest[B, C](that: Queue[B])(f: (A, B) => C): ZStream[Any, Nothing, C]
}

trait QueueTake[+A] {
  def take: UIO[A]
  def takeAll: UIO[Chunk[A]]
  def takeUpTo(max: Int): UIO[Chunk[A]]
  def takeN(n: Int): UIO[Chunk[A]]
}

trait QueuePoll[+A] {
  def poll: UIO[Option[A]]
  def pollUpTo(max: Int): UIO[Chunk[A]]
}

object Queue {

  /**
   * Creates a bounded queue with the specified capacity.
   */
  def bounded[A](capacity: Int): UIO[Queue[A]] =
    if (capacity <= 0) ZIO.die(new IllegalArgumentException("Queue capacity must be positive"))
    else if (capacity == 1) singleProducerSingleConsumerQueue(capacity).map(new OneElementQueue(_))
    else {
      val make = for {
        publisher <- Promise.make[Nothing, Unit]
        shutdown  <- Ref.make(false)
        queue     <- singlyLinkedQueue[A](capacity)
      } yield new BoundedQueue(queue, publisher, shutdown)
      make.sandbox
    }

  /**
   * Creates an unbounded queue.
   */
  def unbounded[A]: UIO[Queue[A]] =
    singlyLinkedQueue[A](Int.MaxValue).map(new UnboundedQueue(_))

  private def singlyLinkedQueue[A](capacity: Int): UIO[SinglyLinkedQueue[A]] =
    Ref.make(new SinglyLinkedQueue.Node[A](null.asInstanceOf[A | Null])).map(new SinglyLinkedQueue[A](_))

  private def singleProducerSingleConsumerQueue[A](capacity: Int): UIO[SingleProducerSingleConsumerQueue[A]] =
    Ref.make(new SingleProducerSingleConsumerQueue.Node[A](null.asInstanceOf[A | Null])).map(
      new SingleProducerSingleConsumerQueue[A](_, capacity)
    )

  private final class BoundedQueue[A](
    queue: SinglyLinkedQueue[A],
    publisher: Promise[Nothing, Unit],
    shutdown: Ref[Boolean]
  ) extends Queue[A] {
    self =>

    def capacity: Int = queue.capacity

    def maxCapacity: Long = Int.MaxValue

    def size: UIO[Int] =
      queue.size

    def isEmpty: UIO[Boolean] =
      queue.isEmpty

    def isFull: UIO[Boolean] =
      queue.isFull

    def available: UIO[Int] =
      queue.available

    def awaitShutdown: UIO[Unit] =
      shutdown.get.flatMap(if (_) ZIO.unit else publisher.await)

    def isShutdown: UIO[Boolean] =
      shutdown.get

    def shutdown: UIO[Unit] =
      shutdown.get.flatMap {
        case true => ZIO.unit
        case false =>
          shutdown.set(true) *>
            publisher.interrupt *>
            queue.drain
      }

    def shutdownCause(cause: Cause[Nothing]): UIO[Chunk[A]] =
      ZIO.uninterruptibleMask { restore =>
        shutdown.getWith { current =>
          if (current) ZIO.succeed(Chunk.empty)
          else
            shutdown.set(true) *>
              publisher.interrupt *> // This will interrupt all waiting takers
              queue.drain.tap { values =>
                // Fail any pending offers with the cause
                publisher.failCause(cause)
              }
        }.flatten
      }

    def offer(a: A): UIO[Boolean] =
      ZIO.uninterruptible {
        ZIO
          .checkInterruptible {
            shutdown.get.flatMap {
              case true => ZIO.failCause(Cause.interrupt)
              case false =>
                queue.offer(a).flatMap { offered =>
                  if (offered) {
                    publisher.succeed(()).orDie *>
                      ZIO.succeed(true)
                  } else {
                    ZIO.succeed(false)
                  }
                }
            }
          }
          .onError { _ =>
            queue.poll.flatMap {
              case Some(`a`) => ZIO.unit
              case _         => ZIO.unit
            }
          }
      }

    def offerAll(as: Iterable[A]): UIO[Boolean] =
      ZIO.uninterruptible {
        ZIO
          .checkInterruptible {
            shutdown.get.flatMap {
              case true => ZIO.failCause(Cause.interrupt)
              case false =>
                queue.offerAll(as).flatMap { offered =>
                  if (offered) {
                    publisher.succeed(()).orDie *>
                      ZIO.succeed(true)
                  } else {
                    ZIO.succeed(false)
                  }
                }
            }
          }
          .onError { _ =>
            queue.pollUpTo(as.size).flatMap { removed =>
              val remaining = as.drop(removed.length)
              ZIO.foreachDiscard(remaining)(a => queue.offer(a))
            }
          }
      }

    def take: UIO[A] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false =>
            queue.poll.flatMap {
              case Some(a) => ZIO.succeed(a)
              case None    => publisher.await *> take
            }
        }
      }

    def takeAll: UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false => queue.takeAll.flatMap { chunk =>
              if (chunk.isEmpty) publisher.await *> takeAll
              else ZIO.succeed(chunk)
            }
        }
      }

    def takeUpTo(max: Int): UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false => queue.takeUpTo(max).flatMap { chunk =>
              if (chunk.isEmpty) publisher.await *> takeUpTo(max)
              else ZIO.succeed(chunk)
            }
        }
      }

    def takeN(n: Int): UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false =>
            if (n <= 0) ZIO.succeed(Chunk.empty)
            else
              queue.poll.flatMap {
                case Some(a) => takeN(n - 1).map(as => a +: as)
                case None    => publisher.await *> takeN(n)
              }
        }
      }

    def poll: UIO[Option[A]] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false => queue.poll
        }
      }

    def pollUpTo(max: Int): UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        shutdown.get.flatMap {
          case true => ZIO.failCause(Cause.interrupt)
          case false => queue.takeUpTo(max)
        }
      }

    def unsafeOffer(a: A): Boolean =
      !shutdown.unsafeGet() && queue.unsafeOffer(a)

    def unsafeTake(): A =
      queue.unsafeTake()

    override def toString: String =
      s"BoundedQueue(capacity = $capacity)"
  }

  private final class UnboundedQueue[A](queue: SinglyLinkedQueue[A]) extends Queue[A] {
    self =>

    def capacity: Int = Int.MaxValue

    def maxCapacity: Long = Long.MaxValue

    def size: UIO[Int] =
      queue.size

    def isEmpty: UIO[Boolean] =
      queue.isEmpty

    def isFull: UIO[Boolean] =
      ZIO.succeed(false)

    def available: UIO[Int] =
      ZIO.succeed(Int.MaxValue)

    private val shutdownFlag = new java.util.concurrent.atomic.AtomicBoolean(false)
    private val shutdownHook = Promise.make[Nothing, Unit].unsafeRunSync()

    def awaitShutdown: UIO[Unit] =
      shutdownHook.await

    def isShutdown: UIO[Boolean] =
      ZIO.succeed(shutdownFlag.get())

    def shutdown: UIO[Unit] =
      ZIO.succeed {
        if (shutdownFlag.compareAndSet(false, true)) {
          shutdownHook.interrupt.unsafeRunSync()
        }
      }

    def shutdownCause(cause: Cause[Nothing]): UIO[Chunk[A]] =
      ZIO.succeed {
        if (shutdownFlag.compareAndSet(false, true)) {
          shutdownHook.interrupt.unsafeRunSync()
          queue.drain.unsafeRunSync()
        } else {
          Chunk.empty
        }
      }

    def offer(a: A): UIO[Boolean] =
      ZIO.succeed {
        if (shutdownFlag.get()) false
        else {
          val result = queue.unsafeOffer(a)
          if (result) true else false
        }
      }

    def offerAll(as: Iterable[A]): UIO[Boolean] =
      ZIO.succeed {
        if (shutdownFlag.get()) false
        else {
          val result = queue.unsafeOfferAll(as)
          if (result) true else false
        }
      }

    def take: UIO[A] =
      ZIO.checkInterruptible {
        if (shutdownFlag.get()) ZIO.failCause(Cause.interrupt)
        else
          queue.poll.flatMap {
            case Some(a) => ZIO.succeed(a)
            case None    => ZIO.yieldNow *> take
          }
      }

    def takeAll: UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        if (shutdownFlag.get()) ZIO.failCause(Cause.interrupt)
        else {
          val chunk = queue.unsafeTakeAll()
          if (chunk.isEmpty) ZIO.yieldNow *> takeAll
          else ZIO.succeed(chunk)
        }
      }

    def takeUpTo(max: Int): UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        if (shutdownFlag.get()) ZIO.failCause(Cause.interrupt)
        else {
          val chunk = queue.unsafeTakeUpTo(max)
          if (chunk.isEmpty) ZIO.yieldNow *> takeUpTo(max)
          else ZIO.succeed(chunk)
        }
      }

    def takeN(n: Int): UIO[Chunk[A]] =
      ZIO.checkInterruptible {
        if (shutdownFlag.get()) ZIO.failCause(Cause.interrupt)
        else if (n <= 0) ZIO.succeed(Chunk.empty)
        else
          queue.poll.flatMap {
            case Some(a) => takeN(n - 1).map(as => a +: as)
            case None    => ZIO.yieldNow *> takeN(n)
          }
      }

    def poll: UIO[Option[A]] =
      ZIO.succeed {
        if (shutdownFlag.get()) None
        else queue.unsafePoll()
      }

    def pollUpTo(max: Int): UIO[Chunk[A]] =
      ZIO.succeed {
        if (shutdownFlag.get()) Chunk.empty
        else queue.unsafeTakeUpTo(max)
      }

    def unsafeOffer(a: A): Boolean =
      !shutdownFlag.get() && queue.unsafeOffer(a)

    def unsafeTake(): A =
      queue.unsafeTake()

    override def toString: String =
      "UnboundedQueue"
  }

  private final class OneElementQueue[A](queue: SingleProducerSingleConsumerQueue[A]) extends Queue[A] {
    self =>

    def capacity: Int = 1

    def maxCapacity: Long = 1L

    def size: UIO[Int] =
      queue.size

    def isEmpty: UIO[Boolean] =
      queue.isEmpty

    def isFull: UIO[Boolean] =
      queue.isFull

    def available: UIO[Int] =
      queue.available

    def awaitShutdown: UIO[Unit] =
      queue.awaitShutdown

    def isShutdown: UIO[Boolean] =
      queue.isShutdown

    def shutdown: UIO[Unit] =
      queue.shutdown

    def shutdownCause(cause: Cause[Nothing]): UIO[Chunk[A]] =
      queue.shutdownCause(cause)

    def offer(a: A): UIO[Boolean] =
      queue.offer(a)

    def offerAll(as: Iterable[A]): UIO[Boolean] =
      queue.offerAll(as)

    def take: UIO[A] =
      queue.take

    def takeAll: UIO[Chunk[A]] =
      queue.takeAll

    def takeUpTo(max: Int): UIO[Chunk[A]] =
      queue.takeUpTo(max)

    def takeN(n: Int): UIO[Chunk[A]] =
      queue.takeN(n)

    def poll: UIO[Option[A]] =
      queue.poll

    def pollUpTo(max: Int): UIO[Chunk[A]] =
      queue.pollUpTo(max)

    def unsafeOffer(a: A): Boolean =
      queue.unsafeOffer(a)

    def unsafeTake(): A =
      queue.unsafeTake()

    override def toString: String =
      "OneElementQueue"
  }

  private final class SinglyLinkedQueue[A] private (private val headRef: Ref[SinglyLinkedQueue.Node[A]]) {
    import SinglyLinkedQueue._

    def capacity: Int = Int.MaxValue

    def size: UIO[Int] =
      Ref
        .make(headRef.unsafeGet())
        .map { ref =>
          var count = 0
          var node  = ref.unsafeGet()
          while (node.next ne null) {
            node = node.next
            count += 1
          }
          count
        }
        .orDie

    def isEmpty: UIO[Boolean] =
      headRef.get.map(_.next eq null)

    def isFull: UIO[Boolean] =
      ZIO.succeed(false)

    def available: UIO[Int] =
      ZIO.succeed(Int.MaxValue)

    def offer(a: A): UIO[Boolean] =
      Ref
        .make(headRef.unsafeGet())
        .map { ref =>
          var node = ref.unsafeGet()
          while (node.next ne null) {
            node = node.next
          }
          node.next = new Node(a)
          true
        }
        .orDie

    def offerAll(as: Iterable[A]): UIO[Boolean] =
      Ref
        .make(headRef.unsafeGet())
        .map { ref =>
          var node = ref.unsafeGet()
          while (node.next ne null) {
            node = node.next
          }
          as.foreach { a =>
            node.next = new Node(a)
            node = node.next
          }
          true
        }
        .orDie

    def poll: UIO[Option[A]] =
      headRef.get.flatMap { head =>
        val next = head.next
        if (next eq null) ZIO.succeed(None)
        else
          headRef.set(next) *>
            ZIO.succeed(Some(next.value))
      }

    def takeAll: UIO[Chunk[A]] =
      Ref
        .make(Chunk.empty[A])
        .zipWith(headRef.get) { (accRef, head) =>
          var node = head.next
          var acc  = accRef.unsafeGet()
          while (node ne null) {
            acc = acc :+ node.value
            node = node.next
          }
          headRef.set(new Node(null))
          acc
        }
        .orDie

    def takeUpTo(max: Int): UIO[Chunk[A]] =
      Ref
        .make(Chunk.empty[A])
        .zipWith(headRef.get) { (accRef, head) =>
          var node = head.next
          var acc  = accRef.unsafeGet()
          var i    = 0
          while (node ne null && i < max) {
            acc = acc :+ node.value
            node = node.next
            i += 1
          }
          if (i > 0) {
            headRef.set(node)
          }
          acc
        }
        .orDie

    def drain: UIO[Chunk[A]] =
      takeAll

    def unsafeOffer(a: A): Boolean = {
      var node =