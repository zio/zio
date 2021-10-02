---
id: zstate
title: "ZState"
---

`ZState[S]` models a value of type `S` that can be read from and written to during the execution of an effect. This is a higher-level construct built on top of [`FiberRef`](../fiber/fiberref.md) and the environment type to support using ZIO where we might have traditionally used state monad transformers.

Let's try a simple example of using `ZState`:

```scala mdoc:silent:nest
import java.io.IOException
import zio._

val myApp: ZIO[Has[Console], IOException, Unit] =
  for {
    counter <- ZState.make(0)
    _ <- counter.update(_ + 1)
    _ <- counter.update(_ + 2)
    state <- counter.get
    _ <- Console.printLine(s"current state: $state")
  } yield ()
```

The idiomatic way to work with `ZState` is as part of the environment using operators defined on `ZIO`. So instead of creating `ZState` directly using `ZState.make` constructor, we can access the `ZState` from the environment, and finally, provide proper layer using `ZState.makeLayer` constructor:

```scala mdoc:compile-only
import zio._

import java.io.IOException

object ZStateExample extends zio.ZIOAppDefault {
  val myApp: ZIO[Has[Console] with Has[ZState[Int]], IOException, Unit] = for {
    s <- ZIO.service[ZState[Int]]
    _ <- s.update(_ + 1)
    _ <- s.update(_ + 2)
    state <- s.get
    _ <- Console.printLine(s"current state: $state")
  } yield ()

  def run = myApp.injectCustom(ZState.makeLayer(0))
}
```

Because we typically use `ZState` as part of the environment, it is recommended to define our own state type `S` such as `MyState` rather than using a type such as `Int` to avoid the risk of ambiguity:

```scala mdoc:compile-only
import zio._
import java.io.IOException

final case class MyState(counter: Int)

object ZStateExample extends zio.ZIOAppDefault {

  val myApp: ZIO[Has[Console] with Has[ZState[MyState]], IOException, Unit] =
    for {
      counter <- ZIO.service[ZState[MyState]]
      _ <- counter.update(state => state.copy(counter = state.counter + 1))
      _ <- counter.update(state => state.copy(counter = state.counter + 2))
      state <- counter.get
      _ <- Console.printLine(s"Current state: $state")
    } yield ()

  def run = myApp.injectCustom(ZState.makeLayer(MyState(0)))
}
```

The `ZIO` data type also has some helper methods to work with `ZState` as the environment of `ZIO` effect such as `ZIO.updateState`, `ZIO.getState`, and `ZIO.getStateWith`:

```scala mdoc:compile-only
final case class MyState(counter: Int)

val myApp: ZIO[Has[Console] with Has[ZState[MyState]], IOException, Int] =
  for {
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 2))
    state <- ZIO.getStateWith[MyState](_.counter)
    _ <- Console.printLine(s"Current state: $state")
  } yield state
```
