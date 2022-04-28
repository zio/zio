---
id: zlayer
title: "ZLayer"
---

A `ZLayer[-RIn, +E, +ROut]` describes a layer of an application: every layer in an application requires some services as input `RIn` and produces some services as the output `ROut`.

We can think of a layer as mental model of an asynchronous function from `RIn` to the `Either[E, ROut]`:

```scala
type ZLayer[-RIn, +E, +ROut] = RIn => async Either[E, ROut]
```

For example, a `ZLayer[Clock & Logging, Throwable, Database]` can be thought of as a function that map `Clock` and `Logging` services into `Database` service:

```scala
(Clock, Logging) => Database
```

So we can say that the `Database` service has two dependencies: `Clock` and `Logging` services.

In some cases, a `ZLayer` may not have any dependencies or requirements from the environment. In this case, we can specify `Any` for the `RIn` type parameter. The [`Layer`](layer.md) type alias provided by ZIO is a convenient way to define a layer without requirements.

ZLayers are:

1. **Recipes for Creating Services** — They describe how to create services from given dependencies. For example, the `ZLayer[Logging & Database, Throwable, UserRepo]` is a recipe for building a service that requires `Logging` and `Database` service, and it produces a `UserRepo` service.

2. **An Alternative to Constructors** — We can think of `ZLayer` as a more powerful version of a constructor, it is an alternative way to represent a constructor. Like a constructor, it allows us to build the `ROut` service in terms of its dependencies (`RIn`).

3. **Composable** — Because of their excellent **composition properties**, layers are the idiomatic way in ZIO to create services that depend on other services. We can define layers that are relying on each other.

4. **Effectful and Resourceful** — The construction of ZIO layers can be effectful and resourceful. They can be acquired effectfully and safely released when the services are done being utilized or even in case of failure, interruption, or defects in the application. 
  
  For example, to create a recipe for a `Database` service, we should describe how the `Database` will be initialized using an acquisition action. In addition, it may contain information about how the `Database` releases its connection pools.

6. **Asynchronous** — Unlike class constructors which are blocking, `ZLayer` is fully asynchronous and non-blocking. Note that in non-blocking applications we typically want to avoid creating something that is blocking inside its constructor.

  For example, when we are constructing some sort of Kafka streaming service, we might want to connect to the Kafka cluster in the constructor of our service, which takes some time. So it wouldn't be a good idea to block inside the constructor. There are some workarounds for fixing this issue, but they are not as perfect as the ZIO solution which allows for asynchronous, non-blocking constructors.

6. **Parallelism** — ZIO layers can be acquired in parallel, unlike class constructors, which do not support parallelism. When we compose multiple layers and then acquire them, the construction of each layer will occur in parallel. This will reduce the initialization time of ZIO applications with a large number of dependencies.

  With ZIO ZLayer, our constructor could be asynchronous, but it could also block. We can acquire resources asynchronously or in a blocking fashion, and spend some time doing that, and we don't need to worry about it. That is not an anti-pattern. This is the best practice with ZIO. And that is because `ZLayer` has the full power of the `ZIO` data type, and as a result, we have strictly more power on our constructors with `ZLayer`.

7. **Resilient** — Layer construction can be resilient. So if the acquiring phase fails, we can have a schedule to retry the acquiring stage. This helps us write apps that are error-proof and respond appropriately to failures.

Let's see how we can create a layer:

## Creation

There are four main ways to create a ZLayer:
1. `ZLayer.succeed` for creating layers from simple values.
2. `ZLayer.scoped` for creating layers with _for comprehension_ style from resourceful effects.
3. `ZLayer.apply`/`ZLayer.fromZIO` for creating layers with _for comprehension_ style from effectual but not resourceful effects.
4. `ZLayer.fromFunction` for creating layers that are neither effectual nor resourceful.

Now let's look at each of these methods.

### From a Simple Value or an Existing Service

With `ZLayer.succeed` we can construct a `ZLayer` from a value. It returns a `ULayer[A]` value, which represents a layer of an application that has a service of type `A`:

```scala
def succeed[A: Tag](a: A): ULayer[A]
```

Using `ZLayer.succeed` we can create a layer containing _simple value_ or a _service_:

1. To create a layer from a _simple value_:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)

val configLayer: ULayer[AppConfig] = ZLayer.succeed(AppConfig("localhost", 8080))
```

In the example above, we created a `configLayer` that provides us an instance of `AppConfig`.

2. To create a layer from an _existing service_:

```scala mdoc:compile-only
import zio._

trait Logging {
  def log(line: String): UIO[Unit]
}

object Logging {
  val layer: ZLayer[Any, Nothing, Logging] = 
    ZLayer.succeed( 
      new Logging {
        override def log(line: String): UIO[Unit] =
          ZIO.succeed(println(line))
      }
    )
}
```

### From Resourceful Effects (Scoped Resources)

Some components of our applications need to be scoped, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). As we stated before, the construction of ZIO layers can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

The `ZLayer` relies on the powerful `Scope` data type and this makes this process extremely simple. We can lift any scoped `ZIO` to `ZLayer` by providing a scoped resource to the `ZLayer.apply` constructor:

```scala mdoc:silent:nest
import zio._
import scala.io.BufferedSource

val fileLayer: ZLayer[Any, Throwable, BufferedSource] =
  ZLayer.scoped {
    ZIO.fromAutoCloseable(
      ZIO.attempt(scala.io.Source.fromFile("file.txt"))
    )
  }
```

Let's see a real-world example of creating a layer from scoped resources. Assume we have the following `UserRepository` service:

```scala mdoc:silent
import zio._
import scala.io.Source._
import java.io.{FileInputStream, FileOutputStream, Closeable}

trait DBConfig
trait Transactor
trait User

def dbConfig: Task[DBConfig] = ZIO.attempt(???)
def initializeDb(config: DBConfig): Task[Unit] = ZIO.attempt(???)
def makeTransactor(config: DBConfig): ZIO[Scope, Throwable, Transactor] = ZIO.attempt(???)

trait UserRepository {
  def save(user: User): Task[Unit]
}

case class UserRepositoryLive(xa: Transactor) extends UserRepository {
  override def save(user: User): Task[Unit] = ZIO.attempt(???)
}
```

Assume we have written a scoped `UserRepository`:

```scala mdoc:silent:nest
def scoped: ZIO[Scope, Throwable, UserRepository] = 
  for {
    cfg <- dbConfig
    _   <- initializeDb(cfg)
    xa  <- makeTransactor(cfg)
  } yield new UserRepositoryLive(xa)
```

We can convert that to `ZLayer` with `ZLayer.apply`:

```scala mdoc:nest
val usersLayer : ZLayer[Any, Throwable, UserRepository] =
  ZLayer.scoped(scoped)
```

```scala mdoc:invisible:reset

```

### From Non-resourceful Effects

We can create `ZLayer` from any `ZIO` effect by using `ZLayer.fromZIO`/`ZLayer.apply` constructor.

For example, assume we have a `ZIO` effect that reads the application config from a file, we can create a layer from that:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)
  
def loadConfig : Task[AppConfig] = 
  ZIO.attempt(???)

object AppConfig {
  val layer: TaskLayer[AppConfig] = 
    ZLayer(loadConfig)  // or ZLayer.fromZIO(loadConfig)
} 
```

This is the for-comprehension way of creating a ZIO service using `ZLayer.apply`:

```scala mdoc:compile-only
import zio._

trait A
trait B
trait C
case class CLive(a: A, b: B) extends C

object CLive {
  val layer: ZLayer[A & B, Nothing, C] =
    ZLayer {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield CLive(a, b)
    }
}
```

### From Functions

A `ZLayer[R, E, A]` can be thought of as a function from `R` to `A`. So we can convert functions to the `ZLayer` using the `ZLayer.fromFunction` constructor.

In the following example, the `CLive` implementation requires two `A` and `B` services, and we can easily convert that case class to a `ZLayer`:

```scala mdoc:compile-only
import zio._

trait A
trait B
trait C
case class CLive(a: A, b: B) extends C

object CLive {
  val layer: ZLayer[A & B, Nothing, C] = 
    ZLayer.fromFunction(CLive.apply _)
}
```

Below is a complete working example:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  final case class DatabaseConfig()

  object DatabaseConfig {
    val live = ZLayer.succeed(DatabaseConfig())
  }

  final case class Database(databaseConfig: DatabaseConfig)

  object Database {
    val live: ZLayer[DatabaseConfig, Nothing, Database] =
      ZLayer.fromFunction(Database.apply _)
  }

  final case class Analytics()

  object Analytics {
    val live: ULayer[Analytics] = ZLayer.succeed(Analytics())
  }

  final case class Users(database: Database, analytics: Analytics)

  object Users {
    val live = ZLayer.fromFunction(Users.apply _)
  }

  final case class App(users: Users, analytics: Analytics) {
    def execute: UIO[Unit] =
      ZIO.debug(s"This app is made from ${users} and ${analytics}")
  }

  object App {
    val live = ZLayer.fromFunction(App.apply _)
  }

  def run =
    ZIO
      .serviceWithZIO[App](_.execute)
      // Cannot use `provide` due to this dotty bug: https://github.com/lampepfl/dotty/issues/12498
      .provideLayer(
        (((DatabaseConfig.live >>> Database.live) ++ Analytics.live >>> Users.live) ++ Analytics.live) >>> App.live
      )
}
```

```scala mdoc:invisible:reset

```

## Building Dependency Graph

We have two options to build a dependency graph:
1. Manual layer construction
2. Automatic layer construction

The first method uses ZIO's composition operators such as horizontal (`++`) and vertical (`>>>`) compositions. The second one uses macro and automatically creates the dependency graph at compile time.

## Manual Layer Construction

We said that we can think of the `ZLayer` as a more powerful _constructor_. Constructors are not composable, because they are not values. While a constructor is not composable, `ZLayer` has a nice facility to compose with other `ZLayer`s. So we can say that a `ZLayer` turns a constructor into values.

> **Note**:
>
> In a regular ZIO application we are not required to build the dependency graph through composing layers tougher. Instead, we can provide all dependencies to the ZIO application using `ZIO#provide`, and the ZIO will create the dependency graph manually under the hood. Therefore, use manual layer composition if you know what you're doing.

### Vertical and Horizontal Composition

Assume we have several services with their dependencies, and we need a way to compose and wire up these dependencies to create the dependency graph of our application. `ZLayer` is a ZIO solution for this problem, it allows us to build up the whole application dependency graph by composing layers horizontally and vertically.

```scala mdoc:invisible
trait A
trait B
trait C
trait D
```

1. **Horizontal Composition** — Layers can be composed together horizontally with the `++` operator. When we compose layers horizontally, the new layer requires all the services that both of them require and produces all services that both of them produce. Horizontal composition is a way of composing two layers side-by-side. It is useful when we combine two layers that don't have any relationship with each other.

We can compose `fooLayer` and `barLayer` _horizontally_ to build a layer that has the requirements of both, to provide the capabilities of both, through `fooLayer ++ barLayer`:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???        // A ==> B
val barLayer: ZLayer[C, Nothing  , D] = ???        // C ==> D

val horizontal: ZLayer[A & C, Throwable, B & D] =  // A & C ==> B & D
  fooLayer ++ barLayer
```

2. **Vertical Composition** — We can also compose layers _vertically_ using the `>>>` operator, meaning the output of one layer is used as input for the subsequent layer, resulting in one layer with the requirement of the first, and the output of the second.  

  For example if we have a layer that requires `A` and produces `B`, we can compose this with another layer that requires `B` and produces `C`; this composition produces a layer that requires `A` and produces `C`. The feed operator, `>>>`, stack them on top of each other by using vertical composition. This sort of composition is like _function composition_, feeding an output of one layer to an input of another:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???  // A ==> B
val barLayer: ZLayer[B, Nothing  , C] = ???  // B ==> C

val horizontal: ZLayer[A, Throwable, C] =    // A ==> C
  fooLayer >>> barLayer
```

### Hidden Versus Passed-through Dependencies

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

### Updating Local Dependencies

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
    ZLayer(ZIO.succeed(AppConfig(5)).debug("Application config initialized")) ++
      Console.live

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
    ZLayer(ZIO.succeed(AppConfig(5)).debug("Application config initialized")) ++
      Console.live

  val updatedConfig: ZLayer[Any, Nothing, AppConfig] =
    appLayers ++ ZLayer.succeed(AppConfig(8))

  def run = myApp.provide(updatedConfig)
}
// Output:
// Application config initialized: AppConfig(5)
// Application config after the update operation: AppConfig(8)
```

### Cyclic Dependencies

The `ZLayer` mechanism makes it impossible to build cyclic dependencies, making the initialization process very linear, by construction.

## Automatic Layer Construction

ZIO also has an automatic layer construction facility, which takes care of building dependency graphs from the individual layers and building blocks. So instead of manually composing layers together to build the final layer, we can only provide individual layers to the ZIO application, and it will do the rest.

The automatic layer construction takes place at the _compile-time_, so if there is a problem in providing a layer, we will receive an error or warning message. So it helps us to diagnose the problem. Additionally, it has a way to print the dependency graph using built-in debug layers.

### Providing Individual Layers to a ZIO Application

When we provide individual layers using `ZIO#provide`, `ZIO#provideCustom`, or `ZIO#provideSome` to a ZIO application, the compiler will create the dependency graph automatically from the provided layers:

> **Note:**
> 
> We have a [separate section](#dependency-propagation) that describes different methods for providing layers to the ZIO application.

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

The type of `myApp` indicates we should provide `Console` and `Cake` to this ZIO application to run it. Let's give it those and see what happens:

```scala mdoc:fail:silent
object MainApp extends ZIOAppDefault {
  def run =
    myApp.provide(
      Cake.live,
      Console.live
    )
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
      Console.live,
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

### Automatically Assembling Layers

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

### ZLayer Debugging

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

## Dependency Propagation

When we write an application, our application has a lot of dependencies. We need a way to provide implementations and to feed and propagate all dependencies throughout the whole application. We can solve the propagation problem by using _ZIO environment_.

During the development of an application, we don't care about implementations. Incrementally, when we use various effects with different requirements on their environment, all parts of our application compose together, and at the end of the day we have a ZIO effect which requires some services as an environment. Before running this effect by `unsafeRun` we should provide an implementation of these services into the ZIO Environment of that effect.

ZIO has some facilities for doing this. `ZIO#provide` is the core function that allows us to _feed_ an `R` to an effect that requires an `R`.

Notice that the act of `provide`ing an effect with its environment, eliminates the environment dependency in the resulting effect type, represented by type `Any` of the resulting environment.

#### Using `ZIO#provideEnvironment` Method

The `ZIO#provideEnvironment` takes an instance of `ZEnvironment[R]` and provides it to the `ZIO` effect which eliminates its dependency on `R`:

```scala
trait ZIO[-R, +E, +A] {
  def provideEnvironment(r: => ZEnvironment[R]): IO[E, A]
}
```

This is similar to dependency injection, and the `provide*` function can be thought of as _inject_.

```scala mdoc:invisible:reset
import zio._
```

Assume we have the following services:

```scala mdoc:silent:nest
trait Logging {
  def log(str: String): UIO[Unit]
}

object Logging {
  def log(line: String) = ZIO.serviceWithZIO[Logging](_.log(line))
}
```

Let's write a simple program using `Logging` service:

```scala mdoc:silent:nest
val app: ZIO[Logging, Nothing, Unit] = Logging.log("Application Started!")
```

We can `provide` implementation of `Logging` service into the `app` effect:

```scala mdoc:silent:nest
val loggingImpl = new Logging {
  override def log(line: String): UIO[Unit] =
    ZIO.succeed(println(line))
}

val effect = app.provideEnvironment(ZEnvironment(loggingImpl))
```

Most of the time, we don't use `ZIO#provideEnvironment` directly to provide our services; instead, we use `ZLayer` to construct the dependency graph of our application, then we use methods like `ZIO#provide`, `ZIO#provideSome` and `ZIO#provideCustom` to propagate dependencies into the environment of our ZIO effect.

#### Using `ZIO#provide` Method

Unlike the `ZIO#provideEnvironment` which takes a `ZEnvironment[R]`, the `ZIO#provide` takes a `ZLayer` to the ZIO effect and translates it to another level.

Assume we have written this piece of program that requires `Clock` and `Console` services:

```scala mdoc:silent:nest
import zio.Clock._
import zio.Console._
import zio.Random._

val myApp: ZIO[Any, Nothing, Unit] = for {
  random  <- nextInt 
  _       <- printLine(s"A random number: $random").orDie
  current <- currentDateTime
  _       <- printLine(s"Current Data Time: $current").orDie
} yield ()
```

We provide implementations of `Random`, `Console` and `Clock` services to the `myApp` effect by using `ZIO#provide` method:

```scala mdoc:silent:nest
val mainEffect: ZIO[Any, Nothing, Unit] = 
  myApp
```

As we see, the type of our effect converted from `ZIO[Random & Console & Clock, Nothing, Unit]` which requires three services to `ZIO[Any, Nothing, Unit]` effect which doesn't require any services.

#### Using `ZIO#provideSome` Method

Sometimes we have written a program, and we don't want to provide all its requirements. In these cases, we can use `ZIO#provideSome` to partially apply some layers to the `ZIO` effect.

In the previous example, if we just want to provide the `Console`, we should use `ZIO#provideSome`:

```scala
val mainEffectSome: ZIO[Any, Nothing, Unit] = 
  myApp
```

> **Note:**
>
> When using `ZIO#provideSome[R0]`, we should provide the remaining type as `R0` type parameter. This workaround helps the compiler to infer the proper types.

#### Using `ZIO#provideCustom` Method

`ZEnv` is a convenient type alias that provides several built-in ZIO services that are useful in most applications. Sometimes we have written a program that contains ZIO built-in services and some other services that are not part of `ZEnv`.

As `ZEnv` provides us the implementation of built-in services, we just need to provide layers for those services that are not part of the `ZEnv`. The `ZIO#provideCustom` method helps us to do so. It returns an effect that only depends on `ZEnv`.

Let's write an effect that has some built-in services and also has a `Logging` service:

```scala mdoc:invisible:reset
import zio._
import zio.Console._
import zio.Clock._
```

```scala mdoc:silent
trait Logging {
  def log(str: String): UIO[Unit]
}

object Logging {
  def log(line: String) = ZIO.serviceWithZIO[Logging](_.log(line))
}

object LoggingLive {
  val layer: ULayer[Logging] = ZLayer.succeed {
    new Logging {
      override def log(str: String): UIO[Unit] = ???
    }
  }
}

val myApp: ZIO[Logging, Nothing, Unit] = for {
  _       <- Logging.log("Application Started!")
  current <- Clock.currentDateTime
  _       <- Console.printLine(s"Current Data Time: $current").orDie
} yield ()
```

This program uses two ZIO built-in services, `Console` and `Clock`. We don't need to provide `Console` and `Clock` manually, to reduce some boilerplate, we use `ZEnv` to satisfy some common base requirements.

By using `ZIO#provideCustom` we only provide the `Logging` layer, and it returns a `ZIO` effect which only requires `ZEnv`:

```scala mdoc:silent
val mainEffect: ZIO[ZEnv, Nothing, Unit] = myApp.provide(LoggingLive.layer)
```

## Environment Scope

We can create a ZIO application by providing a local or a global environment, or a combination:

### Global Environment

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

### Local Environment

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

ZIO Test's [Live service](../test/environment/live.md) uses this pattern to provide real environment to a single part of an effect.

## Layer Memoization

Layer memoization allows a layer to be created once and used multiple times in the dependency graph. So if we use the same layer twice, e.g. `(a >>> b) ++ (a >>> c)`, then the `a` layer will be allocated only once.

### Layers are Memoized by Default when Providing Globally

One important feature of a ZIO application is that layers are shared by default, meaning that if the same layer is used twice, and if we provide the layer [globally](#global-environment) the layer will only be allocated a single time. For every layer in our dependency graph, there is only one instance of it that is shared between all the layers that depend on it.

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

#### Acquiring a Fresh Version

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

### Layers are not Memoized When Providing Locally

If we don't provide a layer globally but instead provide them [locally](#local-environment), that layer doesn't support memoization by default.

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

#### Manual Memoization

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

## Other Operators

### Converting a Layer to a Scoped Value

Every `ZLayer` can be converted to a scoped `ZIO` by using `ZLayer.build`:

```scala mdoc:compile-only
import zio._

trait Database {
  def close: UIO[Unit]
}

object Database {
  def connect: ZIO[Any, Throwable, Database] = ???
}

val database: ZLayer[Any, Throwable, Database] =
  ZLayer.scoped {
    ZIO.acquireRelease {
      Database.connect.debug("connecting to the database")
    } { database =>
      database.close
    }
  }

val scopedDatabase: ZIO[Scope, Throwable, ZEnvironment[Database]] =
  database.build
```

### Falling Back to an Alternate Layer

If a layer fails, we can provide an alternative layer by using `ZLayer#orElse` so it will fall back to the second layer:

```scala mdoc:compile-only
import zio._

trait Database

val postgresDatabaseLayer: ZLayer[Any, Throwable, Database] = ???
val inmemoryDatabaseLayer: ZLayer[Any, Throwable, Database] = ???

val databaseLayer: ZLayer[Any, Throwable, Database] =
  postgresDatabaseLayer.orElse(inmemoryDatabaseLayer)
```

### Converting a Layer to a ZIO Application

Sometimes our entire application is a ZIO Layer, e.g. an HTTP Server, so by calling the `ZLayer#launch` we can convert that to a ZIO application. This will build the layer and use it until it is interrupted.

```scala mdoc:invisible
import zio._

trait HttpServer
trait JsonParser
trait TemplateEngine

object JsonParserLive {
  val layer: ULayer[JsonParser] = ZLayer.succeed(???)
}

object TemplateEngineLive {
  val layer: ULayer[TemplateEngine] = ZLayer.succeed(???)
}
```

```scala
object MainApp extends ZIOAppDefault {

  val httpServer: ZLayer[Any, Nothing, HttpServer] =
    ZLayer.make[HttpServer](
      JsonParserLive.layer,
      TemplateEngineLive.layer 
    )

  def run = httpServer.launch

}
``` 

```scala mdoc:invisible:reset

```

### Retrying

We can retry constructing a layer in case of failure:

```scala mdoc:invisible
trait DatabaseConnection
```

```scala mdoc:compile-only
import zio._

val databaseLayer: ZLayer[Any, Throwable, DatabaseConnection]   = ???

val retriedLayer : ZLayer[Clock, Throwable, DatabaseConnection] = databaseLayer.retry(Schedule.fibonacci(1.second))
```

### Layer Projection

We can project out a part of `ZLayer` by providing a projection function to the `ZLayer#project` method:

```scala mdoc:compile-only
import zio._

case class Connection(host: String, port: Int) 
case class Login(user: String, password: String)

case class DBConfig(
  connection: Connection, 
  login: Login
)

val connection: ZLayer[DBConfig, Nothing, Connection] = 
  ZLayer.service[DBConfig].project(_.connection)
```

### Tapping

We can perform a specified effect based on the success or failure result of the layer using `ZLayer#tap`/`ZLayer#tapError`. This would not change the layer's signature:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)

val config: ZLayer[Any, Throwable, AppConfig] =
  ZLayer.fromZIO(
    ZIO.attempt(???) // reading config from a file
  )

val res: ZLayer[Any, Throwable, AppConfig] =
  config
    .tap(cnf => ZIO.debug(s"layer acquisition succeeded with $cnf"))
    .tapError(err => ZIO.debug(s"error occurred during reading the config $err"))
```

## Examples

### An Example of a ZIO Application with a Simple Dependency

This application demonstrates a ZIO program with a single dependency on a simple `AppConfig`:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)

object MainApp extends ZIOAppDefault {

  // Define our simple ZIO program
  val zio: ZIO[AppConfig, Nothing, Unit] = 
    for {
      config <- ZIO.service[AppConfig]
      _      <- ZIO.succeed(println(s"Application started with config: $config"))
    } yield ()

  // Create a ZLayer that produces an AppConfig and can be used to satisfy the AppConfig 
  // dependency that the program has
  val defaultConfig: ULayer[AppConfig] = ZLayer.succeed(AppConfig(10))

  // Run the program, providing the `defaultConfig`
  def run = zio.provide(defaultConfig)
}

```

### An Example of a ZIO Application with Multiple Dependencies

In the following example, our ZIO application has several dependencies:
- `zio.Clock`
- `zio.Console`
- `B`

And also the `B` service depends upon the `A` service:

```scala mdoc:compile-only
import zio._

import java.io.IOException

trait A {
  def letsGoA(v: Int): UIO[String]
}

object A {
  def letsGoA(v: Int): URIO[A, String] = ZIO.serviceWithZIO(_.letsGoA(v))
}

case class ALive() extends A {
  override def letsGoA(v: Int): UIO[String] = ZIO.succeed(s"done: v = $v ")
}

object ALive {
  val layer: ULayer[A] = ZLayer.succeed(ALive())
}

trait B {
  def letsGoB(v: Int): UIO[String]
}

object B {
  def letsGoB(v: Int): URIO[B, String] = ZIO.serviceWithZIO(_.letsGoB(v))
}

case class BLive(serviceA: A) extends B {
  def letsGoB(v: Int): UIO[String] = serviceA.letsGoA(v)
}

object BLive {
  val layer: ZLayer[A, Nothing, BLive] = ZLayer(ZIO.service[A].map(BLive(_)))
}


object MainApp extends ZIOAppDefault {

  val program: ZIO[B, IOException, Unit] =
    for {
      _ <- Console.printLine(s"Welcome to ZIO!")
      _ <- Clock.sleep(1.second)
      r <- B.letsGoB(10)
      _ <- Console.printLine(r)
    } yield ()

  def run = program.provide(ALive.layer, BLive.layer)

}

// The output: 
// Welcome to ZIO!
// done: v = 10 
```

### An Example of Manually Generating a Dependency Graph

Suppose we have defined the ‍‍`UserRepo`, `DocumentRepo`, `Database`, `BlobStorage`, and `Cache` services and their respective implementations as follows:

```scala mdoc:silent
import zio._

case class User(email: String, name: String)

trait UserRepo {
  def save(user: User): Task[Unit]

  def get(email: String): Task[User]
}

object UserRepo {
  def save(user: User): ZIO[UserRepo, Throwable, Unit] =
    ZIO.serviceWithZIO(_.save(user))

  def get(email: String): ZIO[UserRepo, Throwable, User] =
    ZIO.serviceWithZIO(_.get(email))
}

case class UserRepoLive(cache: Cache, database: Database) extends UserRepo {
  override def save(user: User): Task[Unit] = ???

  override def get(email: String): Task[User] = ???
}

object UserRepoLive {
  val layer: URLayer[Cache & Database, UserRepo] =
    ZLayer {
      for {
        cache    <- ZIO.service[Cache]
        database <- ZIO.service[Database]
      } yield UserRepoLive(cache, database)
    }
}

trait Database

case class DatabaseLive() extends Database

object DatabaseLive {
  val layer: ZLayer[Any, Nothing, Database] =
    ZLayer.succeed(DatabaseLive())
}

trait Cache {
  def save(key: String, value: Array[Byte]): Task[Unit]

  def get(key: String): Task[Array[Byte]]

  def remove(key: String): Task[Unit]
}

class InmemeoryCache() extends Cache {
  override def save(key: String, value: Array[Byte]): Task[Unit] = ???

  override def get(key: String): Task[Array[Byte]] = ???

  override def remove(key: String): Task[Unit] = ???
}

object InmemoryCache {
  val layer: ZLayer[Any, Throwable, Cache] =
    ZLayer(ZIO.attempt(new InmemeoryCache).debug("initialized"))
}

class PersistentCache() extends Cache {
  override def save(key: String, value: Array[Byte]): Task[Unit] = ???

  override def get(key: String): Task[Array[Byte]] = ???

  override def remove(key: String): Task[Unit] = ???
}

object PersistentCache {
  val layer: ZLayer[Any, Throwable, Cache] =
    ZLayer(ZIO.attempt(new PersistentCache).debug("initialized"))
}

case class Document(title: String, author: String, body: String)

trait DocumentRepo {
  def save(document: Document): Task[Unit]

  def get(id: String): Task[Document]
}

object DocumentRepo {
  def save(document: Document): ZIO[DocumentRepo, Throwable, Unit] =
    ZIO.serviceWithZIO(_.save(document))

  def get(id: String): ZIO[DocumentRepo, Throwable, Document] =
    ZIO.serviceWithZIO(_.get(id))
}

case class DocumentRepoLive(cache: Cache, blobStorage: BlobStorage) extends DocumentRepo {
  override def save(document: Document): Task[Unit] = ???

  override def get(id: String): Task[Document] = ???
}

object DocumentRepoLive {
  val layer: ZLayer[Cache & BlobStorage, Nothing, DocumentRepo] =
    ZLayer {
      for {
        cache       <- ZIO.service[Cache]
        blobStorage <- ZIO.service[BlobStorage]
      } yield DocumentRepoLive(cache, blobStorage)
    }
}

trait BlobStorage {
  def store(key: String, value: Array[Byte]): Task[Unit]
}

case class BlobStorageLive() extends BlobStorage {
  override def store(key: String, value: Array[Byte]): Task[Unit] = ???
}

object BlobStorageLive {
  val layer: URLayer[Any, BlobStorage] =
    ZLayer.succeed(BlobStorageLive())
}
```

And then assume we have the following ZIO application:

```scala mdoc:silent
import zio._

def myApp: ZIO[DocumentRepo & UserRepo, Throwable, Unit] =
  for {
    _ <- UserRepo.save(User("john@doe", "john"))
    _ <- DocumentRepo.save(Document("introduction to zio", "john", ""))
    _ <- UserRepo.get("john@doe").debug("retrieved john@doe user")
    _ <- DocumentRepo.get("introduction to zio").debug("retrieved article about zio")
  } yield ()
```

The `myApp` requires `DocumentRepo` and `UserRepo` services to run. So we need to create a `ZLayer` which requires no services and produces `DocumentRepo` and `UserRepo`. We can manually create this layer using [vertical and horizontal layer composition](#vertical-and-horizontal-composition): 

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Any, DocumentRepo with UserRepo] =
    (BlobStorageLive.layer ++ InmemoryCache.layer ++ DatabaseLive.layer) >>>
      (DocumentRepoLive.layer >+> UserRepoLive.layer)

  def run = myApp.provideLayer(layers)
}
```

### An Example of Automatically Generating a Dependency Graph

Instead of creating the required layer manually, we can use the `ZIO#provide`. ZIO internally creates the dependency graph automatically based on all dependencies provided:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def run =
    myApp.provide(
      InmemoryCache.layer,
      DatabaseLive.layer,
      UserRepoLive.layer,
      BlobStorageLive.layer,
      DocumentRepoLive.layer
    )
    
}
```

### An Example of Providing Different Implementations of the Same Service

Let's say we want to provide different versions of the same service to different services. In this example, both `UserRepo` and `DocumentRepo` services require the `Cache` service. However, we want to provide different cache implementations for these two services. Our goal is to provide an `InmemoryCache` layer for `UserRepo` and a `PersistentCache` layer for the `DocumentRepo` service:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Throwable, UserRepo with DocumentRepo] =
    ((InmemoryCache.layer ++ DatabaseLive.layer) >>> UserRepoLive.layer) ++
      ((PersistentCache.layer ++ BlobStorageLive.layer) >>> DocumentRepoLive.layer)

  def run = myApp.provideLayer(layers)
}

// Output:
// initialized: zio.examples.PersistentCache@6e899128
// initialized: zio.examples.InmemeoryCache@852e20a
```

### An Example of How to Get Fresh Layers

Having covered the topic of [acquiring fresh layers](#acquiring-a-fresh-version), let's see an example of using the `ZLayer#fresh` operator.

`DocumentRepo` and `UserRepo` services are dependent on an in-memory cache service. On the other hand, let's assume the cache service is quite simple, and we might be prone to cache conflicts between services. While sharing the cache service may cause some problems for our business logic, we should separate the cache service for both `DocumentRepo` and `UserRepo`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val layers: ZLayer[Any, Throwable, UserRepo & DocumentRepo] =
    ((InmemoryCache.layer.fresh ++ DatabaseLive.layer) >>> UserRepoLive.layer) ++
      ((InmemoryCache.layer.fresh ++ BlobStorageLive.layer) >>> DocumentRepoLive.layer)

  def run = myApp.provideLayer(layers)
}

// Output:
// initialized: zio.examples.InmemoryCache@13c9672b
// initialized: zio.examples.InmemoryCache@26d79027
```

### An Example of Pass-through Dependencies

Notice that in the previous examples, both `UserRepo` and `DocuemntRepo` have some [hidden dependencies](#hidden-versus-passed-through-dependencies), such as `Cache`, `Database`, and `BlobStorage`.  So these hidden dependencies are no longer expressed in the type signature of the `layers`. From the perspective of a caller, `layers` just outputs a `UserRepo` and `DocuemntRepo` and requires no inputs. The caller does not need to be concerned with the internal implementation details of how the `UserRepo` and `DocumentRepo` are constructed.

An upstream dependency that is used by many other services can be "passed-through" and included in a layer's output. This can be done with the `>+>` operator, which provides the output of one layer to another layer, returning a new layer that outputs the services of _both_.

The following example shows how to passthrough all dependencies to the final layer:

```scala mdoc:compile-only

import zio._

object MainApp extends ZIOAppDefault {

  // passthrough all dependencies
  val layers: ZLayer[Any, Throwable, Database & BlobStorage & Cache & DocumentRepo & UserRepo] =
    DatabaseLive.layer >+>
      BlobStorageLive.layer >+>
      InmemoryCache.layer >+>
      DocumentRepoLive.layer >+>
      UserRepoLive.layer

  // providing all passthrough dependencies to the ZIO application
  def run = myApp.provideLayer(layers)
}
```

### An Example of Updating Hidden Dependencies

One of the use cases of having explicit all dependencies in the final layer is that we can [update](#updating-local-dependencies) those hidden layers using `ZLayer#update`. In the following example, we are replacing the `InmemoryCache` with another implementation called `PersistentCache`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def myApp: ZIO[DocumentRepo & UserRepo, Nothing, Unit] =
    for {
      _ <- ZIO.service[UserRepo]
      _ <- ZIO.service[DocumentRepo]
    } yield ()

  val layers: ZLayer[Any, Throwable, Database & BlobStorage & Cache & DocumentRepo & UserRepo] =
    DatabaseLive.layer >+>
      BlobStorageLive.layer >+>
      InmemoryCache.layer >+>
      DocumentRepoLive.layer >+>
      UserRepoLive.layer

  def run =
    myApp.provideLayer(
      layers.update[Cache](_ => new PersistentCache)
    )
}
```

```scala mdoc:invisible:reset

```
