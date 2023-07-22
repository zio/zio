---
id: hub
title: "Hub"
---

A `Hub[A]` is an asynchronous message hub. Publishers can publish messages of type `A` to the hub and subscribers can subscribe to receive messages of type `A` from the hub.

Unlike a `Queue`, where each value offered to the queue can be taken by _one_ taker, each value published to a hub can be received by _all_ subscribers. Whereas a `Queue` represents the optimal solution to the problem of how to _distribute_ values, a `Hub` represents the optimal solution to the problem of how to _broadcast_ them.

The fundamental operators on a `Hub` are `publish` and `subscribe`:

```scala
import zio._

trait Hub[A] {
  def publish(a: A): UIO[Boolean]
  def subscribe: ZManaged[Any, Nothing, Dequeue[A]]
}
```

The `publish` operator returns a `ZIO` effect that publishes a message of type `A` to the hub and succeeds with a value describing whether the message was successfully published to the hub.

The `subscribe` operator returns a `ZManaged` effect where the `acquire` action of the `ZManaged` subscribes to the hub and the `release` action unsubscribes from the hub. Within the context of the `ZManaged` we have access to a `Dequeue`, which is a `Queue` that can only be dequeued from, that allows us to take messages published to the hub.

For example, we can use a hub to broadcast a message to multiple subscribers like this:

```scala
Hub.bounded[String](2).flatMap { hub =>
  hub.subscribe.zip(hub.subscribe).use { case (left, right) =>
    for {
      _ <- hub.publish("Hello from a hub!")
      _ <- left.take.flatMap(console.putStrLn(_))
      _ <- right.take.flatMap(console.putStrLn(_))
    } yield ()
  }
}
```

A subscriber will only receive messages that are published to the hub while it is subscribed. So if we want to make sure that a particular message is received by a subscriber we must take care that the subscription has completed before publishing the message to the hub.

We can do this by publishing a message to the hub within the scope of the subscription as in the example above or by using other coordination mechanisms such as completing a `Promise`  when the `acquire` action of the `ZManaged` has completed.

Of course, in many cases such as subscribing to receive real time data we may not care about this because we are happy to just pick up with the most recent messages after we have subscribed. But for testing and simple applications this can be an important point to keep in mind.

## Constructing Hubs

The most common way to create a hub is with the `bounded` constructor, which returns an effect that creates a new hub with the specified requested capacity.

```scala
def bounded[A](requestedCapacity: Int): UIO[Hub[A]] =
  ???
```

For maximum efficiency you should create hubs with capacities that are powers of two.

Just like a bounded queue, a bounded hub applies back pressure to publishers when it is at capacity, so publishers will semantically block on calls to `publish` if the hub is full.

The advantage of the back pressure strategy is that it guarantees that all subscribers will receive all messages published to the hub while they are subscribed. However, it does create the risk that a slow subscriber will slow down the rate at which messages are published and received by other subscribers.

If you do not want this you can create a hub with the `dropping` constructor.

```scala
def dropping[A](requestedCapacity: Int): UIO[Hub[A]] =
  ???
```

A dropping hub will simply drop values published to it if the hub is at capacity, returning `false` on calls to `publish` if the hub is full to signal that the value was not successfully published.

The advantage of the dropping strategy is that publishers can continue to publish new values so when there is space in the hub the newest values can be published to the hub. However, subscribers are no longer guaranteed to receive all values published to the hub and a slow subscriber can still prevent messages from being published to the hub and received by other subscribers.

You can also create a hub with the `sliding` constructor.

```scala
def sliding[A](requestedCapacity: Int): UIO[Hub[A]] =
  ???
```

A sliding hub will drop the oldest value if a new value is published to it and the hub is at capacity, so publishing will always succeed immediately.

The advantage of the sliding strategy is that a slow subscriber cannot slow down that rate at which messages are published to the hub or received by other subscribers. However, it creates the risk that slow subscribers may not receive all messages published to the hub.

Finally, you can create a hub with the `unbounded` constructor.

```scala
def unbounded[A]: UIO[Hub[A]] =
  ???
```

An unbounded hub is never at capacity so publishing to an unbounded hub always immediately succeeds.

The advantage of an unbounded hub is that it combines the guarantees that all subscribers will receive all messages published to the hub and that a slow subscriber will not slow down the rate at which messages are published and received by other subscribers. However, it does this at the cost of potentially growing without bound if messages are published to the hub more quickly than they are taken by the slowest subscriber.

In general you should prefer bounded, dropping, or sliding hubs for this reason. However, unbounded hubs can be useful in certain situations where you do not know exactly how many values will be published to the hub but are confident that it will not exceed a reasonable size or want to handle that concern at a higher level of your application.

## Operators On Hubs

In addition to `publish` and `subscribe`, many of the same operators that are available on queues are available on hubs.

We can publish multiple values to the hub using the `publishAll` operator.

```scala
trait Hub[A] {
  def publishAll(as: Iterable[A]): UIO[Boolean]
}
```

We can check the capacity of the hub as well as the number of messages currently in the hub using the `size` and `capacity` operators.

```scala
trait Hub[A] {
  def capacity: Int
  def size: UIO[Int]
}
```

Note that `capacity` returns an `Int` because the capacity is set at hub creation and never changes. In contrast, `size` returns a `ZIO` effect that determines the current size of the hub since the number of messages in the hub can change over time.

We can also shut down the hub, check whether it has been shut down, or await its shut down. Shutting down a hub will shut down all the queues associated with subscriptions to the hub, properly propagating the shut down signal.

```scala
trait Hub[A] {
  def awaitShutdown: UIO[Unit]
  def isShutdown: UIO[Boolean]
  def shutdown: UIO[Unit]
}
```

As you can see, the operators on `Hub` are identical to the ones on `Queue` with the exception of `publish` and `subscribe` replacing `offer` and `take`. So if you know how to use a `Queue` you already know how to use a `Hub`.

In fact, a `Hub` can be viewed as a `Queue` that can only be written to.

```scala
trait Hub[A] {
  def toQueue[A]: Enqueue[A]
}
```

Here the `Enqueue` type represents a queue that can only be enqueued. Enqueing to the queue publishes a value to the hub, shutting down the queue shuts down the hub, and so on.

This can be extremely useful because it allows us to use a `Hub` anywhere we are currently using a `Queue` that we only write to.

For example, say we are using the `into` operator on `ZStream` to send all elements of a stream of financial transactions to a `Queue` for processing by a downstream consumer.

```scala
import zio.stream._

trait ZStream[-R, +E, +O] {
  def into[R1 <: R, E1 >: E](
    queue: ZEnqueue[R1, Nothing, Take[E1, O]]
  ): ZIO[R1, E1, Unit]
}
```

We would now like to have multiple downstream consumers process each of these transactions, for example to persist them and log them in addition to applying our business logic to them. With `Hub` this is easy because we can just use the `toQueue` operator to view any `Hub` as a `Queue` that can only be written to.


```scala
type Transaction = ???

val transactionStream: ZStream[Any, Nothing, Transaction] =
  ???

val hub: Hub[Transaction] =
  ???

transactionStream.into(hub.toQueue)
```

All of the elements from the transaction stream will now be published to the hub. We can now have multiple downstream consumers process elements from the financial transactions stream with the guarantee that all downstream consumers will see all transactions in the stream, changing the topology of our data flow from one-to-one to one-to-many with a single line change.

## Polymorphic Hubs

Like many of the other data structures in ZIO, a `Hub` is actually a type alias for a more polymorphic data structure called a `ZHub`.

```scala
trait ZHub[-RA, -RB, +EA, +EB, -A, B] {
  def publish(a: A): ZIO[RA, EA, Boolean]
  def subscribe: ZManaged[Any, Nothing, ZDequeue[RB, EB, B]]
}

type Hub[A] = ZHub[Any, Any, Nothing, Nothing, A, A]
```

A `ZHub` allows publishers to publish messages of type `A` to the hub and subscribers to subscribe to receive messages of type `B` from the hub. Publishing messages to the hub can require an environment of type `RA` and fail with an error of type `EA` and taking messages from the hub can require an environment of type `RB` and fail with an error of type `EB`.

Defining hubs polymorphically like this allows us to describe hubs that potentially transform their inputs or outputs in some way.

To create a polymorphic hub we begin with a normal hub as described above and then add logic to it for transforming its inputs or outputs.

We can transform the type of messages received from the hub using the `map` and `mapM` operators.

```scala
trait ZHub[-RA, -RB, +EA, +EB, -A, +B] {
  def map[C](f: B => C): ZHub[RA, RB, EA, EB, A, C]
  def mapM[RC <: RB, EC >: EB, C](f: B => ZIO[RC, EC, C]): ZHub[RA, RC, EA, EC, A, C]
}
```

The `map` operator allows us to transform the type of messages received from the hub with the specified function. Conceptually, every time a message is taken from the hub by a subscriber it will first be transformed with the function `f` before being received by the subscriber.

The `mapM` operator works the same way except it allows us to perform an effect each time a value is taken from the hub. We could use this for example to log each time a message is taken from the hub.


```scala
import zio.clock._

val hub: Hub[Int] = ???

val hubWithLogging: ZHub[Any, Clock with Console, Nothing, Nothing, Int, Int] =
  hub.mapM { n =>
    clock.currentDateTime.orDie.flatMap { currentDateTime =>
      console.putStrLn(s"Took message $n from the hub at $currentDateTime").orDie
    }.as(n)
  }
```

Note that the specified function in `map` or `mapM` will be applied each time a message is taken from the hub by a subscriber. Thus, if there are `n` subscribers to the hub the function will be evaluated `n` times for each message published to the hub.

This can be useful if we want to, for example, observe the different times that different subscribers are taking messages from the hub as in the example above. However, it is less efficient if we want to apply a transformation once for each value published to the hub.

For this we can use the `contramap` and `contramapM` operators defined on `ZHub`.

```scala
trait ZHub[-RA, -RB, +EA, +EB, -A, +B] {
  def contramap[C](
    f: C => A
  ): ZHub[RA, RB, EA, EB, C, B]
  def contramapM[RC <: RA, EC >: EA, C](
    f: C => ZIO[RC, EC, A]
  ): ZHub[RC, RB, EC, EB, C, B]
}
```

The `contramap` operator allows us to transform each value published to the hub by applying the specified function. Conceptually it returns a new hub where every time we publish a value we first transform it with the specified function before publishing it to the original hub.

The `contramapM` operator works the same way except it allows us to perform an effect each time a message is published to the hub.

Using these operators, we could describe a hub that validates its inputs, allowing publishers to publish raw data and subscribers to receive validated data while signaling to publishers when data they attempt to publish is not valid.


```scala
import zio.clock._

val hub: Hub[Int] = ???

val hubWithLogging: ZHub[Any, Any, String, Nothing, String, Int] =
  hub.contramapM { (s: String) =>
    ZIO.effect(s.toInt).orElseFail(s"$s is not a valid message")
  }
```

We can also transform inputs and outputs at the same time using the `dimap` or `dimapM` operators.

```scala
trait ZHub[-RA, -RB, +EA, +EB, -A, +B] {
  def dimap[C, D](
    f: C => A,
    g: B => D
  ): ZHub[RA, RB, EA, EB, C, D]
  def dimapM[RC <: RA, RD <: RB, EC >: EA, ED >: EB, C, D](
    f: C => ZIO[RC, EC, A],
    g: B => ZIO[RD, ED, D]
  ): ZHub[RC, RD, EC, ED, C, D]
}
```

These correspond to transforming the inputs and outputs of a hub at the same time using the specified functions. This is the same as transforming the outputs with `map` or `mapM` and the inputs with `contramap` or `contramapM`.

In addition to just transforming the inputs and outputs of a hub we can also filter the inputs or outputs of a hub.

```scala
trait ZHub[-RA, -RB, +EA, +EB, -A, +B] {
  def filterInput[A1 <: A](
    f: A1 => Boolean
  ): ZHub[RA, RB, EA, EB, A1, B]
  def filterInputM[RA1 <: RA, EA1 >: EA, A1 <: A](
    f: A1 => ZIO[RA1, EA1, Boolean]
  ): ZHub[RA1, RB, EA1, EB, A1, B]
  def filterOutput(
    f: B => Boolean
  ): ZHub[RA, RB, EA, EB, A, B]
  def filterOutputM[RB1 <: RB, EB1 >: EB](
    f: B => ZIO[RB1, EB1, Boolean]
  ): ZHub[RA, RB1, EA, EB1, A, B]
}
```

Filtering the inputs to a hub conceptually "throws away" messages that do not meet the filter predicate before they are published to the hub. The `publish` operator will return `false` to signal that such a message was not successfully published to the hub.

Similarly, filtering the outputs from a hub causes subscribers to ignore messages that do not meet the filter predicate, continuing to take messages from the hub until they find one that does meet the filter predicate.

We could, for example, create a hub that only handles tweets containing a particular term.

```scala
final case class Tweet(text: String)

val hub: Hub[Tweet] = ???

val zioHub: Hub[Tweet] =
  hub.filterInput(_.text.contains("zio"))
```

In most cases the hubs we work with in practice will be monomorphic hubs and we will use the hub purely to broadcast values, performing any necessary effects before publishing values to the hub or after taking values from the hub. But it is nice to know that we have this kind of power if we need it.

## Hubs And Streams

Hubs play extremely well with streams.

We can create a `ZStream` from a subscription to a hub using the `fromHub` operator.

```scala
import zio.stream._

object ZStream {
  def fromHub[R, E, O](hub: ZHub[Nothing, R, Any, E, Nothing, O]): ZStream[R, E, O] =
    ???
}
```

This will return a stream that subscribes to receive values from a hub and then emits every value published to the hub while the subscription is active. When the stream ends the subscriber will automatically be unsubscribed from the hub.

There is also a `fromHubManaged` operator that returns the stream in the context of a managed effect.

```scala
object ZStream {
  def fromHubManaged[R, E, O](
    hub: ZHub[Nothing, R, Any, E, Nothing, O]
  ): ZManaged[Any, Nothing, ZStream[R, E, O]] =
    ???
}
```

The managed effect here describes subscribing to receive messages from the hub while the stream describes taking messages from the hub. This can be useful when we need to ensure that a consumer has subscribed before a producer begins publishing values.

Here is an example of using it:


```scala
for {
  promise <- Promise.make[Nothing, Unit]
  hub     <- Hub.bounded[String](2)
  managed  = ZStream.fromHubManaged(hub).tapM(_ => promise.succeed(()))
  stream   = ZStream.unwrapManaged(managed)
  fiber   <- stream.take(2).runCollect.fork
  _       <- promise.await
  _       <- hub.publish("Hello")
  _       <- hub.publish("World")
  _       <- fiber.join
} yield ()
```

Notice that in this case we used a `Promise` to ensure that the subscription had completed before publishing to the hub. The `ZManaged` in the return type of `fromHubManaged` made it easy for us to signal when the subscription had occurred by using `tapM` and completing the `Promise`.

Of course in many real applications we don't need this kind of sequencing and just want to subscribe to receive new messages. In this case we can use the `fromHub` operator to return a `ZStream` that will automatically handle subscribing and unsubscribing for us.

There is also a `fromHubWithShutdown` variant that shuts down the hub itself when the stream ends. This is useful when the stream represents your main application logic and you want to shut down other subscriptions to the hub when the stream ends.

Each of these constructors also has `Chunk` variants, `fromChunkHub` and `fromChunkHubWithShutdown`, that allow you to preserve the chunked structure of data when working with hubs and streams.

In addition to being able to create streams from subscriptions to hubs, there are a variety of ways to send values emitted by streams to hubs to build more complex data flow graphs.

The simplest of these is the `toHub` operator, which constructs a new hub and publishes each element emitted by the stream to that hub.

```scala
trait ZStream[-R, +E, +O] {
  def toHub(
    capacity: Int
  ): ZManaged[R, Nothing, ZHub[Nothing, Any, Any, Nothing, Nothing, Take[E, O]]]
}
```

The hub will be constructed with the `bounded` constructor using the specified capacity.

If you want to send values emitted by a stream to an existing hub or a hub created using one of the other hub constructors you can use the `intoHub` operator.

```scala
trait ZStream[-R, +E, +O] {
  def intoHub[R1 <: R, E1 >: E](
    hub: ZHub[R1, Nothing, Nothing, Any, Take[E1, O], Any]
  ): ZIO[R1, E1, Unit]
}
```

There is an `intoHubManaged` variant of this if you want to send values to the hub in the context of a `ZManaged` instead of a `ZIO` effect.

You can also create a sink that sends values to a hub.

```scala
object ZSink {
  def fromHub[R, E, I](
    hub: ZHub[R, Nothing, E, Any, I, Any]
  ): ZSink[R, E, I, Nothing, Unit] =
    ???
}
```

The sink will publish each value sent to the sink to the specified hub. Again there is a `fromHubWithShutdown` variant that will shut down the hub when the stream ends.

Finally, `ZHub` is used internally to provide a highly efficient implementation of the `broadcast` family of operators, including `broadcast` and `broadcastDynamic`.

```scala
trait ZStream[-R, +E, +O] {
  def broadcast(
    n: Int,
    maximumLag: Int
  ): ZManaged[R, Nothing, List[ZStream[Any, E, O]]]
  def broadcastDynamic(
    maximumLag: Int
  ): ZManaged[R, Nothing, ZManaged[Any, Nothing, ZStream[Any, E, O]]]
}
```

The `broadcast` operator generates the specified number of new streams and broadcasts each value from the original stream to each of the new streams. The `broadcastDynamic` operator returns a new `ZManaged` value that you can use to dynamically subscribe and unsubscribe to receive values broadcast from the original stream.

You don't have to do anything with `ZHub` to take advantage of these operators other than enjoy their optimized implementation in terms of `ZHub`.

With `broadcast` and other `ZStream` operators that model distributing values to different streams and combining values from different streams it is straightforward to build complex data flow graphs, all while being as performant as possible.
