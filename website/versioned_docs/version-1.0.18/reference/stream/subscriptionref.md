---
id: subscription-ref
title: "SubscriptionRef"
---

A `SubscriptionRef[A]` contains a current value of type `A` and a stream that can be consumed to observe all changes to that value.

```scala
import zio._
import zio.stream._

trait SubscriptionRef[A] {
  def changes: ZStream[Any, Nothing, A]
  def ref: RefM[A]
}
```

The `ref` allows us to access a `RefM` containing the current value. We can use all the normal methods on `RefM` to `get`, `set`, or `modify` the current value.

The `changes` stream can be consumed to observe the current value as well as all changes to that value. Since `changes` is just a description of a stream, each time we run the stream we will observe the current value as of that point in time as well as all changes after that.

To create a `SubscriptionRef` you can use the `make` constructor, which makes a new `SubscriptionRef` with the specified initial value.

```scala
object SubscriptionRef {
  def make[A](a: A): UIO[SubscriptionRef[A]] =
    ???
}
```

A `SubscriptionRef` can be extremely useful to model some shared state where one or more observers must perform some action for all changes in that shared state. For example, in a functional reactive programming context the value of the `SubscriptionRef` might represent one part of the application state and each observer would need to update various user interface elements based on changes in that state.

To see how this works, let's create a simple example where a "server" repeatedly updates a value that is observed by multiple "clients".


```scala
def server(ref: RefM[Long]): UIO[Nothing] =
  ref.update(n => ZIO.succeed(n + 1)).forever
```

Notice that `server` just takes a `RefM` and does not need to know anything about `SubscriptionRef`. From its perspective it is just updating a value.

```scala
import zio.random._

def client(changes: ZStream[Any, Nothing, Long]): URIO[Random, Chunk[Long]] =
  for {
    n     <- random.nextLongBetween(1, 200)
    chunk <- changes.take(n).runCollect
  } yield chunk
```

Similarly `client` just takes a `ZStream` of values and does not have to know anything about the source of these values. In this case we will simply observe a fixed number of values.

To wire everything together, we start the server, then start multiple instances of the client in parallel, and finally shut down the server when we are done. We also actually create the `SubscriptionRef` here.

```scala
for {
  subscriptionRef <- SubscriptionRef.make(0L)
  server          <- server(subscriptionRef.ref).fork
  chunks          <- ZIO.collectAllPar(List.fill(100)(client(subscriptionRef.changes)))
  _               <- server.interrupt
  _               <- ZIO.foreach(chunks)(chunk => console.putStrLn(chunk.toString))
} yield ()
```

This will ensure that each client observes the current value when it starts and all changes to the value after that.

Since the changes are just streams it is also easy to build much more complex programs using all the stream operators we are accustomed to. For example, we can transform these streams, filter them, or merge them with other streams.
