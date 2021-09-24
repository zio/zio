/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import zio._

/**
 * A `ZTHub[RA, RB, EA, EB, A, B]` is a transactional message hub. Publishers
 * can publish messages of type `A` to the hub and subscribers can subscribe to
 * take messages of type `B` from the hub. Publishing messages can require an
 * environment of type `RA` and fail with an error of type `EA`. Taking
 * messages can require an environment of type `RB` and fail with an error of
 * type `EB`.
 */
sealed abstract class ZTHub[-RA, -RB, +EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * The maximum capacity of the hub.
   */
  def capacity: Int

  /**
   * Checks whether the hub is shut down.
   */
  def isShutdown: USTM[Boolean]

  /**
   * Publishes a message to the hub, returning whether the message was
   * published to the hub.
   */
  def publish(a: A): ZSTM[RA, EA, Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning whether
   * they were published to the hub.
   */
  def publishAll(as: Iterable[A]): ZSTM[RA, EA, Boolean]

  /**
   * Shuts down the hub.
   */
  def shutdown: USTM[Unit]

  /**
   * The current number of messages in the hub.
   */
  def size: USTM[Int]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription
   * can be evaluated multiple times to take a message from the hub each time.
   * The caller is responsible for unsubscribing from the hub by shutting down
   * the queue.
   */
  def subscribe: USTM[ZTDequeue[RB, EB, B]]

  /**
   * Waits for the hub to be shut down.
   */
  final def awaitShutdown: USTM[Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Transforms messages published to the hub using the specified function.
   */
  final def contramap[C](f: C => A): ZTHub[RA, RB, EA, EB, C, B] =
    contramapSTM(c => ZSTM.succeedNow(f(c)))

  /**
   * Transforms messages published to the hub using the specified transactional
   * function.
   */
  final def contramapSTM[RC <: RA, EC >: EA, C](f: C => ZSTM[RC, EC, A]): ZTHub[RC, RB, EC, EB, C, B] =
    dimapSTM(f, ZSTM.succeedNow)

  /**
   * Transforms messages published to and taken from the hub using the
   * specified functions.
   */
  final def dimap[C, D](f: C => A, g: B => D): ZTHub[RA, RB, EA, EB, C, D] =
    dimapSTM(c => ZSTM.succeedNow(f(c)), b => ZSTM.succeedNow(g(b)))

  /**
   * Transforms messages published to and taken from the hub using the
   * specified transactional functions.
   */
  final def dimapSTM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZSTM[RC, EC, A],
    g: B => ZSTM[RD, ED, D]
  ): ZTHub[RC, RD, EC, ED, C, D] =
    new ZTHub[RC, RD, EC, ED, C, D] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def publish(c: C): ZSTM[RC, EC, Boolean] =
        f(c).flatMap(self.publish)
      def publishAll(cs: Iterable[C]): ZSTM[RC, EC, Boolean] =
        ZSTM.foreach(cs)(f).flatMap(self.publishAll)
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def subscribe: USTM[ZTDequeue[RD, ED, D]] =
        self.subscribe.map(_.mapSTM(g))
    }

  /**
   * Filters messages published to the hub using the specified function.
   */
  final def filterInput[A1 <: A](f: A1 => Boolean): ZTHub[RA, RB, EA, EB, A1, B] =
    filterInputSTM(a => ZSTM.succeedNow(f(a)))

  /**
   * Filters messages published to the hub using the specified transactional
   * function.
   */
  final def filterInputSTM[RA1 <: RA, EA1 >: EA, A1 <: A](
    f: A1 => ZSTM[RA1, EA1, Boolean]
  ): ZTHub[RA1, RB, EA1, EB, A1, B] =
    new ZTHub[RA1, RB, EA1, EB, A1, B] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def publish(a: A1): ZSTM[RA1, EA1, Boolean] =
        f(a).flatMap(b => if (b) self.publish(a) else ZSTM.succeedNow(false))
      def publishAll(as: Iterable[A1]): ZSTM[RA1, EA1, Boolean] =
        ZSTM.filter(as)(f).flatMap(as => if (as.nonEmpty) self.publishAll(as) else ZSTM.succeedNow(false))
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def subscribe: USTM[ZTDequeue[RB, EB, B]] =
        self.subscribe
    }

  /**
   * Filters messages taken from the hub using the specified function.
   */
  final def filterOutput(f: B => Boolean): ZTHub[RA, RB, EA, EB, A, B] =
    filterOutputSTM(b => ZSTM.succeedNow(f(b)))

  /**
   * Filters messages taken from the hub using the specified transactional
   * function.
   */
  final def filterOutputSTM[RB1 <: RB, EB1 >: EB](
    f: B => ZSTM[RB1, EB1, Boolean]
  ): ZTHub[RA, RB1, EA, EB1, A, B] =
    new ZTHub[RA, RB1, EA, EB1, A, B] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def publish(a: A): ZSTM[RA, EA, Boolean] =
        self.publish(a)
      def publishAll(as: Iterable[A]): ZSTM[RA, EA, Boolean] =
        self.publishAll(as)
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def subscribe: USTM[ZTDequeue[RB1, EB1, B]] =
        self.subscribe.map(_.filterOutputSTM(f))
    }

  /**
   * Transforms messages taken from the hub using the specified function.
   */
  final def map[C](f: B => C): ZTHub[RA, RB, EA, EB, A, C] =
    mapSTM(b => ZSTM.succeedNow(f(b)))

  /**
   * Transforms messages taken from the hub using the specified transactional
   * function.
   */
  final def mapSTM[RC <: RB, EC >: EB, C](f: B => ZSTM[RC, EC, C]): ZTHub[RA, RC, EA, EC, A, C] =
    dimapSTM(ZSTM.succeedNow, f)

  /**
   * Subscribes to receive messages from the hub. The resulting subscription
   * can be evaluated multiple times within the scope of the managed to take a
   * message from the hub each time.
   */
  final def subscribeManaged: ZManaged[Any, Nothing, ZTDequeue[RB, EB, B]] =
    ZManaged.acquireReleaseWith(subscribe.commit)(_.shutdown.commit)

  /**
   * Views the hub as a transactional queue that can only be written to.
   */
  final def toTQueue: ZTEnqueue[RA, EA, A] =
    new ZTEnqueue[RA, EA, A] {
      def capacity: Int =
        self.capacity
      def isShutdown: USTM[Boolean] =
        self.isShutdown
      def offer(a: A): ZSTM[RA, EA, Boolean] =
        self.publish(a)
      def offerAll(as: Iterable[A]): ZSTM[RA, EA, Boolean] =
        self.publishAll(as)
      def peek: ZSTM[Nothing, Any, Any] =
        ZSTM.unit
      def peekOption: ZSTM[Nothing, Any, Option[Any]] =
        ZSTM.succeedNow(None)
      def shutdown: USTM[Unit] =
        self.shutdown
      def size: USTM[Int] =
        self.size
      def take: ZSTM[Nothing, Any, Any] =
        ZSTM.unit
      def takeAll: ZSTM[Nothing, Any, Chunk[Any]] =
        ZSTM.succeedNow(Chunk.empty)
      def takeUpTo(n: Int): ZSTM[Nothing, Any, Chunk[Any]] =
        ZSTM.succeedNow(Chunk.empty)
    }
}

object ZTHub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub will retain
   * messages until they have been taken by all subscribers, applying back
   * pressure to publishers if the hub is at capacity.
   */
  def bounded[A](requestedCapacity: Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.BackPressure)

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   */
  def dropping[A](requestedCapacity: Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.Dropping)

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[A](requestedCapacity: Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.Sliding)

  /**
   * Creates an unbounded hub.
   */
  def unbounded[A]: USTM[THub[A]] =
    makeHub(Int.MaxValue, Strategy.Dropping)

  /**
   * Creates a hub with the specified strategy.
   */
  private def makeHub[A](requestedCapacity: Int, strategy: Strategy): USTM[THub[A]] =
    for {
      empty           <- TRef.make[Node[A]](null)
      hubSize         <- TRef.make(0)
      publisherHead   <- TRef.make(empty)
      publisherTail   <- TRef.make(empty)
      subscriberCount <- TRef.make(0)
      subscribers     <- TRef.make[Set[TRef[TRef[Node[A]]]]](Set.empty)
    } yield unsafeMakeHub(
      hubSize,
      publisherHead,
      publisherTail,
      requestedCapacity,
      strategy,
      subscriberCount,
      subscribers
    )

  /**
   * Unsafely creates a hub with the specified strategy.
   */
  private def unsafeMakeHub[A](
    hubSize: TRef[Int],
    publisherHead: TRef[TRef[Node[A]]],
    publisherTail: TRef[TRef[Node[A]]],
    requestedCapacity: Int,
    strategy: Strategy,
    subscriberCount: TRef[Int],
    subscribers: TRef[Set[TRef[TRef[Node[A]]]]]
  ): THub[A] =
    new THub[A] {
      def capacity: Int =
        requestedCapacity
      def isShutdown: USTM[Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val currentPublisherTail = publisherTail.unsafeGet(journal)
          currentPublisherTail eq null
        }
      def publish(a: A): USTM[Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val currentPublisherTail = publisherTail.unsafeGet(journal)
          if (currentPublisherTail eq null) throw ZSTM.InterruptException(fiberId)
          else {
            val currentSubscriberCount = subscriberCount.unsafeGet(journal)
            if (currentSubscriberCount == 0) true
            else {
              val currentHubSize = hubSize.unsafeGet(journal)
              if (currentHubSize < capacity) {
                val updatedPublisherTail = ZTRef.unsafeMake[Node[A]](null)
                val updatedNode          = Node(a, currentSubscriberCount, updatedPublisherTail)
                currentPublisherTail.unsafeSet(journal, updatedNode)
                publisherTail.unsafeSet(journal, updatedPublisherTail)
                hubSize.unsafeSet(journal, currentHubSize + 1)
                true
              } else {
                strategy match {
                  case Strategy.BackPressure => throw ZSTM.RetryException
                  case Strategy.Dropping     => false
                  case Strategy.Sliding =>
                    if (capacity > 0) {
                      var currentPublisherHead = publisherHead.unsafeGet(journal)
                      var loop                 = true
                      while (loop) {
                        val node = currentPublisherHead.unsafeGet(journal)
                        val head = node.head
                        val tail = node.tail
                        if (head != null) {
                          val updatedNode = node.copy(head = null.asInstanceOf[A])
                          currentPublisherHead.unsafeSet(journal, updatedNode)
                          publisherHead.unsafeSet(journal, tail)
                          loop = false
                        } else {
                          currentPublisherHead = tail
                        }
                      }
                      val updatedPublisherTail = ZTRef.unsafeMake[Node[A]](null)
                      val updatedNode          = Node(a, currentSubscriberCount, updatedPublisherTail)
                      currentPublisherTail.unsafeSet(journal, updatedNode)
                      publisherTail.unsafeSet(journal, updatedPublisherTail)
                    }
                    true
                }
              }
            }
          }
        }
      def publishAll(as: Iterable[A]): USTM[Boolean] =
        ZSTM.foreach(as)(publish).map(_.forall(identity))
      def shutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          val currentPublisherTail = publisherTail.unsafeGet(journal)
          if (currentPublisherTail ne null) {
            publisherTail.unsafeSet(journal, null)
            val currentSubscribers = subscribers.unsafeGet(journal)
            currentSubscribers.foreach(_.unsafeSet(journal, null))
            subscribers.unsafeSet(journal, Set.empty)
          }
        }
      def size: USTM[Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          val currentPublisherTail = publisherTail.unsafeGet(journal)
          if (currentPublisherTail eq null) throw ZSTM.InterruptException(fiberId)
          else hubSize.unsafeGet(journal)
        }
      def subscribe: USTM[TDequeue[A]] =
        makeSubscription(hubSize, publisherHead, publisherTail, requestedCapacity, subscriberCount, subscribers)
    }

  private def makeSubscription[A](
    hubSize: TRef[Int],
    publisherHead: TRef[TRef[Node[A]]],
    publisherTail: TRef[TRef[Node[A]]],
    requestedCapacity: Int,
    subscriberCount: TRef[Int],
    subscribers: TRef[Set[TRef[TRef[Node[A]]]]]
  ): USTM[TDequeue[A]] =
    for {
      currentPublisherTail   <- publisherTail.get
      subscriberHead         <- TRef.make(currentPublisherTail)
      currentSubscriberCount <- subscriberCount.get
      currentSubscribers     <- subscribers.get
      _                      <- subscriberCount.set(currentSubscriberCount + 1)
      _                      <- subscribers.set(currentSubscribers + subscriberHead)
    } yield unsafeMakeSubscription(
      hubSize,
      publisherHead,
      requestedCapacity,
      subscriberHead,
      subscriberCount,
      subscribers
    )

  private def unsafeMakeSubscription[A](
    hubSize: TRef[Int],
    publisherHead: TRef[TRef[Node[A]]],
    requestedCapacity: Int,
    subscriberHead: TRef[TRef[Node[A]]],
    subscriberCount: TRef[Int],
    subscribers: TRef[Set[TRef[TRef[Node[A]]]]]
  ): TDequeue[A] =
    new TDequeue[A] {
      override def capacity: Int =
        requestedCapacity
      override def isShutdown: USTM[Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val currentSubscriberHead = subscriberHead.unsafeGet(journal)
          currentSubscriberHead eq null
        }
      override def offer(a: Nothing): ZSTM[Nothing, Any, Boolean] =
        ZSTM.succeedNow(false)
      override def offerAll(as: Iterable[Nothing]): ZSTM[Nothing, Any, Boolean] =
        ZSTM.succeedNow(false)
      override def peek: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead eq null) throw ZSTM.InterruptException(fiberId)
          else {
            var a    = null.asInstanceOf[A]
            var loop = true
            while (loop) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) throw ZSTM.RetryException
              else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  a = node.head
                  loop = false
                } else {
                  currentSubscriberHead = tail
                }
              }
            }
            a
          }
        }
      override def peekOption: ZSTM[Any, Nothing, Option[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead eq null) throw ZSTM.InterruptException(fiberId)
          else {
            var a    = null.asInstanceOf[Option[A]]
            var loop = true
            while (loop) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) {
                a = None
                loop = false
              } else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  a = Some(node.head)
                  loop = false
                } else {
                  currentSubscriberHead = tail
                }
              }
            }
            a
          }
        }
      override def shutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead ne null) {
            subscriberHead.unsafeSet(journal, null)
            var loop = true
            while (loop) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) {
                loop = false
              } else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  val subscribers = node.subscribers
                  if (subscribers == 1) {
                    val size        = hubSize.unsafeGet(journal)
                    val updatedNode = node.copy(head = null.asInstanceOf[A], subscribers = 0)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                    publisherHead.unsafeSet(journal, tail)
                    hubSize.unsafeSet(journal, size - 1)
                  } else {
                    val updatedNode = node.copy(subscribers = subscribers - 1)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                  }
                }
                currentSubscriberHead = tail
              }
            }
            val currentSubscriberCount = subscriberCount.unsafeGet(journal)
            subscriberCount.unsafeSet(journal, currentSubscriberCount - 1)
            subscribers.unsafeSet(journal, subscribers.unsafeGet(journal) - subscriberHead)
          }
        }
      override def size: USTM[Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead eq null) throw ZSTM.InterruptException(fiberId)
          else {
            var loop = true
            var size = 0
            while (loop) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) loop = false
              else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  size += 1
                  if (size == Int.MaxValue) {
                    loop = false
                  }
                }
                currentSubscriberHead = tail
              }
            }
            size
          }
        }
      override def take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead eq null) throw ZSTM.InterruptException(fiberId)
          else {
            var a    = null.asInstanceOf[A]
            var loop = true
            while (loop) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) throw ZSTM.RetryException
              else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  val subscribers = node.subscribers
                  if (subscribers == 1) {
                    val size        = hubSize.unsafeGet(journal)
                    val updatedNode = node.copy(head = null.asInstanceOf[A], subscribers = 0)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                    publisherHead.unsafeSet(journal, tail)
                    hubSize.unsafeSet(journal, size - 1)
                  } else {
                    val updatedNode = node.copy(subscribers = subscribers - 1)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                  }
                  subscriberHead.unsafeSet(journal, tail)
                  a = head
                  loop = false
                } else {
                  currentSubscriberHead = tail
                }
              }
            }
            a
          }
        }
      override def takeAll: ZSTM[Any, Nothing, Chunk[A]] =
        takeUpTo(Int.MaxValue)
      override def takeUpTo(max: Int): ZSTM[Any, Nothing, Chunk[A]] =
        ZSTM.Effect { (journal, fiberId, _) =>
          var currentSubscriberHead = subscriberHead.unsafeGet(journal)
          if (currentSubscriberHead eq null) throw ZSTM.InterruptException(fiberId)
          else {
            val builder = ChunkBuilder.make[A]()
            var n       = 0
            while (n != max) {
              val node = currentSubscriberHead.unsafeGet(journal)
              if (node eq null) {
                n = max
              } else {
                val head = node.head
                val tail = node.tail
                if (head != null) {
                  val subscribers = node.subscribers
                  if (subscribers == 1) {
                    val size        = hubSize.unsafeGet(journal)
                    val updatedNode = node.copy(head = null.asInstanceOf[A], subscribers = 0)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                    publisherHead.unsafeSet(journal, tail)
                    hubSize.unsafeSet(journal, size - 1)
                  } else {
                    val updatedNode = node.copy(subscribers = subscribers - 1)
                    currentSubscriberHead.unsafeSet(journal, updatedNode)
                  }
                  builder += head
                  n += 1
                }
                currentSubscriberHead = tail
              }
            }
            subscriberHead.unsafeSet(journal, currentSubscriberHead)
            builder.result()
          }
        }
    }

  private final case class Node[A](head: A, subscribers: Int, tail: TRef[Node[A]])

  /**
   * A `Strategy` describes how the hub will handle messages if the hub is at
   * capacity.
   */
  private sealed trait Strategy

  private object Strategy {

    /**
     * A strategy that retries if the hub is at capacity.
     */
    case object BackPressure extends Strategy

    /**
     * A strategy that drops new messages if the hub is at capacity.
     */
    case object Dropping extends Strategy

    /**
     * A strategy that drops old messages if the hub is at capacity.
     */
    case object Sliding extends Strategy
  }
}
