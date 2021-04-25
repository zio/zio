---
id: index
title: "Introduction"
---

## ZIO Environment
The `ZIO[-R, +E, +A]` data type describes an effect that requires an input type of `R`, as an environment, may fail with an error of type `E` or succeed and produces a value of type `A`.

The input type is also known as _environment type_. This type-parameter indicates that to run an effect we need one or some services as an environment of that effect.

For example, when we have `ZIO[Console, Nothing, Unit]`, this shows that to run this effect we need to provide an implementation of the `Console` service:

```scala mdoc:silent
import zio.ZIO
import zio.console._
val effect: ZIO[Console, Nothing, Unit] = putStrLn("Hello, World!")
```

So finally when we provide a live version of `Console` service to our `effect`, it will be converted to an effect that doesn't require any environmental service:

```scala mdoc:silent
val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live)
```

Finally, to run our application we can put our `mainApp` inside the `run` method:

```scala mdoc:silent:nest
import zio.{ExitCode, ZEnv, ZIO}
import zio.console._

object MainApp extends zio.App {
  val effect: ZIO[Console, Nothing, Unit] = putStrLn("Hello, World!")
  val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live)

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = 
    mainApp.exitCode
}
```

Sometimes an effect needs more than one environmental service, it doesn't matter, in these cases, we compose all dependencies by `++` operator:

```scala mdoc:silent:nest
import zio.console._
import zio.random._

val effect: ZIO[Console with Random, Nothing, Unit] = for {
  r <- nextInt
  _ <- putStrLn(s"random number: $r")
} yield ()

val mainApp: ZIO[Any, Nothing, Unit] = effect.provideLayer(Console.live ++ Random.live)
```

We don't need to provide live layers for built-in services. ZIO has a `ZEnv` type alias for the composition of all ZIO built-in services (Clock, Console, System, Random, and Blocking). So we can run the above `effect` as follows:

```scala mdoc:silent:nest
import zio.console._
import zio.random._
import zio.{ExitCode, ZEnv, ZIO}

object MainApp extends zio.App {
  val effect: ZIO[Console with Random, Nothing, Unit] = for {
    r <- nextInt
    _ <- putStrLn(s"random number: $r")
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    effect.exitCode
}
```

ZIO environment facility enables us to:

1. **Code to Interface** — like object-oriented paradigm, in ZIO we encouraged to code to interfaces and defer the implementation. It is the best practice, but ZIO does not enforce us to do that.

2. **Write a Testable Code** — By coding to an interface, whenever we want to test our effects, we can easily mock any external services, by providing a _test_ version of those instead of the `live` version.
