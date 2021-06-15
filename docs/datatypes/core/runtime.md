---
id: runtime
title: "Runtime"
---

A `Runtime[R]` is capable of executing tasks within an environment `R`.

To run an effect, we need a `Runtime`, which is capable of executing effects. Runtimes bundle a thread pool together with the environment that effects need.

## What is a Runtime System?

Whenever we write a ZIO program, we create a ZIO effect from ZIO constructors plus using its combinators. We are building a blueprint. ZIO effect is just a data structure that describes the execution of a concurrent program. So we end up with a tree data structure that contains lots of different data structures combined together to describe what the ZIO effect should do. This data structure doesn't do anything, it is just a description of a concurrent program.

So the most thing we should keep in mind when we are working with a functional effect system like ZIO is that when we are writing code, printing a string onto the console, reading a file, querying a database, and so forth; We are just writing a workflow or blueprint of an application. We are just building a data structure.

So how ZIO run these workflows? This is where ZIO Runtime System comes into play. Whenever we run an `unsaferun` function, the Runtime System is responsible to step through all the instructions described by the ZIO effect and execute them.

To simplify everything, we can think of a Runtime System like a black box that takes both the ZIO effect (`ZIO[R, E, A]`) and its environment (`R`), it will run this effect and then will return its result as an `Either[E, A]` value.


![ZIO Runtime System](assets/zio-runtime-system.svg)

## Responsibilities of the Runtime System

Runtime Systems have a lot of responsibilities:

1. **Execute every step of the blueprint** — They have to execute every step of the blueprint in a while loop until it's done.

2. **Handle unexpected errors** — They have to handle unexpected errors, not just the expected ones but also the unexpected ones. 

3. **Spawn concurrent fiber** — They are actually responsible for the concurrency that effect systems have. They have to spawn a fiber every time we call `fork` on an effect to spawn off a new fiber.

4. **Cooperatively yield to other fibers** — They have to cooperatively yield to other fibers so that fibers that are sort of hogging the spotlight, don't get to monopolize all the CPU resources. They have to make sure that the fibers split the CPU cores among all the fibers that are working.

5. **Capture execution and stack traces** — They have to keep track of where we are in the progress of our own user-land code so the nice detailed execution traces can be captured. 

6. **Ensure finalizers are run appropriately** — They have to ensure finalizers are run appropriately at the right point in all circumstances to make sure that resources are closed that clean-up logic is executed. This is the feature that powers ZManaged and all the other resource-safe constructs in ZIO.

7. **Handle asynchronous callback** — They have to handle this messy job of dealing with asynchronous callbacks. So we don't have to deal with async code. When we are doing ZIO, everything is just async out of the box. 

## Running a ZIO Effect

There are two ways to run ZIO effect:
1. **Using `zio.App` entry point**
2. **Using `unsafeRun` method directly**

### Using zio.App

In most cases we use this method to run our ZIO effect. `zio.App` has a `run` function which is the main entry point for running a ZIO application on the JVM:

```scala
package zio
trait App {
  def run(args: List[String]): URIO[ZEnv, ExitCode]
}
```

Assume we have written an effect using ZIO:

```scala mdoc:silent
import zio.console._

def myAppLogic =
  for {
    _ <- putStrLn("Hello! What is your name?")
    n <- getStrLn
    _ <- putStrLn("Hello, " + n + ", good to meet you!")
  } yield ()
```

Now we can run that effect using `run` entry point:

```scala mdoc:silent
object MyApp extends zio.App {
  final def run(args: List[String]) =
    myAppLogic.exitCode
}
```

### Using unsafeRun

Another way to execute ZIO effect is to feed the ZIO effect to the `unsafeRun` method of Runtime system:

```scala mdoc:silent:nest
object RunZIOEffectUsingUnsafeRun extends scala.App {
  zio.Runtime.default.unsafeRun(
    myAppLogic
  )
}
```

We don't usually use this method to run our effects. One of the use cases of this method is when we are integrating the legacy (non-effectful code) with the ZIO effect. It also helps us to refactor a large legacy code base into a ZIO effect gradually; Assume we have decided to refactor a component in the middle of a legacy code and rewrite that with ZIO. We can start rewriting that component with the ZIO effect and then integrate that component with the existing code base, using the `unsafeRun` function.
