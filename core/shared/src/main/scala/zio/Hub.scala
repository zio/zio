/*
 * Copyright 2021-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.{MutableConcurrentQueue, Platform}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.Set
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A `Hub` is an asynchronous message hub. Publishers can offer messages to the
 * hub and subscribers can subscribe to take messages from the hub.
 */
abstract class Hub[A] extends Enqueue[A] {

  /**
   * Publishes a message to the hub, returning whether the message was published
   * to the hub.
   */
  def publish(a: A)(implicit trace: Trace): UIO[Boolean]

  /**
   * Publishes all of the specified messages to the hub, returning any messages
   * that were not published to the hub.
   */
  def publishAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]]

  /**
   * Subscribes to receive messages from the hub. The resulting subscription can
   * be evaluated multiple times within the scope to take a message from the hub
   * each time.
   */
  def subscribe(implicit trace: Trace): ZIO[Scope, Nothing, Dequeue[A]]

  override final def isEmpty(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == 0)

  override final def isFull(implicit trace: Trace): UIO[Boolean] =
    size.map(_ == capacity)

  final def offer(a: A)(implicit trace: Trace): UIO[Boolean] =
    publish(a)

  final def offerAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
    publishAll(as)
}

object Hub {

  /**
   * Creates a bounded hub with the back pressure strategy. The hub will retain
   * messages until they have been taken by all subscribers, applying back
   * pressure to publishers if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def bounded[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.succeed(internal.Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.BackPressure()))

  /**
   * Creates a bounded hub with the dropping strategy. The hub will drop new
   * messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def dropping[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.succeed(internal.Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.Dropping()))

  /**
   * Creates a bounded hub with the sliding strategy. The hub will add new
   * messages and drop old messages if the hub is at capacity.
   *
   * For best performance use capacities that are powers of two.
   */
  def sliding[A](requestedCapacity: => Int)(implicit trace: Trace): UIO[Hub[A]] =
    ZIO.succeed(internal.Hub.bounded[A](requestedCapacity)).flatMap(makeHub(_, Strategy.Sliding()))

  /**
   * Creates an unbounded hub.
   */
  def unbounded[A](implicit trace: Trace): UIO[Hub[A]] =
    ZIO.succeed(internal.Hub.unbounded[A]).flatMap(makeHub(_, Strategy.Dropping()))

  /**
   * Creates a hub with the specified strategy.
   */
  private def makeHub[A](hub: internal.Hub[A], strategy: Strategy[A])(implicit trace: Trace): UIO[Hub[A]] =
    Scope.make.flatMap { scope =>
      Promise.make[Nothing, Unit].map { promise =>
        Unsafe.unsafeCompat { implicit u =>
          unsafe.makeHub(
            hub,
            Platform.newConcurrentSet[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])](),
            scope,
            promise,
            new AtomicBoolean(false),
            strategy
          )
        }
      }
    }

  /**
   * Creates a subscription with the specified strategy.
   */
  private def makeSubscription[A](
    hub: internal.Hub[A],
    subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
    strategy: Strategy[A]
  )(implicit trace: Trace): UIO[Dequeue[A]] =
    Promise.make[Nothing, Unit].map { promise =>
      Unsafe.unsafeCompat { implicit u =>
        unsafe.makeSubscription(
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
   * A `Strategy[A]` describes the protocol for how publishers and subscribers
   * will communicate with each other through the hub.
   */
  private sealed abstract class Strategy[A] {

    /**
     * Describes how publishers should signal to subscribers that they are
     * waiting for space to become available in the hub.
     */
    def handleSurplus(
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
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
    def onHubEmptySpace(
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
    )(implicit unsafe: Unsafe[Any]): Unit

    /**
     * Describes how subscribers waiting for additional values from the hub
     * should take those values and signal to publishers that they are no longer
     * waiting for additional values.
     */
    final def completePollers(
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
      subscription: internal.Hub.Subscription[A],
      pollers: MutableConcurrentQueue[Promise[Nothing, A]]
    )(implicit unsafe: Unsafe[Any]): Unit = {
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
              Hub.unsafe.offerAll(pollers, poller +: Hub.unsafe.pollAll(pollers))
            case a =>
              Hub.unsafe.completePromise(poller, a)
              onHubEmptySpace(hub, subscribers)
          }
        }
      }
    }

    /**
     * Describes how publishers should signal to subscribers waiting for
     * additional values from the hub that new values are available.
     */
    final def completeSubscribers(
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
    )(implicit unsafe: Unsafe[Any]): Unit = {
      val iterator = subscribers.iterator
      while (iterator.hasNext) {
        val (subscription, pollers) = iterator.next()
        completePollers(hub, subscribers, subscription, pollers)
      }
    }
  }

  private object Strategy {

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
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutDown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.fiberIdWith { fiberId =>
          Unsafe.unsafeCompat { implicit u =>
            val promise = Promise.unsafe.make[Nothing, Boolean](fiberId)
            ZIO.suspendSucceed {
              offer(as, promise)
              onHubEmptySpace(hub, subscribers)
              completeSubscribers(hub, subscribers)
              if (isShutDown.get) ZIO.interrupt else promise.await
            }.onInterrupt(ZIO.succeed(remove(promise)))
          }
        }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        for {
          fiberId    <- ZIO.fiberId
          publishers <- ZIO.succeedUnsafe(implicit u => unsafe.pollAll(publishers))
          _ <- ZIO.foreachParDiscard(publishers) { case (_, promise, last) =>
                 if (last) promise.interruptAs(fiberId) else ZIO.unit
               }
        } yield ()

      def onHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      )(implicit unsafe: Unsafe[Any]): Unit = {
        val empty       = null.asInstanceOf[(A, Promise[Nothing, Boolean], Boolean)]
        var keepPolling = true

        while (keepPolling && !hub.isFull()) {
          val publisher = publishers.poll(empty)
          if (publisher eq null) keepPolling = false
          else {
            val published = hub.publish(publisher._1)
            if (published && publisher._3) {
              Hub.unsafe.completePromise(publisher._2, true)
            } else if (!published) {
              Hub.unsafe.offerAll(publishers, publisher +: Hub.unsafe.pollAll(publishers))
            }
            completeSubscribers(hub, subscribers)
          }
        }

      }

      private def offer(as: Iterable[A], promise: Promise[Nothing, Boolean])(implicit unsafe: Unsafe[Any]): Unit =
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

      private def remove(promise: Promise[Nothing, Boolean])(implicit unsafe: Unsafe[Any]): Unit = {
        Hub.unsafe.offerAll(publishers, Hub.unsafe.pollAll(publishers).filterNot(_._2 == promise))
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
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] =
        ZIO.succeedNow(false)

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.unit

      def onHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      )(implicit unsafe: Unsafe[Any]): Unit =
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
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
        as: Iterable[A],
        isShutdown: AtomicBoolean
      )(implicit trace: Trace): UIO[Boolean] = {
        def slidingPublish(as: Iterable[A])(implicit unsafe: Unsafe[Any]): Unit =
          if (as.nonEmpty && hub.capacity > 0) {
            val iterator = as.iterator
            var a        = iterator.next()
            var loop     = true
            while (loop) {
              hub.slide()
              val published = hub.publish(a)
              if (published && iterator.hasNext) {
                a = iterator.next()
              } else if (published && !iterator.hasNext) {
                loop = false
              }
            }
          }

        ZIO.succeedUnsafe { implicit u =>
          slidingPublish(as)
          completeSubscribers(hub, subscribers)
          true
        }
      }

      def shutdown(implicit trace: Trace): UIO[Unit] =
        ZIO.unit

      def onHubEmptySpace(
        hub: internal.Hub[A],
        subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])]
      )(implicit unsafe: Unsafe[Any]): Unit =
        ()
    }
  }

  private object unsafe { self =>

    /**
     * Unsafely creates a hub with the specified strategy.
     */
    def makeHub[A](
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
      scope: Scope.Closeable,
      shutdownHook: Promise[Nothing, Unit],
      shutdownFlag: AtomicBoolean,
      strategy: Strategy[A]
    )(implicit unsafe: Unsafe[Any]): Hub[A] =
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
              Unsafe.unsafeCompat { implicit unsafe =>
                strategy.completeSubscribers(hub, subscribers)
              }
              ZIO.succeedNow(true)
            } else {
              strategy.handleSurplus(hub, subscribers, Chunk(a), shutdownFlag)
            }
          }
        def publishAll[A1 <: A](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
          ZIO.suspendSucceed {
            if (shutdownFlag.get) ZIO.interrupt
            else {
              Unsafe.unsafeCompat { implicit unsafe =>
                val surplus = self.publishAll(hub, as)
                strategy.completeSubscribers(hub, subscribers)
                if (surplus.isEmpty) ZIO.succeedNow(Chunk.empty)
                else
                  strategy.handleSurplus(hub, subscribers, surplus, shutdownFlag).map { published =>
                    if (published) Chunk.empty else surplus
                  }
              }
            }
          }
        def shutdown(implicit trace: Trace): UIO[Unit] =
          ZIO.fiberIdWith { fiberId =>
            shutdownFlag.set(true)
            ZIO
              .whenZIO(shutdownHook.succeed(())) {
                scope.close(Exit.interrupt(fiberId)) *> strategy.shutdown
              }
              .unit
          }.uninterruptible
        def size(implicit trace: Trace): UIO[Int] =
          ZIO.suspendSucceed {
            if (shutdownFlag.get) ZIO.interrupt
            else ZIO.succeedNow(hub.size())
          }
        def subscribe(implicit trace: Trace): ZIO[Scope, Nothing, Dequeue[A]] =
          ZIO.acquireRelease {
            Hub.makeSubscription(hub, subscribers, strategy).tap { dequeue =>
              scope.addFinalizer(dequeue.shutdown)
            }
          } { dequeue =>
            dequeue.shutdown
          }
      }

    /**
     * Unsafely creates a subscription with the specified strategy.
     */
    def makeSubscription[A](
      hub: internal.Hub[A],
      subscribers: Set[(internal.Hub.Subscription[A], MutableConcurrentQueue[Promise[Nothing, A]])],
      subscription: internal.Hub.Subscription[A],
      pollers: MutableConcurrentQueue[Promise[Nothing, A]],
      shutdownHook: Promise[Nothing, Unit],
      shutdownFlag: AtomicBoolean,
      strategy: Strategy[A]
    )(implicit unsafe: Unsafe[Any]): Dequeue[A] =
      new Dequeue[A] { self =>
        def awaitShutdown(implicit trace: Trace): UIO[Unit] =
          shutdownHook.await
        val capacity: Int =
          hub.capacity
        def isShutdown(implicit trace: Trace): UIO[Boolean] =
          ZIO.succeed(shutdownFlag.get)
        def offer(a: Nothing)(implicit trace: Trace): UIO[Boolean] =
          ZIO.succeedNow(false)
        def offerAll[A1 <: Nothing](as: Iterable[A1])(implicit trace: Trace): UIO[Chunk[A1]] =
          ZIO.succeedNow(Chunk.fromIterable(as))
        def shutdown(implicit trace: Trace): UIO[Unit] =
          ZIO.fiberIdWith { fiberId =>
            shutdownFlag.set(true)
            ZIO
              .whenZIO(shutdownHook.succeed(())) {
                ZIO.foreachPar(Hub.unsafe.pollAll(pollers))(_.interruptAs(fiberId)) *>
                  ZIO.succeed(subscription.unsubscribe()) *>
                  ZIO.succeed(strategy.onHubEmptySpace(hub, subscribers))
              }
              .unit
          }.uninterruptible
        def size(implicit trace: Trace): UIO[Int] =
          ZIO.suspendSucceed {
            if (shutdownFlag.get) ZIO.interrupt
            else ZIO.succeedNow(subscription.size())
          }
        def take(implicit trace: Trace): UIO[A] =
          ZIO.fiberIdWith { fiberId =>
            if (shutdownFlag.get) ZIO.interrupt
            else {
              val empty   = null.asInstanceOf[A]
              val message = if (pollers.isEmpty()) subscription.poll(empty) else empty
              message match {
                case null =>
                  val promise = Promise.unsafe.make[Nothing, A](fiberId)
                  ZIO.suspendSucceed {
                    pollers.offer(promise)
                    subscribers.add(subscription -> pollers)
                    strategy.completePollers(hub, subscribers, subscription, pollers)
                    if (shutdownFlag.get) ZIO.interrupt else promise.await
                  }.onInterrupt(ZIO.succeed(Hub.unsafe.remove(pollers, promise)))
                case a =>
                  strategy.onHubEmptySpace(hub, subscribers)
                  ZIO.succeedNow(a)
              }
            }
          }
        def takeAll(implicit trace: Trace): ZIO[Any, Nothing, Chunk[A]] =
          ZIO.suspendSucceed {
            if (shutdownFlag.get) ZIO.interrupt
            else {
              val as = if (pollers.isEmpty()) Hub.unsafe.pollAll(subscription) else Chunk.empty
              strategy.onHubEmptySpace(hub, subscribers)
              ZIO.succeedNow(as)
            }
          }
        def takeUpTo(max: Int)(implicit trace: Trace): ZIO[Any, Nothing, Chunk[A]] =
          ZIO.suspendSucceed {
            if (shutdownFlag.get) ZIO.interrupt
            else {
              val as = if (pollers.isEmpty()) Hub.unsafe.pollN(subscription, max) else Chunk.empty
              strategy.onHubEmptySpace(hub, subscribers)
              ZIO.succeedNow(as)
            }
          }
      }

    /**
     * Unsafely completes a promise with the specified value.
     */
    def completePromise[A](promise: Promise[Nothing, A], a: A)(implicit unsafe: Unsafe[Any]): Unit =
      promise.unsafe.done(ZIO.succeedNow(a))

    /**
     * Unsafely offers the specified values to a queue.
     */
    def offerAll[A](queue: MutableConcurrentQueue[A], as: Iterable[A])(implicit unsafe: Unsafe[Any]): Chunk[A] =
      queue.offerAll(as)

    /**
     * Unsafely polls all values from a queue.
     */
    def pollAll[A](queue: MutableConcurrentQueue[A])(implicit unsafe: Unsafe[Any]): Chunk[A] =
      queue.pollUpTo(Int.MaxValue)

    /**
     * Unsafely polls all values from a subscription.
     */
    def pollAll[A](subscription: internal.Hub.Subscription[A])(implicit unsafe: Unsafe[Any]): Chunk[A] =
      subscription.pollUpTo(Int.MaxValue)

    /**
     * Unsafely polls the specified number of values from a subscription.
     */
    def pollN[A](subscription: internal.Hub.Subscription[A], max: Int)(implicit unsafe: Unsafe[Any]): Chunk[A] =
      subscription.pollUpTo(max)

    /**
     * Unsafely publishes the specified values to a hub.
     */
    def publishAll[A, B <: A](hub: internal.Hub[A], as: Iterable[B])(implicit unsafe: Unsafe[Any]): Chunk[B] =
      hub.publishAll(as)

    /**
     * Unsafely removes the specified item from a queue.
     */
    def remove[A](queue: MutableConcurrentQueue[A], a: A)(implicit unsafe: Unsafe[Any]): Unit = {
      offerAll(queue, pollAll(queue).filterNot(_ == a))
      ()
    }
  }
}
