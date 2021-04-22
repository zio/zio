---
id: zlayer
title: "ZLayer"
---

A `ZLayer[A, E, B]` describes a layer of an application: every layer in an application requires some services (the input) and produces some services (the output). 

Layers can be thought of as recipes for producing bundles of services, given their dependencies (other services).

Construction of layers can be effectful and utilize resources that must be acquired and safely released when the services are done being utilized.

Layers are shared by default, meaning that if the same layer is used twice, the layer will only be allocated a single time. 

Because of their excellent composition properties, layers are the idiomatic way in ZIO to create services that depend on other services.

For example, the `ZLayer[Blocking with Logging with Database, Throwable, UserRepoService]` is a recipe for building a service that requires `Blocking`, `Logging` and `Database` service, and it produces a `UserRepoService` service.

## Creation

### succeed

With `ZLayer.succeed` we can construct a `ZLayer` from a value. It returns a `ULayer[Has[A]]` value, which represents a layer of application that _has_ a service of type `A`:

```scala
def succeed[A: Tag](a: A): ULayer[Has[A]]
```

In the following example, we are going to create a `nameLayer` that provides us the name of `Adam`.

```scala mdoc:invisible
import zio._
```

```scala mdoc:silent
val nameLayer: ULayer[Has[String]] = ZLayer.succeed("Adam")
```

In most cases, we use `ZLayer.succeed` to provide a layer of service of type `A`.

For example, assume we have written the following service:

```scala mdoc:silent
object terminal {
  type Terminal = Has[Terminal.Service]

  object Terminal {
    trait Service {
      def putStrLn(line: String): UIO[Unit]
    }

    object Service {
      val live: Service = new Service {
        override def putStrLn(line: String): UIO[Unit] =
          ZIO.effectTotal(println(line))
      }
    }
  }
}
```

Now we can create a `ZLayer` from the `live` version of this service:

```scala mdoc:silent
import terminal._
val live: ZLayer[Any, Nothing, Terminal] = ZLayer.succeed(Terminal.Service.live)
```

## Examples

### The simplest ZLayer application

This application demonstrates a ZIO program with a single dependency on a simple string value:

```scala mdoc:silent
import zio._

object Example extends zio.App {

  // Define our simple ZIO program
  val zio: ZIO[Has[String], Nothing, Unit] = for {
    name <- ZIO.access[Has[String]](_.get)
    _    <- UIO(println(s"Hello, $name!"))
  } yield ()

  // Create a ZLayer that produces a string and can be used to satisfy a string
  // dependency that the program has
  val nameLayer: ULayer[Has[String]] = ZLayer.succeed("Adam")

  // Run the program, providing the `nameLayer`
  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    zio.provideLayer(nameLayer).as(ExitCode.success)
}

```

### ZLayer application with dependencies 

In the following example, our ZIO application has several dependencies:
 - `zio.clock.Clock`
 - `zio.console.Console`
 - `ModuleB`

`ModuleB` in turn depends upon `ModuleA`:

```scala mdoc:silent
import zio._
import zio.clock._
import zio.console._
import zio.duration.Duration._
import java.io.IOException

object moduleA {
  type ModuleA = Has[ModuleA.Service]

  object ModuleA {
    trait Service {
      def letsGoA(v: Int): UIO[String]
    }

    val any: ZLayer[ModuleA, Nothing, ModuleA] =
      ZLayer.requires[ModuleA]

    val live: Layer[Nothing, Has[Service]] = ZLayer.succeed {
      new Service {
        def letsGoA(v: Int): UIO[String] = UIO(s"done: v = $v ")
      }
    }
  }

  def letsGoA(v: Int): URIO[ModuleA, String] =
    ZIO.accessM(_.get.letsGoA(v))
}

import moduleA._

object moduleB {
  type ModuleB = Has[ModuleB.Service]

  object ModuleB {
    trait Service {
      def letsGoB(v: Int): UIO[String]
    }

    val any: ZLayer[ModuleB, Nothing, ModuleB] =
      ZLayer.requires[ModuleB]

    val live: ZLayer[ModuleA, Nothing, ModuleB] = ZLayer.fromService { (moduleA: ModuleA.Service) =>
      new Service {
        def letsGoB(v: Int): UIO[String] =
          moduleA.letsGoA(v)
      }
    }
  }

  def letsGoB(v: Int): URIO[ModuleB, String] =
    ZIO.accessM(_.get.letsGoB(v))
}

object ZLayerApp0 extends zio.App {

  import moduleB._

  val env = Console.live ++ Clock.live ++ (ModuleA.live >>> ModuleB.live)
  val program: ZIO[Console with Clock with moduleB.ModuleB, IOException, Unit] =
    for {
      _ <- putStrLn(s"Welcome to ZIO!")
      _ <- sleep(Finite(1000))
      r <- letsGoB(10)
      _ <- putStrLn(r)
    } yield ()

  def run(args: List[String]) =
    program.provideLayer(env).exitCode

}

// output: 
// [info] running ZLayersApp 
// Welcome to ZIO!
// done: v = 10 
```

### ZLayer example with complex dependencies

In this example, we can see that `ModuleC` depends upon `ModuleA`, `ModuleB`, and `Clock`. The layer provided to the runnable application shows how dependency layers can be combined using `++` into a single combined layer. The combined layer will then be able to produce both of the outputs of the original layers as a single layer:

```scala mdoc:silent
import zio._
import zio.clock._

object ZLayerApp1 extends scala.App {
  val rt = Runtime.default

  type ModuleA = Has[ModuleA.Service]

  object ModuleA {

    trait Service {}

    val any: ZLayer[ModuleA, Nothing, ModuleA] =
      ZLayer.requires[ModuleA]

    val live: ZLayer[Any, Nothing, ModuleA] =
      ZLayer.succeed(new Service {})
  }

  type ModuleB = Has[ModuleB.Service]

  object ModuleB {

    trait Service {}

    val any: ZLayer[ModuleB, Nothing, ModuleB] =
      ZLayer.requires[ModuleB]

    val live: ZLayer[Any, Nothing, ModuleB] =
      ZLayer.succeed(new Service {})
  }

  type ModuleC = Has[ModuleC.Service]

  object ModuleC {

    trait Service {
      def foo: UIO[Int]
    }

    val any: ZLayer[ModuleC, Nothing, ModuleC] =
      ZLayer.requires[ModuleC]

    val live: ZLayer[ModuleA with ModuleB with Clock, Nothing, ModuleC] =
      ZLayer.succeed {
        new Service {
          val foo: UIO[Int] = UIO.succeed(42)
        }
      }

    val foo: URIO[ModuleC, Int] =
      ZIO.accessM(_.get.foo)
  }

  val env = (ModuleA.live ++ ModuleB.live ++ ZLayer.identity[Clock]) >>> ModuleC.live

  val res = ModuleC.foo.provideCustomLayer(env)

  val out = rt.unsafeRun(res)
  println(out)
  // 42
}
```
