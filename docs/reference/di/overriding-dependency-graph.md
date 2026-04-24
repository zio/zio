---
id: overriding-dependency-graph
title: "Overriding Dependency Graph"
---

We can create a ZIO application by providing a local or a global environment, or a combination:

## Global Environment

It is usual when writing ZIO applications to provide layers at the end of the world. Then we provide layers to the whole ZIO application all at once. This pattern uses a single global environment for all ZIO applications:

```scala mdoc:invisible
import zio._

trait ServiceA
trait ServiceB
trait ServiceC
trait ServiceD
val a = ZLayer.succeed[ServiceA](new ServiceA{})
val b = ZLayer.succeed[ServiceB](new ServiceB{})
val c = ZLayer.succeed[ServiceC](new ServiceC{})
val d = ZLayer.succeed[ServiceD](new ServiceD{})
```

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[ServiceA & ServiceB & ServiceC & ServiceD, Throwable, Unit] = ???
    
  def run = myApp.provide(a, b, c, d)
}
```

```scala mdoc:invisible:reset
```

## Local Environment

Occasionally, we may need to provide different environments for different parts of our application, or it may be necessary to provide a single global environment for the entire application except for some inner layers.

Providing a layer locally is analogous to overriding a method in an object-oriented paradigm. So we can think of that as overriding the global environment:

```scala mdoc:invisible
import zio._

trait A
trait B
trait C

val globalA = ZLayer.succeed[A](new A {})
val globalB = ZLayer.succeed[B](new B {})
val globalC = ZLayer.succeed[C](new C {})
val localC  = ZLayer.succeed[C](new C {})
```

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def myApp: ZIO[A & B & C, Throwable, Unit] = {
    def innerApp1: ZIO[A & B & C, Throwable, Unit] = ???
    def innerApp2: ZIO[A & C,     Throwable, Unit] = ???

    innerApp1.provideSomeLayer[A & B](localC) *> innerApp2
  }

  def run = myApp.provide(globalA, globalB, globalC)
}
```

```scala mdoc:invisible:reset

```

ZIO Test's [Live service](../test/services/live.md) uses this pattern to provide real environment to a single part of an effect.
