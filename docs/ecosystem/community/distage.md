---
id: distage
title: "Distage"
---

[Distage](https://izumi.7mind.io/distage/) is a compile-time safe, transparent, and debuggable Dependency Injection framework for pure FP Scala.

## Introduction

By using _Distage_ we can auto-wire all components of our application.
- We don't need to manually link components together
- We don't need to manually specify the order of allocation and allocation of dependencies. This will be derived automatically from the dependency order.
- We can override any component within the dependency graph.
- It helps us to create different configurations of our components for different use cases.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-core" % "1.0.8"
```

## Example

In this example we create a `RandomApp` comprising two `Random` and `Logger` services. By using `ModuleDef` we _bind_ services to their implementations:

```scala
import distage.{Activation, Injector, ModuleDef, Roots}
import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import zio.{ExitCode, Task, UIO, URIO, ZIO}

import java.time.LocalDateTime

trait Random {
  def nextInteger: UIO[Int]
}

final class ScalaRandom extends Random {
  override def nextInteger: UIO[Int] =
    ZIO.effectTotal(scala.util.Random.nextInt())
}

trait Logger {
  def log(name: String): Task[Unit]
}

final class ConsoleLogger extends Logger {
  override def log(line: String): Task[Unit] = {
    val timeStamp = LocalDateTime.now()
    ZIO.effect(println(s"$timeStamp: $line"))
  }
}

final class RandomApp(random: Random, logger: Logger) {
  def run: Task[Unit] = for {
    random <- random.nextInteger
    _ <- logger.log(s"random number generated: $random")
  } yield ()
}

object DistageExample extends zio.App {
  def RandomAppModule: ModuleDef = new ModuleDef {
    make[Random].from[ScalaRandom]
    make[Logger].from[ConsoleLogger]
    make[RandomApp] // `.from` is not required for concrete classes
  }
  
  val resource: Lifecycle[Task, Locator] = Injector[Task]().produce(
    plan = Injector[Task]().plan(
      bindings = RandomAppModule,
      activation = Activation.empty,
      roots = Roots.target[RandomApp]
    )
  )

  val myApp: Task[Unit] = resource.use(locator => locator.get[RandomApp].run)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
