---
id: zstate
title: "ZState"
---

`ZState[S]` models a value of type `S` that can be read from and written to during the execution of an effect. This is a higher-level construct built on top of [`FiberRef`](fiberref.md) and the environment type to support using ZIO where we might have traditionally used state monad transformers.

Let's try a simple example of using `ZState`:

```scala mdoc:compile-only
import zio._

import java.io.IOException

object ZStateExample extends zio.ZIOAppDefault {
  val myApp: ZIO[ZState[Int], IOException, Unit] = for {
    s <- ZIO.service[ZState[Int]]
    _ <- s.update(_ + 1)
    _ <- s.update(_ + 2)
    state <- s.get
    _ <- Console.printLine(s"current state: $state")
  } yield ()

  def run = ZIO.stateful(0)(myApp)
}
```

The idiomatic way to work with `ZState` is as part of the environment using operators defined on `ZIO` to access the `ZState` from the environment, and finally, allocate the initial state using the `ZIO.stateful` operator.

Because we typically use `ZState` as part of the environment, it is recommended to define our own state type `S` such as `MyState` rather than using a type such as `Int` to avoid the risk of ambiguity:

```scala mdoc:compile-only
import zio._

import java.io.IOException

final case class MyState(counter: Int)

object ZStateExample extends zio.ZIOAppDefault {

  val myApp: ZIO[ZState[MyState], IOException, Unit] =
    for {
      counter <- ZIO.service[ZState[MyState]]
      _ <- counter.update(state => state.copy(counter = state.counter + 1))
      _ <- counter.update(state => state.copy(counter = state.counter + 2))
      state <- counter.get
      _ <- Console.printLine(s"Current state: $state")
    } yield ()

  def run = ZIO.stateful(MyState(0))(myApp)
}
```

The `ZIO` data type also has some helper methods to work with `ZState` as the environment of `ZIO` effect such as `ZIO.updateState`, `ZIO.getState`, and `ZIO.getStateWith`:

```scala mdoc:compile-only
import zio._

import java.io.IOException

final case class MyState(counter: Int)

val myApp: ZIO[ZState[MyState], IOException, Int] =
  for {
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 2))
    state <- ZIO.getStateWith[MyState](_.counter)
    _ <- Console.printLine(s"Current state: $state")
  } yield state
```

An important note about `ZState` is that it is on top of the `FiberRef` data type. So it will inherit its behavior from the `FiberRef`.

For example, when a fiber is going to join to its parent fiber, its state will be merged with its parent state:

```scala mdoc:compile-only
import zio._

case class MyState(counter: Int)

object ZStateExample extends ZIOAppDefault {
  val myApp = for {
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
    fiber <-
      (for {
        _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
        state <- ZIO.getState[MyState]
        _ <- Console.printLine(s"Current state inside the forked fiber: $state")
      } yield ()).fork
    _ <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 5))
    state1 <- ZIO.getState[MyState]
    _ <- Console.printLine(s"Current state before merging the fiber: $state1")
    _ <- fiber.join
    state2 <- ZIO.getState[MyState]
    _ <- Console.printLine(s"The final state: $state2")
  } yield ()

  def run =
    ZIO.stateful(MyState(0))(myApp)
}
```

The output of running this snippet code would be as below:

```
Current state before merging the fiber: MyState(6)
Current state inside the forked fiber: MyState(2)
The final state: MyState(2)
```
