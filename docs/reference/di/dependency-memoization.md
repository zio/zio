---
id: dependency-memoization
title: "Dependency Memoization"
---

Layer memoization allows a layer to be created once and used multiple times in the dependency graph. So if we use the same layer twice, e.g. `(a >>> b) ++ (a >>> c)`, then the `a` layer will be allocated only once.

## Layers are Memoized by Default When Providing Globally

One important feature of a ZIO application is that layers are shared by default, meaning that if the same layer is used twice, and if we provide the layer [globally](overriding-dependency-graph.md#global-environment) the layer will only be allocated a single time. For every layer in our dependency graph, there is only one instance of it that is shared between all the layers that depend on it.

For example, assume we have the three `A`, `B`, and `C` services. The implementation of both `B` and `C` are dependent on the `A` service:

```scala mdoc:silent
import zio._

trait A
trait B
trait C

case class BLive(a: A) extends B
case class CLive(a: A) extends C

val a: ZLayer[Any, Nothing, A] =
  ZLayer(ZIO.succeed(new A {}).debug("initialized"))

val b: ZLayer[A, Nothing, B] =
  ZLayer {
    for {
      a <- ZIO.service[A]
    } yield BLive(a)
  }

val c: ZLayer[A, Nothing, C] =
  ZLayer {
    for {
      a <- ZIO.service[A]
    } yield CLive(a)
  }
```

Although both `b` and `c` layers require the `a` layer, the `a` layer is instantiated only once. It is shared with both `b` and `c`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[B & C, Nothing, Unit] =
    for {
      _ <- ZIO.service[B]
      _ <- ZIO.service[C]
    } yield ()
    
  // alternative: myApp.provideLayer((a >>> b) ++ (a >>> c))
  def run = myApp.provide(a, b, c) 
}
// Output:
// initialized: MainApp3$$anon$32@62c8b8d3
```

## Acquiring a Fresh Version

If we don't want to share a module, we should create a fresh, non-shared version of it through `ZLayer#fresh`.

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[B & C, Nothing, Unit] =
    for {
      _ <- ZIO.service[B]
      _ <- ZIO.service[C]
    } yield ()

  def run = myApp.provideLayer((a.fresh >>> b) ++ (a.fresh >>> c))
}
// Output:
// initialized: MainApp$$anon$22@7eb282da
// initialized: MainApp$$anon$22@6397a26a
```

## Layers Are Not Memoized When Providing Locally

If we don't provide a layer globally but instead provide them [locally](overriding-dependency-graph.md#local-environment), that layer doesn't support memoization by default.

In the following example, we provided the `A` layer two times locally and the ZIO doesn't memoize the construction of the `A` layer. So, it will be initialized two times:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[Any, Nothing, Unit] =
    for {
      _ <- ZIO.service[A].provide(a) // providing locally
      _ <- ZIO.service[A].provide(a) // providing locally
    } yield ()

  def run = myApp
}
// The output:
// initialized: MainApp$$anon$1@cd60bde
// initialized: MainApp$$anon$1@a984546
```

## Manual Memoization

We can memoize the `A` layer manually using the `ZLayer#memoize` operator. It will return a scoped effect that, if evaluated, will return the lazily computed result of this layer:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[Any, Nothing, Unit] =
    ZIO.scoped {
      a.memoize.flatMap { aLayer =>
        for {
          _ <- ZIO.service[A].provide(aLayer)
          _ <- ZIO.service[A].provide(aLayer)
        } yield ()
      }
    }
    
  def run = myApp
}
// The output:
// initialized: MainApp$$anon$1@2bfc2bcc
```

```scala mdoc:invisible:reset

```
