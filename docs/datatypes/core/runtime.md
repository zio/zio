---
id: runtime
title: "Runtime"
---
```scala mdoc:invisible
import zio.{FiberRefs, Runtime, RuntimeFlags, Task, UIO, Unsafe, URIO, ZEnvironment, ZIO}
```

A `Runtime[R]` is capable of executing tasks within an environment `R`.

To run an effect, we need a `Runtime`, which is capable of executing effects. Runtimes bundle a thread pool together with the environment that effects need.

## What is a Runtime System?

Whenever we write a ZIO program, we create a ZIO effect from ZIO constructors plus using its combinators. We are building a blueprint. ZIO effect is just a data structure that describes the execution of a concurrent program. So we end up with a tree data structure that contains lots of different data structures combined together to describe what the ZIO effect should do. This data structure doesn't do anything, it is just a description of a concurrent program.

So the most important thing we should keep in mind when we are working with a functional effect system like ZIO is that when we are writing code, printing a string onto the console, reading a file, querying a database, and so forth; We are just writing a workflow or blueprint of an application. We are just building a data structure.

So how can ZIO run these workflows? This is where ZIO Runtime System comes into play. Whenever we run an `unsaferun` function, the Runtime System is responsible to step through all the instructions described by the ZIO effect and execute them.

To simplify everything, we can think of a Runtime System like a black box that takes both the ZIO effect (`ZIO[R, E, A]`) and its environment (`R`), it will run this effect and then will return its result as an `Either[E, A]` value.


![ZIO Runtime System](/img/zio-runtime-system.svg)

## Responsibilities of the Runtime System

Runtime Systems have a lot of responsibilities:

1. **Execute every step of the blueprint** — They have to execute every step of the blueprint in a while loop until it's done.

2. **Handle unexpected errors** — They have to handle unexpected errors, not just the expected ones but also the unexpected ones. 

3. **Spawn concurrent fiber** — They are actually responsible for the concurrency that effect systems have. They have to spawn a fiber every time we call `fork` on an effect to spawn off a new fiber.

4. **Cooperatively yield to other fibers** — They have to cooperatively yield to other fibers so that fibers that are sort of hogging the spotlight, don't get to monopolize all the CPU resources. They have to make sure that the fibers split the CPU cores among all the fibers that are working.

5. **Capture execution and stack traces** — They have to keep track of where we are in the progress of our own user-land code so the nice detailed execution traces can be captured. 

6. **Ensure finalizers are run appropriately** — They have to ensure finalizers are run appropriately at the right point in all circumstances to make sure that resources are closed that clean-up logic is executed. This is the feature that powers Scope and all the other resource-safe constructs in ZIO.

7. **Handle asynchronous callback** — They have to handle this messy job of dealing with asynchronous callbacks. So we don't have to deal with async code. When we are doing ZIO, everything is just async out of the box. 

## Running a ZIO Effect

There are two common ways to run a ZIO effect. Most of the time, we use the [`ZIOAppDefault`](zioapp.md) trait. There are, however, some advanced use cases for which we need to directly feed a ZIO effect into the runtime system's `unsafeRun` method:

```scala mdoc:compile-only
import zio._

object RunZIOEffectUsingUnsafeRun extends scala.App {
  val myAppLogic = for {
    _ <- Console.printLine("Hello! What is your name?")
    n <- Console.readLine
    _ <- Console.printLine("Hello, " + n + ", good to meet you!")
  } yield ()

  Unsafe.unsafe { implicit u =>
      zio.Runtime.default.unsafe.run(
        myAppLogic
      ).getOrThrowFiberFailure()
  }
}
```

We don't usually use this method to run our effects. One of the use cases of this method is when we are integrating the legacy (non-effectful code) with the ZIO effect. It also helps us to refactor a large legacy code base into a ZIO effect gradually; Assume we have decided to refactor a component in the middle of a legacy code and rewrite that with ZIO. We can start rewriting that component with the ZIO effect and then integrate that component with the existing code base, using the `unsafeRun` function.

## Default Runtime

ZIO contains a default runtime called `Runtime.default` designed to work well for mainstream usage. It is already implemented as below:

```scala
object Runtime {
  lazy val default: Runtime[Any] = Runtime(ZEnvironment.empty)
}
```

The default runtime contains minimum capabilities to bootstrap execution of ZIO tasks.

We can easily access the default `Runtime` to run an effect:

```scala mdoc:compile-only
object MainApp extends scala.App {
  val myAppLogic = ZIO.succeed(???)
  val runtime = Runtime.default
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(myAppLogic).getOrThrowFiberFailure()
  }
}
```

## Custom Runtime

Sometimes we need to create a custom `Runtime` with a user-defined environment.

Some use-cases of custom Runtimes:

### Providing Environment to Runtime System

The custom runtime can be used to run many different effects that all require the same environment, so we don't have to call `ZIO#provide` on all of them before we run them.

For example, assume we want to create a `Runtime` for services that are for testing purposes, and they don't interact with real external APIs. So we can create a runtime, especially for testing.

Let's say we have defined two `Logging` and `Email` services:

```scala mdoc:silent:nest
trait Logging {
  def log(line: String): UIO[Unit]
}

object Logging {
  def log(line: String): URIO[Logging, Unit] =
    ZIO.serviceWith[Logging](_.log(line))
}

trait Email {
  def send(user: String, content: String): Task[Unit]
}

object Email {
  def send(user: String, content: String): ZIO[Email, Throwable, Unit] =
    ZIO.serviceWith[Email](_.send(user, content))
}
```

We are going to implement a live version of `Logging` service and also a mock version of `Email` service for testing:

```scala mdoc:silent:nest
case class LoggingLive() extends Logging {
  override def log(line: String): UIO[Unit] =
    ZIO.succeed(print(line))
}

case class EmailMock() extends Email {
  override def send(user: String, content: String): Task[Unit] =
    ZIO.attempt(println(s"sending email to $user"))
}
```

Let's create a custom runtime that contains these two service implementations in its environment:

```scala mdoc:silent:nest
val testableRuntime = Runtime(
  ZEnvironment[Logging, Email](LoggingLive(), EmailMock()),
  FiberRefs.empty,
  RuntimeFlags.default
)
```

Also, we can map the default runtime to the new runtime, so we can append new services to the ZIO environment:

```scala mdoc:silent:nest
val testableRuntime: Runtime[Logging with Email] =
  Runtime.default.map { _ =>
    ZEnvironment[Logging, Email](LoggingLive(), EmailMock())
  }
```

Now we can run our effects using this custom `Runtime`:

```scala mdoc:silent:nest
Unsafe.unsafe { implicit u =>
    testableRuntime.unsafe.run(
      for {
        _ <- Logging.log("sending newsletter")
        _ <- Email.send("David", "Hi! Here is today's newsletter.")
      } yield ()
    ).getOrThrowFiberFailure()
}
```
