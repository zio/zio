---
id: manual-layer-construction
title: "Manual Layer Construction"
---

We said that we can think of the `ZLayer` as a more powerful _constructor_. Constructors are not composable, because they are not values. While a constructor is not composable, `ZLayer` has a nice facility to compose with other `ZLayer`s. So we can say that a `ZLayer` turns a constructor into values.

:::note
In a regular ZIO application we are not required to build the dependency graph through composing layers tougher. Instead, we can provide all dependencies to the ZIO application using `ZIO#provide`, and the ZIO will create the dependency graph manually under the hood. Therefore, use manual layer composition if you know what you're doing.
:::

## Vertical and Horizontal Composition

Assume we have several services with their dependencies, and we need a way to compose and wire up these dependencies to create the dependency graph of our application. `ZLayer` is a ZIO solution for this problem, it allows us to build up the whole application dependency graph by composing layers horizontally and vertically.

```scala mdoc:invisible
trait A
trait B
trait C
trait D
```

### Horizontal Composition

Layers can be composed together horizontally with the `++` operator. When we compose layers horizontally, the new layer requires all the services that both of them require and produces all services that both of them produce. Horizontal composition is a way of composing two layers side-by-side. It is useful when we combine two layers that don't have any relationship with each other.

We can compose `fooLayer` and `barLayer` _horizontally_ to build a layer that has the requirements of both, to provide the capabilities of both, through `fooLayer ++ barLayer`:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???        // A ==> B
val barLayer: ZLayer[C, Nothing  , D] = ???        // C ==> D

val horizontal: ZLayer[A & C, Throwable, B & D] =  // A & C ==> B & D
  fooLayer ++ barLayer
```

### Vertical Composition

We can also compose layers _vertically_ using the `>>>` operator, meaning the output of one layer is used as input for the subsequent layer, resulting in one layer with the requirement of the first, and the output of the second.

For example if we have a layer that requires `A` and produces `B`, we can compose this with another layer that requires `B` and produces `C`; this composition produces a layer that requires `A` and produces `C`. The feed operator, `>>>`, stack them on top of each other by using vertical composition. This sort of composition is like _function composition_, feeding an output of one layer to an input of another:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???  // A ==> B
val barLayer: ZLayer[B, Nothing  , C] = ???  // B ==> C

val horizontal: ZLayer[A, Throwable, C] =    // A ==> C
  fooLayer >>> barLayer
```

## Hidden Versus Passed-through Dependencies

ZLayer has a `passthrough` operator which returns a new layer that produces the outputs of this layer but also passes-through the inputs:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Nothing, B] = ???  // A ==> B

val result1 : ZLayer[A, Nothing, A & B] =  // A ==> A & B
  fooLayer.passthrough
  
val result2 : ZLayer[A, Nothing, A & B] =  // A ==> A & B
  ZLayer.service[A] ++ fooLayer
 
// (A ==> A) ++ (A ==> B)
// (A ==> A & B)
```

By default, the `ZLayer` hides intermediate dependencies when composing vertically. For example, when we compose `fooLayer` with `barLayer` vertically, the output would be a `ZLayer[A, Throwable, C]`. This hides the dependency on the `B` layer. By using the above technique, we can pass through hidden dependencies.

Let's include the `B` service into the upstream dependencies of the final layer using the `ZIO.service[B]`. We can think of `ZIO.service[B]` as an _identity function_ (`B ==> B`).

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???  // A  ==> B
val barLayer: ZLayer[B, Throwable, C] = ???  // B  ==> C

val finalLayer: ZLayer[A & B, Throwable, C] = // A & B ==> C
  (fooLayer ++ ZLayer.service[B]) >>> barLayer

// ((A ==> B) ++ (B ==> B)) >>> (B ==> C)
// (A & B ==> B) >> (B ==> C)
// (A & B ==> C)
```

Or we may want to include the middle services in the output channel of the final layer, resulting in a new layer with the inputs of the first layer and the outputs of both layers:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ??? // A  ==> B
val barLayer: ZLayer[B, Throwable, C] = ??? // B  ==> C

val finalLayer: ZLayer[A, Throwable, B & C] = // A ==> B & C
  fooLayer >>> (ZLayer.service[B] ++ barLayer)
  
// (A ==> B) >>> ((B ==> B) ++ (B ==> C))
// (A ==> B) >>> (B ==> B & C)
// (A ==> B & C)
```

We can do the same with the `>+>` operator:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ??? // A  ==> B
val barLayer: ZLayer[B, Throwable, C] = ??? // B  ==> C

val finalLayer: ZLayer[A, Throwable, B & C] = // A ==> B & C
  fooLayer >+> barLayer
```

This technique is useful when we want to defer the creation of some intermediate services and require them as part of the input of the final layer. For example, assume we have these two layers:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A    , Throwable, B] = ???   // A     ==> B
val barLayer: ZLayer[B & C, Throwable, D] = ???   // B & C ==> D

val finalLayer: ZLayer[A & B & C, Throwable, D] = // A & B & C ==> B & D
  fooLayer >>> barLayer
```

So we can defer the creation of the `C` layer using `ZLayer.service[C]`:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A    , Throwable, B] = ??? // A ==> B 
val barLayer: ZLayer[B & C, Throwable, D] = ??? // B & C ==> D

val layer: ZLayer[A & C, Throwable, D] =        // A & C ==> D
  (fooLayer ++ ZLayer.service[C]) >>> barLayer

// ((A ==> B) ++ (C ==> C)) >>> (B & C ==> D)
// (A & C ==> B & C) >>> (B & C ==> D)
// (A & C ==> D)
```

Here is an example in which we passthrough all requirements to bake a `Cake` so all the requirements are available to all the downstream services:

```scala mdoc:silent
import zio._

trait Baker 
trait Ingredients
trait Oven
trait Dough
trait Cake

lazy val baker      : ZLayer[Any, Nothing, Baker] = ???
lazy val ingredients: ZLayer[Any, Nothing, Ingredients] = ???
lazy val oven       : ZLayer[Any, Nothing, Oven] = ???
lazy val dough      : ZLayer[Baker & Ingredients, Nothing, Dough] = ???
lazy val cake       : ZLayer[Baker & Oven & Dough, Nothing, Cake] = ???

lazy val all: ZLayer[Any, Nothing, Baker & Ingredients & Oven & Dough & Cake] =
  baker >+>       // Baker
  ingredients >+> // Baker & Ingredients
  oven >+>        // Baker & Ingredients & Oven
  dough >+>       // Baker & Ingredients & Oven & Dough
  cake            // Baker & Ingredients & Oven & Dough & Cake
```

This allows a style of composition where the `>+>` operator is used to build a progressively larger set of services, with each new service able to depend on all the services before it. If we passthrough dependencies and later want to hide them we can do so through a simple type ascription:

```scala mdoc:silent
lazy val hidden: ZLayer[Any, Nothing, Cake] = all
```

The `ZLayer` makes it easy to mix and match these styles. If we build our dependency graph more explicitly, we can be confident that dependencies used in multiple parts of the dependency graph will only be created once due to memoization and sharing.

```scala mdoc:invisible:reset

```

Using these simple operators we can build complex dependency graphs.

## Updating Local Dependencies

Given a layer, it is possible to update one or more components it provides. We update a dependency in two ways:

1. **Using the `update` Method** — This method allows us to replace one requirement with a different implementation:

```scala mdoc:compile-only
import zio._

val origin: ZLayer[Any, Nothing, String & Int & Double] = 
  ZLayer.succeedEnvironment(ZEnvironment[String, Int, Double]("foo", 123, 1.3))

val updated1 = origin.update[String](_ + "bar")
val updated2 = origin.update[Int](_ + 5)
val updated3 = origin.update[Double](_ - 0.3)
```

Here is an example of updating a config layer:

```scala mdoc:compile-only
import zio._

import java.io.IOException

case class AppConfig(poolSize: Int)

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[AppConfig, IOException, Unit] =
    for {
      config <- ZIO.service[AppConfig]
      _ <- Console.printLine(s"Application config after the update operation: $config")
    } yield ()


  val appLayers: ZLayer[Any, Nothing, AppConfig] =
    ZLayer(ZIO.succeed(AppConfig(5)).debug("Application config initialized"))

  val updatedConfig: ZLayer[Any, Nothing, AppConfig] =
    appLayers.update[AppConfig](c =>
      c.copy(poolSize = c.poolSize + 10)
    )

  def run = myApp.provide(updatedConfig)
}

// Output:
// Application config initialized: AppConfig(5)
// Application config after the update operation: AppConfig(15)
```

2. **Using Horizontal Composition** — Another way to update a requirement is to horizontally compose in a layer that provides the updated service. The resulting composition will replace the old layer with the new one:

```scala mdoc:compile-only
import zio._

val origin: ZLayer[Any, Nothing, String & Int & Double] =
  ZLayer.succeedEnvironment(ZEnvironment[String, Int, Double]("foo", 123, 1.3))

val updated = origin ++ ZLayer.succeed(321)
```

Let's see an example of updating a config layer:

```scala mdoc:compile-only
import zio._

import java.io.IOException

case class AppConfig(poolSize: Int)

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[AppConfig, IOException, Unit] =
    for {
      config <- ZIO.service[AppConfig]
      _      <- Console.printLine(s"Application config after the update operation: $config")
    } yield ()


  val appLayers: ZLayer[Any, Nothing, AppConfig] =
    ZLayer(ZIO.succeed(AppConfig(5)).debug("Application config initialized"))

  val updatedConfig: ZLayer[Any, Nothing, AppConfig] =
    appLayers ++ ZLayer.succeed(AppConfig(8))

  def run = myApp.provide(updatedConfig)
}
// Output:
// Application config initialized: AppConfig(5)
// Application config after the update operation: AppConfig(8)
```

## Cyclic Dependencies

The `ZLayer` mechanism makes it impossible to build cyclic dependencies, making the initialization process very linear, by construction.
