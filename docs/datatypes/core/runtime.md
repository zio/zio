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

## Running a ZIO Effect

There are two ways to run ZIO effect:
1. **Using `zio.App` Entry Point**
2. **Using `unsafeRun` Method Directly**

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
