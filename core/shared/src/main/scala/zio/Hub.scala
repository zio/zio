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

package zio

import zio.internal.{MutableConcurrentQueue, Platform, Sync}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A `Hub` is an asynchronous message hub. Publishers can offer messages to the
 * hub and subscribers can subscribe to take messages from the hub.
 *
 * Subscribers do not compete for messages: each subscriber takes them on its
 * own, whereas each value offered to a [[Queue]] goes to one taker. With
 * `Hub.bounded` and `Hub.unbounded`, a subscriber receives each message
 * published while it is subscribed. With `Hub.dropping` and `Hub.sliding`, it
 * can miss the messages that the hub dropped while it was full. `subscribe`
 * returns a [[Dequeue]] that stays subscribed until its `Scope` closes. A
 * message published while there are no subscribers is not kept.
 *
 * A bounded hub keeps each message until every subscriber has taken it, or,
 * with `Hub.sliding`, until a newer message replaces it. When it is full, what
 * `publish` does depends on how the hub was made:
 *   - `Hub.bounded` suspends the publishing fiber until there is room (back
 *     pressure).
 *   - `Hub.dropping` drops the new message, and `publish` returns `false`.
 *   - `Hub.sliding` drops the oldest message to make room for the new one.
 *   - `Hub.unbounded` has a capacity of `Int.MaxValue`.
 *
 * {{{
 * val program: UIO[(Chunk[String], Chunk[String])] =
 *   ZIO.scoped {
 *     for {
 *       hub    <- Hub.bounded[String](8)
 *       first  <- hub.subscribe
 *       second <- hub.subscribe
 *       _      <- hub.publishAll(List("a", "b"))
 *       a      <- first.takeAll
 *       b      <- second.takeN(2)
 *     } yield (a, b)
 *   }
 * // succeeds with (Chunk(a,b),Chunk(a,b))
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/concurrency/hub/ Hub]]
 */
sealed abstract class Hub[A] extends Enqueue.Internal[A] {

  /**
   * Publishes a message to the hub, returning whether the message was published
   * to the hub. When the hub has no subscribers, it returns `true` and the
   * message is not kept. When the hub is full, a `bounded` hub suspends the
   * fiber until there is room, a `dropping` hub returns `false`, and a
   * `sliding` hub drops the oldest message and returns `true`. Interrupted when
   * the hub is shut down.
   */
  def publish(a: A)(implicit trace: Trace): UIO[Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning any messages
   * that were not published to the hub. Only a `dropping` hub returns messages:
   * a `bounded` hub suspends the fiber until all of them are published, and a
   * `sliding` hub drops older messages to make room. Interrupted when the hub
   * is shut down.
   */
  def publishAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription is
   * a [[Dequeue]] from which `take` and the other methods read the messages
   * published from now on, each one once. The subscription is shut down when
   * the scope closes, and `take` on it is then interrupted. On a hub that is
   * shut down, it returns a subscription that is already shut down.
   */
  def subscribe(implicit trace: Trace): ZIO[Scope, Nothing, Dequeue[A]]

  override final def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == 0)

  override final def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == capacity)

  /**
   * Publishes a message to the hub, as `publish` does.
   */
  final def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
    publish(a)

  /**
   * Publishes the messages to the hub, as `publishAll` does.
   */
  final def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
    publishAll(as)
}

/**
 * Constructors for [[Hub]]: `bounded`, `dropping`, `sliding` and `unbounded`.
 */
object Hub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub will retain
   * messages until they have been taken by all subscribers, applying back
   * pressure to publishers if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two. The capacity is
   * not rounded. The returned effect dies with an `IllegalArgumentException`
   * when `requestedCapacity` is less than 1.
   */
  def bounded[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.suspendSucceed {
      val hub = internal.Hub.bounded[A](requestedCapacity)

      makeHub(hub, Strategy.BackPressure())
    }

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two. The capacity is
   * not rounded. The returned effect dies with an `IllegalArgumentException`
   * when `requestedCapacity` is less than 1.
   */
  def dropping[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.suspendSucceed {
      val hub = internal.Hub.bounded[A](requestedCapacity)

      makeHub(hub, Strategy.Dropping())
    }

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two. The capacity is
   * not rounded. The returned effect dies with an `IllegalArgumentException`
   * when `requestedCapacity` is less than 1.
   */
  def sliding[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.suspendSucceed {
      val hub = internal.Hub.bounded[A](requestedCapacity)

      makeHub(hub, Strategy.Sliding())
    }

  /**
   * Creates an unbounded hub. Its `capacity` is `Int.MaxValue`.
   */
  def unbounded[A](implicit trace: Trace): UIO[Hub[A]] =
    ZIO.suspendSucceed {
      val hub = internal.Hub.unbounded[A]

      makeHub(hub, Strategy.Dropping())
    }

  /**
   * Creates a hub with the specified strategy.
   */
  private def makeHub[A](hub: internal.Hub[A], strategy: Strategy[A])(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.fiberIdWith { fiberId =>
      Exit.succeed {
        val scope   = Scope.unsafe.make(Unsafe)
        val promise = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)

        unsafeMakeHub(
          hub,
          Platform
            .newConcurrentSet[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]()(Unsafe),
          scope,
          promise,
          new AtomicBoolean(false),
          strategy
        )
      }
    }

  /**
   * Unsafely creates a hub with the specified strategy.
   */
  private def unsafeMakeHub[A](
    hub: internal.Hub[A],
    subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
    scope: Scope.Closeable,
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Hub[A] =
    new Hub[A] {
      def awaitShutdown(implicit trace: Trace): UIO[Unit] =
        shutdownHook.await
      val capacity: Int =
        hub.capacity
      def isShutdown(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed(shutdownFlag.get)
      def publish(a: A)(implicit trace: Trace): UIO[Boolean] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else if (hub.publish(a)) {
            strategy.unsafeCompleteSubscribers(hub, subscribers)
            Exit.`true`
          } else {
            strategy.handleSurplus(hub, subscribers, Chunk.single(a), shutdownFlag)
          }
        }
      def publishAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val surplus = unsafePublishAll(hub, as)
            strategy.unsafeCompleteSubscribers(hub, subscribers)
            if (surplus.isEmpty) Exit.emptyChunk
            else
              strategy
                .handleSurplus(hub, subscribers, surplus, shutdownFlag)
                .map(published => if (published) Chunk.empty else surplus)
          }
        }
      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberIdWith { fiberId =>
          shutdownFlag.set(true)
          ZIO
            .whenZIODiscard(shutdownHook.succeedUnit) {
              scope.close(Exit.interrupt(fiberId)) *> strategy.shutdown
            }
        }.uninterruptible
      def size(implicit trace: Trace): UIO[Int] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else Exit.succeed(hub.size())
        }
      def subscribe(implicit trace: Trace): ZIO[Scope, Nothing, Dequeue[A]] =
        ZIO.acquireReleaseExit {
          for {
            child   <- scope.fork
            dequeue <- makeSubscription(hub, subscribers, strategy)
            _       <- child.addFinalizer(dequeue.shutdown)
          } yield (dequeue, child)
        } { case ((_, scope), exit) =>
          scope.close(exit)
        }.map(_._1)
    }

  /**
   * Creates a subscription with the specified strategy.
   */
  private def makeSubscription[A](
    hub: internal.Hub[A],
    subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
    strategy: Strategy[A]
  )(implicit trace: Trace): UIO[Dequeue[A]] =
    ZIO.fiberIdWith { fiberId =>
      Exit.succeed {
        val promise = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)

        unsafeMakeSubscription(
          hub,
          subscribers,
          hub.subscribe(),
          MutableConcurrentQueue.unbounded[Promise[Nothing, A]],
          promise,
          new AtomicBoolean(false),
          strategy
        )
      }
    }

  /**
   * Unsafely creates a subscription with the specified strategy.
   */
  private def unsafeMakeSubscription[A](
    hub: internal.Hub[A],
    subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
    subscription: internal.Hub.Subscription[A],
    pollers: MutableConcurrentQueue[Promise[Nothing, A]],
    shutdownHook: Promise[Nothing, Unit],
    shutdownFlag: AtomicBoolean,
    strategy: Strategy[A]
  ): Dequeue[A] =
    new Dequeue.Internal[A] { self =>
      def awaitShutdown(implicit trace: Trace): UIO[Unit] =
        shutdownHook.await
      val capacity: Int =
        hub.capacity
      def isShutdown(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed(shutdownFlag.get)
      def offer(a: Nothing)(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed(false)
      def offerAll[A1 <: Nothing](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
        ZIO.succeed(Chunk.fromIterable(as))
      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberIdWith { fiberId =>
          shutdownFlag.set(true)
          ZIO
            .whenZIODiscard(shutdownHook.succeedUnit) {
              ZIO.foreachParDiscard(unsafePollAll(pollers))(_.interruptAs(fiberId)) *>
                Exit.succeed {
                  subscribers.remove(subscription -> pollers)
                  subscription.unsubscribe()
                  strategy.unsafeOnHubEmptySpace(hub, subscribers)
                }
            }
        }.uninterruptible
      def size(implicit trace: Trace): UIO[Int] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else Exit.succeed(subscription.size())
        }
      def take(implicit trace: Trace): UIO[A] =
        ZIO.fiberIdWith { fiberId =>
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val empty   = null.asInstanceOf[A]
            val message = if (pollers.isEmpty()) subscription.poll(empty) else empty
            message match {
              case null =>
                val promise = Promise.unsafe.make[Nothing, A](fiberId)(Unsafe)
                ZIO.suspendSucceed {
                  pollers.offer(promise)
                  subscribers.add(subscription -> pollers)
                  strategy.unsafeCompletePollers(hub, subscribers, subscription, pollers)
                  if (shutdownFlag.get) ZIO.interrupt else promise.await
                }.onInterrupt(ZIO.succeed(unsafeRemove(pollers, promise)))
              case a =>
                strategy.unsafeOnHubEmptySpace(hub, subscribers)
                Exit.succeed(a)
            }
          }
        }
      def takeAll(implicit trace: Trace): ZIO[Any, Nothing, Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val as = if (pollers.isEmpty()) Exit.succeed(unsafePollAll(subscription)) else Exit.emptyChunk
            strategy.unsafeOnHubEmptySpace(hub, subscribers)
            as
          }
        }
      def takeUpTo(max: Int)(implicit trace: Trace): ZIO[Any, Nothing, Chunk[A]] =
        ZIO.suspendSucceed {
          if (shutdownFlag.get) ZIO.interrupt
          else {
            val as = if (pollers.isEmpty()) Exit.succeed(unsafePollN(subscription, max)) else Exit.emptyChunk
            strategy.unsafeOnHubEmptySpace(hub, subscribers)
            as
          }
        }
    }

  /**
   * A `Strategy[A]` describes the protocol for how publishers and subscribers
   * will communicate with each other through the hub.
   */
  private[zio] sealed abstract class Strategy[A] {

    /**
     * Describes what happens to the messages that did not fit in the hub: the
     * back pressure strategy suspends the publisher until they are published,
     * the dropping strategy drops them, and the sliding strategy drops older
     * messages to make room for them.
     */
    def handleSurplus(
      hub: internal.Hub[A],
      subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
      as: Iterable[A],
      isShutdown: AtomicBoolean
    )(implicit trace: Trace): UIO[Boolean]

    /**
     * Describes any finalization logic associated with this strategy.
     */
    def shutdown(implicit trace: Trace): UIO[Unit]

    /**
     * Describes how subscribers should signal to publishers waiting for space
     * to become available in the hub that space may be available.
     */
    def unsafeOnHubEmptySpace(
      hub: internal.Hub[A],
      subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
    ): Unit

    /**
     * Describes how subscribers waiting for additional values from the hub
     * should take those values and signal to publishers that they are no longer
     * waiting for additional values.
     */
    final def unsafeCompletePollers(
      hub: internal.Hub[A],
      subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
      subscription: internal.Hub.Subscription[A],
      pollers: MutableConcurrentQueue[Promise[Nothing, A]]
    ): Unit = {
      var keepPolling = true
      val nullPoller  = null.asInstanceOf[Promise[Nothing, A]]
      val empty       = null.asInstanceOf[A]

      while (keepPolling && !subscription.isEmpty()) {
        val poller = pollers.poll(nullPoller)
        if (poller eq nullPoller) {
          subscribers.remove(subscription -> pollers)
          if (pollers.isEmpty()) keepPolling = false
          else subscribers.add(subscription -> pollers)
        } else {
          subscription.poll(empty) match {
            case null =>
              unsafeOfferAll(pollers, poller +: unsafePollAll(pollers))
            case a =>
              unsafeCompletePromise(poller, a)
              unsafeOnHubEmptySpace(hub, subscribers)
          }
        }
      }
    }

    /**
     * Describes how publishers should signal to subscribers waiting for
     * additional values from the hub that new values are available.
     */
    final def unsafeCompleteSubscribers(
      hub: internal.Hub[A],
      subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
    ): Unit = {
      val iterator = subscribers.iterator
      while (iterator.hasNext) {
        val (subscription, pollers) = iterator.next()
        unsafeCompletePollers(hub, subscribers, subscription, pollers)
      }
    }
  }

  private[zio] object Strategy {

    /**
     * A strategy that applies back pressure to publishers when the hub is at
     * capacity. This guarantees that all subscribers will receive all messages
     * published to the hub while they are subscribed. However, it creates the
     * risk that a slow subscriber will slow down the rate at which messages are
     * published and received by other subscribers.
     */
    final case class BackPressure[A]() extends Strategy[A] {
      val publishers: MutableConcurrentQueue[(A, Promise[Nothing, Boolean], Boolean)] =
        MutableConcurrentQueue.unbounded[(A, Promise[Nothing, Boolean], Boolean)]

      def handleSurplus(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutDown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          val promise = Promise.unsafe.make[Nothing, Boolean](fiberId)(Unsafe)
          ZIO.suspendSucceed {
            unsafeOffer(as, promise)
            unsafeOnHubEmptySpace(hub, subscribers)
            unsafeCompleteSubscribers(hub, subscribers)
            if (isShutDown.get) ZIO.interrupt else promise.await
          }.onInterrupt(ZIO.succeed(unsafeRemove(promise)))
        }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.fiberIdWith { fiberId =>
          ZIO.foreachParDiscard(unsafePollAll(publishers)) { case (_, promise, last) =>
            if (last) promise.interruptAs(fiberId) else Exit.unit
          }
        }

      def unsafeOnHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      ): Unit = {
        val empty       = null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]
        var keepPolling = true

        while (keepPolling && !hub.isFull()) {
          val publisher = publishers.poll(empty)
          if (publisher eq null) keepPolling = false
          else {
            val published = hub.publish(publisher._1)
            if (published && publisher._3) {
              unsafeCompletePromise(publisher._2, true)
            } else if (!published) {
              unsafeOfferAll(publishers, publisher +: unsafePollAll(publishers))
            }
            unsafeCompleteSubscribers(hub, subscribers)
          }
        }

      }

      private def unsafeOffer(as: Iterable[A], promise: Promise[Nothing, Boolean]): Unit =
        if (as.nonEmpty) {
          val iterator = as.iterator
          var a        = iterator.next()
          while (iterator.hasNext) {
            publishers.offer((a, promise, false))
            a = iterator.next()
          }
          publishers.offer((a, promise, true))
          ()
        }

      private def unsafeRemove(promise: Promise[Nothing, Boolean]): Unit = {
        unsafeOfferAll(publishers, unsafePollAll(publishers).filterNot(_._2 == promise))
        ()
      }
    }

    /**
     * A strategy that drops new messages when the hub is at capacity. This
     * guarantees that a slow subscriber will not slow down the rate at which
     * messages are published. However, it creates the risk that a slow
     * subscriber will slow down the rate at which messages are received by
     * other subscribers and that subscribers may not receive all messages
     * published to the hub while they are subscribed.
     */
    final case class Dropping[A]() extends Strategy[A] {

      def handleSurplus(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed(false)

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.unit

      def unsafeOnHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      ): Unit =
        ()
    }

    /**
     * A strategy that adds new messages and drops old messages when the hub is
     * at capacity. This guarantees that a slow subscriber will not slow down
     * the rate at which messages are published and received by other
     * subscribers. However, it creates the risk that a slow subscriber will not
     * receive some messages published to the hub while it is subscribed.
     */
    final case class Sliding[A]() extends Strategy[A] {

      def handleSurplus(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeed {
          unsafeSlidingPublish(as, hub)
          unsafeCompleteSubscribers(hub, subscribers)
          true
        }

      /**
       * Publishes values to the hub, dropping the oldest values to make room
       * for the new ones.
       *
       * Only the last `capacity` values can survive, so we take those and
       * publish them in bulk. There is no bulk `slide`, but `publishAll`
       * reserves its space in one compare-and-set on the bounded hubs, rather
       * than racing for one slot per value.
       *
       * We slide only the shortfall, the room the hub lacks, rather than once
       * per value we still hold. Sliding `remaining.length` times every round
       * would drop whatever occupies those slots now, which on a retry round
       * can be values a concurrent publisher has already been told were
       * accepted. `size` is approximate under concurrency, so the shortfall is
       * a hint: too low costs another round, while too high destroys someone
       * else's values, and we bias to the former.
       *
       * `publishAll` returns the values it could not place, which is what we
       * retry with, never fewer, so a value is not dropped without being
       * published. If a whole round places nothing then a concurrent publisher
       * took the space we just freed, so hint that we are spinning.
       *
       * Exposed for testing.
       */
      private[zio] def unsafeSlidingPublish(as: Iterable[A], hub: internal.Hub[A]): Unit = {
        val capacity = hub.capacity
        if (as.nonEmpty && capacity > 0) {
          val chunk = Chunk.fromIterable(as)
          if (chunk.length == 1) unsafeSlidingPublishOne(chunk(0), hub)
          else {
            var remaining = chunk.takeRight(capacity)
            while (remaining.nonEmpty) {
              unsafeSlide(hub, remaining.length - (capacity - hub.size()))
              val surplus = hub.publishAll(remaining)
              if (surplus.length == remaining.length) Sync.onSpinWait()
              remaining = surplus
            }
          }
        }
      }

      /**
       * The single value case, which `publish` always takes. Going through the
       * bulk path would allocate a slice and a surplus chunk to move one value,
       * so slide and publish it directly.
       */
      private def unsafeSlidingPublishOne(a: A, hub: internal.Hub[A]): Unit = {
        var loop = true
        while (loop) {
          if (hub.size() >= hub.capacity) hub.slide()
          if (hub.publish(a)) loop = false
          else Sync.onSpinWait()
        }
      }

      /**
       * Slides up to `n` values out. There's no bulk `slide`, so this drops
       * them one at a time, mirroring `Queue.Strategy.Sliding.unsafeDrop`.
       */
      private def unsafeSlide(hub: internal.Hub[A], n: Int): Unit = {
        var i = n
        while (i > 0) {
          hub.slide()
          i -= 1
        }
      }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.unit

      def unsafeOnHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: java.util.Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      ): Unit =
        ()
    }
  }

  /**
   * Unsafely completes a promise with the specified value.
   */
  private def unsafeCompletePromise[A](promise: Promise[Nothing, A], a: A): Unit =
    promise.unsafe.done(Exit.succeed(a))(Unsafe)

  /**
   * Unsafely offers the specified values to a queue.
   */
  private def unsafeOfferAll[A](queue: MutableConcurrentQueue[A], as: Iterable[A]): Chunk[A] =
    queue.offerAll(as)

  /**
   * Unsafely polls all values from a queue.
   */
  private def unsafePollAll[A](queue: MutableConcurrentQueue[A]): Chunk[A] =
    queue.pollUpTo(Int.MaxValue)

  /**
   * Unsafely polls all values from a subscription.
   */
  private def unsafePollAll[A](subscription: internal.Hub.Subscription[A]): Chunk[A] =
    subscription.pollUpTo(Int.MaxValue)

  /**
   * Unsafely polls up to the specified number of values from a subscription.
   */
  private def unsafePollN[A](subscription: internal.Hub.Subscription[A], max: Int): Chunk[A] =
    subscription.pollUpTo(max)

  /**
   * Unsafely publishes the specified values to a hub.
   */
  private def unsafePublishAll[A, B <: A](hub: internal.Hub[A], as: Iterable[B]): Chunk[B] =
    hub.publishAll(as)

  /**
   * Unsafely removes the specified item, and any item equal to it, from a
   * queue.
   */
  private def unsafeRemove[A](queue: MutableConcurrentQueue[A], a: A): Unit = {
    unsafeOfferAll(queue, unsafePollAll(queue).filterNot(_ == a))
    ()
  }
}
