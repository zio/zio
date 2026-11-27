/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `THub` is a transactional message hub. Publishers publish messages to the
 * hub, and subscribers subscribe to take messages published after they
 * subscribed. It works like `Hub`, but its operations are STM transactions, so
 * publishing and taking can be combined with other transactional operations
 * and commit together.
 *
 * A subscription is a [[TDequeue]]. What `publish` does when the hub is full
 * depends on the constructor: `bounded` waits for room, `dropping` discards the
 * new message, and `sliding` discards the oldest message.
 *
 * {{{
 * import zio._
 *
 * val program: UIO[(Chunk[String], Chunk[String])] =
 *   ZIO.scoped {
 *     for {
 *       hub        <- THub.bounded[String](8).commit
 *       first      <- hub.subscribeScoped
 *       second     <- hub.subscribeScoped
 *       _          <- hub.publishAll(List("a", "b")).commit
 *       fromFirst  <- first.takeAll.commit
 *       fromSecond <- second.takeAll.commit
 *     } yield (fromFirst, fromSecond)
 *   }
 * // (Chunk(a,b),Chunk(a,b))
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stm/thub/ THub]]
 */
sealed abstract class THub[A] extends TEnqueue.Internal[A] {

  /**
   * Publishes a message to the hub. With no subscribers, the message is
   * discarded and the result is `true`. When the hub is full, a bounded hub
   * retries, so the fiber suspends until the subscribers have taken enough
   * messages to make room; a dropping hub discards the message and succeeds
   * with `false`; a sliding hub drops the oldest message and succeeds with
   * `true`.
   */
  def publish(a: A): USTM[Boolean]

  /**
   * Publishes the messages one by one in a single transaction, and succeeds
   * with `true` when every one of them was published. When there is not room
   * for all of them, a bounded hub with subscribers retries, so the fiber
   * suspends until the hub changes. A dropping hub publishes the messages that
   * fit and succeeds with `false` when it discards any. A sliding hub drops the
   * oldest messages to make room and succeeds with `true`.
   */
  def publishAll(as: Iterable[A]): USTM[Boolean]

  /**
   * Subscribes to the hub. The subscription is a [[TDequeue]] that receives
   * messages published after it was created. The caller unsubscribes by
   * shutting the subscription down; until then, the messages it has not taken
   * count toward the hub's size and capacity.
   */
  def subscribe: USTM[TDequeue[A]]

  override final def awaitShutdown: USTM[Unit] =
    isShutdown.flatMap(b => if (b) ZSTM.unit else ZSTM.retry)

  /**
   * Checks if the hub is empty, that is, its `size` is 0.
   */
  override final def isEmpty: USTM[Boolean] =
    size.map(_ == 0)

  /**
   * Checks if the hub is at capacity, that is, its size equals `capacity`.
   */
  override final def isFull: USTM[Boolean] =
    size.map(_ == capacity)

  /**
   * Publishes a message to the hub. The same as `publish`.
   */
  final def offer(a: A): USTM[Boolean] =
    publish(a)

  /**
   * Publishes the messages to the hub. The same as `publishAll`.
   */
  final def offerAll(as: Iterable[A]): USTM[Boolean] =
    publishAll(as)

  /**
   * Subscribes to the hub, committing the subscription right away, and shuts
   * the subscription down when the scope closes. The subscription receives
   * messages published after it was created, as with `subscribe`.
   */
  final def subscribeScoped(implicit trace: Trace): ZIO[Scope, Nothing, TDequeue[A]] =
    ZIO.acquireRelease(subscribe.commit)(_.shutdown.commit)
}

/**
 * Constructors for [[THub]]: `bounded`, `dropping`, `sliding` and `unbounded`.
 */
object THub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub keeps
   * messages until they have been taken by all subscribers. When it is full,
   * `publish` retries, so the publishing fiber suspends until the subscribers
   * make room.
   */
  def bounded[A](requestedCapacity: => Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.BackPressure)

  /**
   * Creates a bounded hub with the dropping strategy. When the hub is full,
   * `publish` discards the new message and succeeds with `false`.
   */
  def dropping[A](requestedCapacity: => Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.Dropping)

  /**
   * Creates a bounded hub with the sliding strategy. When the hub is full,
   * `publish` drops the oldest message, so subscribers that had not taken it no
   * longer receive it, adds the new one and succeeds with `true`.
   */
  def sliding[A](requestedCapacity: => Int): USTM[THub[A]] =
    makeHub(requestedCapacity, Strategy.Sliding)

  /**
   * Creates an unbounded hub: its capacity is `Int.MaxValue`, so `publish` does
   * not wait for room.
   */
  def unbounded[A]: USTM[THub[A]] =
    makeHub(Int.MaxValue, Strategy.Dropping)

  /**
   * Creates a hub with the specified strategy.
   */
  private def makeHub[A](requestedCapacity: => Int, strategy: => Strategy): USTM[THub[A]] =
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
                val updatedPublisherTail = TRef.unsafeMake[Node[A]](null)
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
                        if (node eq null) throw ZSTM.RetryException
                        else {
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
                      }
                      val updatedPublisherTail = TRef.unsafeMake[Node[A]](null)
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
    new TDequeue.Internal[A] {
      override def capacity: Int =
        requestedCapacity
      override def isShutdown: USTM[Boolean] =
        ZSTM.Effect { (journal, _, _) =>
          val currentSubscriberHead = subscriberHead.unsafeGet(journal)
          currentSubscriberHead eq null
        }
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
