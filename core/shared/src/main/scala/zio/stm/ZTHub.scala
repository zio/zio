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
import zio.stm.ZSTM.internal._

/**
 * A `ZTHub[RA, RB, EA, EB, A, B]` is an asynchronous message hub that can be
 * composed transactionally. Publishers can publish messages of type `A` to the
 * hub and subscribers can subscribe to take messages of type `B` from the hub.
 * Publishing messages can require an environment of type `RA` and fail with an
 * error of type `EA`. Taking messages can require an environment of type `RB`
 * and fail with an error of
 * type `EB`.
 */
sealed abstract class ZTHub[-RA, -RB, +EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Waits for the hub to be shut down.
   */
  def awaitShutdown: USTM[Unit]

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
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
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
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
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
      def awaitShutdown: USTM[Unit] =
        self.awaitShutdown
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
        ???
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
   * Creates a bounded hub with the back pressure strategy that can be composed
   * transactionally. The hub will retain messages until they have been taken
   * by all subscribers, applying back pressure to publishers if the hub is at
   * capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[A](requestedCapacity: Int): USTM[THub[A]] =
    ZSTM.succeed(Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.BackPressure()))

  /**
   * Creates a bounded hub with the dropping strategy that can be composed
   * transactionally. The hub will drop new messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[A](requestedCapacity: Int): USTM[THub[A]] =
    ZSTM.succeed(Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.Dropping()))

  /**
   * Creates a bounded hub with the sliding strategy that can be composed
   * transactionally. The hub will add new messages and drop old messages if
   * the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[A](requestedCapacity: Int): USTM[THub[A]] =
    ZSTM.succeed(Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.Sliding()))

  /**
   * Creates an unbounded hub that can be composed transactionally.
   */
  def unbounded[A]: USTM[THub[A]] =
    ZSTM.succeed(Hub.unbounded[A]).flatMap(makeHub(_, Strategy.Dropping()))

  /**
   * Creates a hub with the specified strategy that can be composed
   * transactionally.
   */
  private def makeHub[A](hub: Hub[A], strategy: Strategy[A]): USTM[THub[A]] =
    TRef.make(false).map { shutdownFlag =>
      unsafeMakeHub(
        hub,
        ZTRef.unsafeMake[Set[TDequeue[A]]](Set.empty),
        shutdownFlag,
        strategy
      )
    }

  /**
   * Unsafely creates a hub with the specified strategy that can be composed
   * transactionally.
   */
  private def unsafeMakeHub[A](
    hub: Hub[A],
    subscribers: TRef[Set[TDequeue[A]]],
    shutdownFlag: TRef[Boolean],
    strategy: Strategy[A]
  ): THub[A] =
    new THub[A] {
      val awaitShutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          if (shutdownFlag.unsafeGet(journal)) ()
          else throw ZSTM.RetryException
        }
      val capacity: Int =
        hub.capacity
      val isShutdown: USTM[Boolean] =
        shutdownFlag.get
      def publish(a: A): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          if (shutdownFlag.unsafeGet(journal)) throw ZSTM.InterruptException(fiberId)
          else if (hub.publish(journal, a)) true
          else strategy.handleSurplus(journal, hub, Chunk(a))
        }
      def publishAll(as: Iterable[A]): ZSTM[Any, Nothing, Boolean] =
        ZSTM.Effect { (journal, fiberId, _) =>
          if (shutdownFlag.unsafeGet(journal)) throw ZSTM.InterruptException(fiberId)
          else {
            val surplus = hub.publishAll(journal, as)
            if (surplus.isEmpty) true
            else strategy.handleSurplus(journal, hub, surplus)
          }
        }
      val shutdown: USTM[Unit] =
        ZSTM
          .unlessSTM(shutdownFlag.getAndSet(true)) {
            subscribers.getAndSet(Set.empty).flatMap(ZSTM.foreach(_)(_.shutdown))
          }
          .unit
      val size: USTM[Int] =
        ZSTM.Effect { (journal, fiberId, _) =>
          if (shutdownFlag.unsafeGet(journal)) throw ZSTM.InterruptException(fiberId)
          else hub.size(journal)
        }
      val subscribe: USTM[ZTDequeue[Any, Nothing, A]] =
        for {
          subscription <- makeSubscription(hub, subscribers)
          _            <- subscribers.update(_ + subscription)
        } yield subscription
    }

  /**
   * Creates a subscription with the specified strategy that can be composed
   * transactionally.
   */
  def makeSubscription[A](hub: Hub[A], subscribers: TRef[Set[TDequeue[A]]]): USTM[ZTDequeue[Any, Nothing, A]] =
    ZSTM.Effect { (journal, _, _) =>
      val shutdownFlag = ZTRef.unsafeMake(false)
      unsafeMakeSubscription(
        hub,
        subscribers,
        hub.subscribe(journal),
        shutdownFlag
      )
    }

  /**
   * Unsafely makes a subscription with the specified strategy that can be
   * composed transactionally.
   */
  def unsafeMakeSubscription[A](
    hub: Hub[A],
    subscribers: TRef[Set[TDequeue[A]]],
    subscription: Hub.Subscription[A],
    shutdownFlag: TRef[Boolean]
  ): ZTDequeue[Any, Nothing, A] =
    new ZTDequeue[Any, Nothing, A] { self =>
      val capacity: Int =
        hub.capacity
      val isShutdown: USTM[Boolean] =
        shutdownFlag.get
      def offer(a: Nothing): ZSTM[Nothing, Any, Boolean] =
        ZSTM.succeedNow(false)
      def offerAll(as: Iterable[Nothing]): ZSTM[Nothing, Any, Boolean] =
        ZSTM.succeedNow(false)
      def peek: ZSTM[Any, Nothing, A] =
        ???
      def peekOption: ZSTM[Any, Nothing, Option[A]] =
        ???
      val shutdown: USTM[Unit] =
        ZSTM.Effect { (journal, _, _) =>
          if (shutdownFlag.unsafeGet(journal)) ()
          else {
            shutdownFlag.unsafeSet(journal, true)
            subscribers.update(_ - self)
            subscription.unsubscribe(journal)
          }
        }
      val size: USTM[Int] =
        ZSTM.Effect((journal, _, _) => subscription.size(journal))
      val take: ZSTM[Any, Nothing, A] =
        ZSTM.Effect { (journal, fiberId, _) =>
          if (shutdownFlag.unsafeGet(journal)) throw ZSTM.InterruptException(fiberId)
          else {
            val empty   = null.asInstanceOf[A]
            val message = subscription.poll(journal, empty)
            message match {
              case null => throw ZSTM.RetryException
              case a    => a
            }
          }
        }
      val takeAll: ZSTM[Any, Nothing, Chunk[A]] =
        ???
      def takeUpTo(n: Int): ZSTM[Any, Nothing, Chunk[A]] =
        ???
    }

  private trait Strategy[A] {
    def handleSurplus(journal: Journal, hub: Hub[A], as: Chunk[A]): Boolean
  }

  private object Strategy {
    final case class BackPressure[A]() extends Strategy[A] {
      def handleSurplus(journal: Journal, hub: Hub[A], as: Chunk[A]): Boolean =
        throw ZSTM.RetryException
    }
    final case class Dropping[A]() extends Strategy[A] {
      def handleSurplus(journal: Journal, hub: Hub[A], as: Chunk[A]): Boolean =
        false
    }
    final case class Sliding[A]() extends Strategy[A] {
      def handleSurplus(journal: Journal, hub: Hub[A], as: Chunk[A]): Boolean = {
        def unsafeSlidingPublish(as: Iterable[A]): Unit =
          if (as.nonEmpty && hub.capacity > 0) {
            val iterator = as.iterator
            var a        = iterator.next()
            var loop     = true
            while (loop) {
              hub.slide(journal)
              val published = hub.publish(journal, a)
              if (published && iterator.hasNext) {
                a = iterator.next()
              } else if (published && !iterator.hasNext) {
                loop = false
              }
            }
          }

        unsafeSlidingPublish(as)
        true
      }
    }
  }

  private trait Hub[A] {

    /**
     * The maximum capacity of the hub.
     */
    def capacity: Int

    /**
     * Checks whether the hub is currently empty.
     */
    def isEmpty(journal: Journal): Boolean

    /**
     * Checks whether the hub is currently full.
     */
    def isFull(journal: Journal): Boolean

    /**
     * Publishes the specified value to the hub and returns whether the value was
     * successfully published to the hub.
     */
    def publish(journal: Journal, a: A): Boolean

    /**
     * Publishes the specified values to the hub, returning the values that could
     * not be successfully published to the hub.
     */
    def publishAll(journal: Journal, as: Iterable[A]): Chunk[A]

    /**
     * The current number of values in the hub.
     */
    def size(journal: Journal): Int

    /**
     * Drops a value from the hub.
     */
    def slide(journal: Journal): Unit

    /**
     * Subscribes to receive values from the hub.
     */
    def subscribe(journal: Journal): Hub.Subscription[A]
  }

  private object Hub {

    trait Subscription[A] {

      /**
       * Checks whether there are values available to take from the hub.
       */
      def isEmpty(journal: Journal): Boolean

      /**
       * Takes a value from the hub if there is one or else returns the
       * specified default value.
       */
      def poll(journal: Journal, default: A): A

      /**
       * Takes up to the specified number of values from the hub.
       */
      def pollUpTo(journal: Journal, n: Int): Chunk[A]

      /**
       * The current number of values available to take from the hub.
       */
      def size(journal: Journal): Int

      /**
       * Unsubscribes this subscriber from the hub.
       */
      def unsubscribe(journal: Journal): Unit
    }

    def bounded[A](requestedCapacity: Int): Hub[A] = {
      assert(requestedCapacity > 0)
      if (requestedCapacity == 1) new BoundedHubSingle
      else if (nextPow2(requestedCapacity) == requestedCapacity) new BoundedHubPow2(requestedCapacity)
      else new BoundedHubPow2(requestedCapacity)
    }

    def unbounded[A]: Hub[A] =
      new UnboundedHub
  }

  private class BoundedHubPow2[A](requestedCapacity: Int) extends Hub[A] {
    private[this] val array = Array.ofDim[TRef[AnyRef]](requestedCapacity)
    // private[this] val mask             = requestedCapacity - 1
    private[this] val seq              = Array.ofDim[TRef[Long]](requestedCapacity)
    private[this] val publisherIndex   = ZTRef.unsafeMake(0L)
    private[this] val subscriberCount  = ZTRef.unsafeMake(0)
    private[this] val subscribers      = Array.ofDim[TRef[Int]](requestedCapacity)
    private[this] val subscribersIndex = ZTRef.unsafeMake(0L)
    (0 until requestedCapacity).foreach(n => array(n) = ZTRef.unsafeMake(null))
    (0 until requestedCapacity).foreach(n => seq(n) = ZTRef.unsafeMake(n.toLong))
    (0 until requestedCapacity).foreach(n => subscribers(n) = ZTRef.unsafeMake(0))

    def capacity: Int =
      requestedCapacity
    def isEmpty(journal: Journal): Boolean = {
      val currentPublisherIndex   = publisherIndex.unsafeGet(journal)
      val currentSubscribersIndex = subscribersIndex.unsafeGet(journal)
      currentPublisherIndex == currentSubscribersIndex
    }
    def isFull(journal: Journal): Boolean = {
      val currentPublisherIndex   = publisherIndex.unsafeGet(journal)
      val currentSubscribersIndex = subscribersIndex.unsafeGet(journal)
      currentPublisherIndex == currentSubscribersIndex + capacity
    }
    def publish(journal: Journal, a: A): Boolean = {
      val currentPublisherIndex = publisherIndex.unsafeGet(journal)
      val currentIndex          = (currentPublisherIndex % capacity).toInt
      val currentSeq            = seq(currentIndex).unsafeGet(journal)
      if (currentPublisherIndex == currentSeq) {
        array(currentIndex).unsafeSet(journal, a.asInstanceOf[AnyRef])
        seq(currentIndex).unsafeSet(journal, currentSeq + 1)
        publisherIndex.unsafeSet(journal, currentPublisherIndex + 1)
        val currentSubscriberCount = subscriberCount.unsafeGet(journal)
        subscribers(currentIndex).unsafeSet(journal, currentSubscriberCount)
        true
      } else false
    }
    def publishAll(journal: Journal, as: Iterable[A]): Chunk[A] =
      ???
    def size(journal: Journal): Int =
      ???
    def slide(journal: Journal): Unit =
      ???
    def subscribe(journal: Journal): Hub.Subscription[A] =
      new Hub.Subscription[A] {
        private[this] val currentPublisherIndex  = publisherIndex.unsafeGet(journal)
        private[this] val subscriberIndex        = ZTRef.unsafeMake(currentPublisherIndex)
        private[this] val unsubscribed           = ZTRef.unsafeMake(false)
        private[this] val currentSubscriberCount = subscriberCount.unsafeGet(journal)
        subscriberCount.unsafeSet(journal, currentSubscriberCount + 1)

        def isEmpty(journal: Journal): Boolean =
          ???
        def poll(journal: Journal, default: A): A = {
          val currentSubscriberIndex = subscriberIndex.unsafeGet(journal)
          val currentIndex           = (currentSubscriberIndex % capacity).toInt
          val currentSeq             = seq(currentIndex).unsafeGet(journal)
          if (currentSubscriberIndex + 1 == currentSeq) {
            val a = array(currentIndex).unsafeGet(journal)
            subscriberIndex.unsafeSet(journal, currentSubscriberIndex + 1)
            val currentSubscribers = subscribers(currentIndex).unsafeGet(journal)
            subscribers(currentIndex).unsafeSet(journal, currentSubscribers - 1)
            if (currentSubscribers == 1) {
              val currentSubscribersIndex = subscriberIndex.unsafeGet(journal)
              subscribersIndex.unsafeSet(journal, currentSubscribersIndex + 1)
              seq(currentIndex).unsafeSet(journal, currentSubscriberIndex + capacity)
            }
            a.asInstanceOf[A]
          } else default
        }
        def pollUpTo(journal: Journal, n: Int): Chunk[A] =
          ???
        def size(journal: Journal): Int =
          ???
        def unsubscribe(journal: Journal): Unit =
          ()
      }
  }

  private class BoundedHubArb[A](requestedCapacity: Int) extends Hub[A] {
    def capacity: Int =
      ???
    def isEmpty(journal: Journal): Boolean =
      ???
    def isFull(journal: Journal): Boolean =
      ???
    def publish(journal: Journal, a: A): Boolean =
      ???
    def publishAll(journal: Journal, as: Iterable[A]): Chunk[A] =
      ???
    def size(journal: Journal): Int =
      ???
    def slide(journal: Journal): Unit =
      ???
    def subscribe(journal: Journal): Hub.Subscription[A] =
      new Hub.Subscription[A] {
        def isEmpty(journal: Journal): Boolean =
          ???
        def poll(journal: Journal, default: A): A =
          ???
        def pollUpTo(journal: Journal, n: Int): Chunk[A] =
          ???
        def size(journal: Journal): Int =
          ???
        def unsubscribe(journal: Journal): Unit =
          ()
      }
  }

  private class BoundedHubSingle[A] extends Hub[A] {
    import BoundedHubSingle._

    private[this] val state = ZTRef.unsafeMake(State(0, 0, 0, null.asInstanceOf[A]))

    def capacity: Int =
      1
    def isEmpty(journal: Journal): Boolean =
      ???
    def isFull(journal: Journal): Boolean =
      ???
    def publish(journal: Journal, a: A): Boolean = {
      val currentState           = state.unsafeGet(journal)
      val currentSubscriberCount = currentState.subscriberCount
      val currentSubscribers     = currentState.subscribers
      if (currentState.subscribers != 0) false
      else if (currentState.subscriberCount == 0) true
      else {
        val currentPublisherIndex = currentState.publisherIndex
        val updatedState = currentState.copy(
          publisherIndex = currentPublisherIndex + 1,
          subscribers = currentSubscriberCount,
          value = a
        )
        state.unsafeSet(journal, updatedState)
        true
      }
    }
    def publishAll(journal: Journal, as: Iterable[A]): Chunk[A] =
      ???
    def size(journal: Journal): Int =
      ???
    def slide(journal: Journal): Unit =
      ???
    def subscribe(journal: Journal): Hub.Subscription[A] =
      new Hub.Subscription[A] {
        private[this] val currentState          = state.unsafeGet(journal)
        private[this] val currentPublisherIndex = currentState.publisherIndex
        private[this] val subscriberIndex       = ZTRef.unsafeMake(currentPublisherIndex)
        private[this] val unsubscribed          = ZTRef.unsafeMake(false)
        state.unsafeSet(journal, currentState.copy(subscriberCount = currentState.subscriberCount + 1))

        def isEmpty(journal: Journal): Boolean =
          ???
        def poll(journal: Journal, default: A): A = {
          val currentState           = state.unsafeGet(journal)
          val currentPublisherIndex  = currentState.publisherIndex
          val currentSubscriberIndex = subscriberIndex.unsafeGet(journal)
          if (currentSubscriberIndex < currentPublisherIndex) {
            if (currentState.subscribers == 1) {
              val updatedstate = currentState.copy(
                subscribers = 0,
                value = null.asInstanceOf[A]
              )
              state.unsafeSet(journal, updatedstate)
            } else {
              val updatedstate = currentState.copy(
                subscribers = currentState.subscribers - 1
              )
              state.unsafeSet(journal, updatedstate)
            }
            subscriberIndex.unsafeSet(journal, currentPublisherIndex)
            currentState.value
          } else {
            default
          }
        }
        def pollUpTo(journal: Journal, n: Int): Chunk[A] =
          ???
        def size(journal: Journal): Int =
          ???
        def unsubscribe(journal: Journal): Unit =
          ()
      }
  }

  private object BoundedHubSingle {
    final case class State[A](publisherIndex: Int, subscriberCount: Int, subscribers: Int, value: A)
  }

  private class UnboundedHub[A] extends Hub[A] {
    private[this] val empty         = ZTRef.unsafeMake[Node[A]](null)
    private[this] val publisherHead = ZTRef.unsafeMake(empty)
    private[this] val publisherTail = ZTRef.unsafeMake(empty)

    def capacity: Int =
      Int.MaxValue
    def isEmpty(journal: Journal): Boolean =
      ???
    def isFull(journal: Journal): Boolean =
      false
    def publish(journal: Journal, a: A): Boolean = {
      val currentPublisherTail = publisherTail.unsafeGet(journal)
      val empty                = ZTRef.unsafeMake[Node[A]](null)
      currentPublisherTail.unsafeSet(journal, Node(a, empty))
      publisherTail.unsafeSet(journal, empty)
      true
    }
    def publishAll(journal: Journal, as: Iterable[A]): Chunk[A] =
      ???
    def size(journal: Journal): Int =
      ???
    def slide(journal: Journal): Unit =
      ???
    def subscribe(journal: Journal): Hub.Subscription[A] =
      new Hub.Subscription[A] {
        private[this] val currentPublisherTail = publisherTail.unsafeGet(journal)
        private[this] val subscriberHead       = ZTRef.unsafeMake(currentPublisherTail)

        def isEmpty(journal: Journal): Boolean =
          ???
        def poll(journal: Journal, default: A): A = {
          val currentSubscriberHead = subscriberHead.unsafeGet(journal)
          val head                  = currentSubscriberHead.unsafeGet(journal)
          if (head eq null) throw ZSTM.RetryException
          else {
            subscriberHead.unsafeSet(journal, head.tail)
            head.head
          }

        }
        def pollUpTo(journal: Journal, n: Int): zio.Chunk[A] =
          ???
        def size(journal: Journal): Int =
          ???
        def unsubscribe(journal: Journal): Unit =
          ()
      }
  }

  final case class Node[A](head: A, tail: TRef[Node[A]])

  private def nextPow2(n: Int): Int = {
    val nextPow = (math.log(n.toDouble) / math.log(2.0)).ceil.toInt
    math.pow(2, nextPow.toDouble).toInt.max(2)
  }
}
