---
id: automatic-layer-construction
title: "Automatic Layer Construction"
---

ZIO also has an automatic layer construction facility, which takes care of building dependency graphs from the individual layers and building blocks. So instead of manually composing layers together to build the final layer, we can only provide individual layers to the ZIO application, and it will do the rest.

The automatic layer construction takes place at the _compile-time_, so if there is a problem in providing a layer, we will receive an error or warning message. So it helps us to diagnose the problem. Additionally, it has a way to print the dependency graph using built-in debug layers.

## Providing Individual Layers to a ZIO Application

When we provide individual layers using `ZIO#provide`, `ZIO#provideCustom`, or `ZIO#provideSome` to a ZIO application, the compiler will create the dependency graph automatically from the provided layers:

:::info
We have a [separate section](dependency-propagation.md) that describes different methods for providing layers to the ZIO application.
:::

Assume we have written the following services (`Cake`, `Chocolate`, `Flour`, and `Spoon`):

```scala mdoc:silent
import zio._

trait Cake

object Cake {
  val live: ZLayer[Chocolate & Flour, Nothing, Cake] =
    for {
      _ <- ZLayer.environment[Chocolate & Flour]
      cake <- ZLayer.succeed(new Cake {})
    } yield cake
}

trait Spoon

object Spoon {
  val live: ULayer[Spoon] =
    ZLayer.succeed(new Spoon {})
}

trait Chocolate

object Chocolate {
  val live: ZLayer[Spoon, Nothing, Chocolate] =
    ZLayer.service[Spoon].project(_ => new Chocolate {})
}

trait Flour

object Flour {
  val live: ZLayer[Spoon, Nothing, Flour] =
    ZLayer.service[Spoon].project(_ => new Flour {})
}
```

The `Cake` service has the following dependency graph:

```
          Cake
          /   \
   Chocolate   Flour
       |         |
     Spoon     Spoon
```

Now we can write an application that uses the `Cake` service as below:

```scala mdoc:silent
import zio._

import java.io.IOException

val myApp: ZIO[Cake, IOException, Unit] = for {
  cake <- ZIO.service[Cake]
  _    <- Console.printLine(s"Yay! I baked a cake with flour and chocolate: $cake")
} yield ()
```

The type of `myApp` indicates we should provide `Cake` to this ZIO application to run it. Let's give it that and see what happens:

```scala mdoc:fail:silent
object MainApp extends ZIOAppDefault {
  def run =
    myApp.provide(Cake.live)
}

// error:
// 
// ──── ZLAYER ERROR ────────────────────────────────────────────────────
// 
//  Please provide layers for the following 2 types:
// 
//    Required by Cake.live
//    1. Chocolate
//    2. Flour
//    
// ──────────────────────────────────────────────────────────────────────
```

Here are the errors that will be printed:

```
──── ZLAYER ERROR ────────────────────────────────────────────────────

 Please provide layers for the following 2 types:

   Required by Cake.live
   1. Chocolate
   2. Flour
   
──────────────────────────────────────────────────────────────────────
```

It says that we missed providing `Chocolate` and `Flour` layers. Now let's add these two missing layers:

```scala mdoc:fail:silent
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    myApp.provide(
      Cake.live,
      Chocolate.live,
      Flour.live
    )
}

// error:
// 
// ──── ZLAYER ERROR ────────────────────────────────────────────────────
// 
// Please provide a layer for the following type:
// 
// Required by Flour.live
// 1. Spoon
// 
// Required by Chocolate.live
// 1. Spoon
// 
// ──────────────────────────────────────────────────────────────────────
```

Again, the compiler asks us to provide another dependency called `Spoon`:

```
──── ZLAYER ERROR ────────────────────────────────────────────────────

Please provide a layer for the following type:

Required by Flour.live
1. Spoon

Required by Chocolate.live
1. Spoon

──────────────────────────────────────────────────────────────────────
```

Finally, our application compiles without any errors:

```scala mdoc
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    myApp.provide(
      Cake.live,
      Chocolate.live,
      Flour.live,
      Spoon.live  
    )
}
```

Note that the order of dependencies doesn't matter. We can provide them in any order.

Now, let's compare the automatic layer construction with the manual one:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ULayer[Cake] =
      (((Spoon.live >>> Chocolate.live) ++ (Spoon.live >>> Flour.live)) >>> Cake.live)

  def run = myApp.provideLayer(layers)

}
```

## Automatically Assembling Layers

1. **ZLayer.make[R]** — Using `ZLayer.make[R]`, we can provide a type `R` and then provide individual layers as arguments, it will automatically assemble these layers to create a layer of type `R`.

For example, we can create a `Cake` layer as below:

```scala mdoc:compile-only
import zio._

val cakeLayer: ZLayer[Any, Nothing, Cake] =
  ZLayer.make[Cake](
    Cake.live,
    Chocolate.live,
    Flour.live,
    Spoon.live
  )
```

We can also create a layer for intersections of services:

```scala mdoc:compile-only
import zio._

val chocolateAndFlourLayer: ZLayer[Any, Nothing, Chocolate & Flour] =
  ZLayer.make[Chocolate & Flour](
    Chocolate.live,
    Flour.live,
    Spoon.live
  )
```

2. **ZLayer.makeSome[R0, R]** — Automatically constructs a layer for the provided type `R`, leaving a remainder `R0`:

```scala mdoc:compile-only
import zio._

val cakeLayer: ZLayer[Spoon, Nothing, Cake] =
  ZLayer.makeSome[Spoon, Cake](
    Cake.live,
    Chocolate.live,
    Flour.live
  )
```

## ZLayer Debugging

To debug ZLayer construction, we have two built-in layers, i.e., `ZLayer.Debug.tree` and `ZLayer.Debug.mermaid`.

Let's include the `ZLayer.Debug.tree` layer into the layer construction:

```scala
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    myApp.provide(
      Cake.live,
      Chocolate.live,
      Flour.live,
      Spoon.live,
      ZLayer.Debug.tree
    )
}
```

The following debug messages will be generated by the compiler:

```
[info]   ZLayer Wiring Graph
[info]
[info] ◉ Cake.live
[info] ├─◑ Chocolate.live
[info] │ ╰─◑ Spoon.live
[info] ╰─◑ Flour.live
[info]   ╰─◑ Spoon.live
[info] 
```

If we use the `ZLayer.Debug.mermaid` layer, it will generate the following debug messages:

```
[info]   ZLayer Wiring Graph  
[info] 
[info] ◉ Cake.live
[info] ├─◑ Chocolate.live
[info] │ ╰─◑ Spoon.live
[info] ╰─◑ Flour.live
[info]   ╰─◑ Spoon.live
[info] 
[info] Mermaid Live Editor Link
[info] https://mermaid-js.github.io/mermaid-live-editor/edit/#eyJjb2RlIjoiZ3JhcGhcbiAgICBDb25zb2xlLmxpdmVcbiAgICBDYWtlLmxpdmUgLS0+IENob2NvbGF0ZS5saXZlXG4gICAgQ2FrZS5saXZlIC0tPiBGbG91ci5saXZlXG4gICAgRmxvdXIubGl2ZSAtLT4gU3Bvb24ubGl2ZVxuICAgIFNwb29uLmxpdmVcbiAgICBDaG9jb2xhdGUubGl2ZSAtLT4gU3Bvb24ubGl2ZVxuICAgICIsIm1lcm1haWQiOiAie1xuICBcInRoZW1lXCI6IFwiZGVmYXVsdFwiXG59IiwgInVwZGF0ZUVkaXRvciI6IHRydWUsICJhdXRvU3luYyI6IHRydWUsICJ1cGRhdGVEaWFncmFtIjogdHJ1ZX0=
```
