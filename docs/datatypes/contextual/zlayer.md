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

1. **Recipes for Creating Services** — They describe how a given dependencies produces another services. For example, the `ZLayer[Logging & Database, Throwable, UserRepo]` is a recipe for building a service that requires `Logging` and `Database` service, and it produces a `UserRepo` service.

2. **An Alternative to Constructors** — We can think of `ZLayer` as a more powerful version of a constructor, it is an alternative way to represent a constructor. Like a constructor, it allows us to build the `ROut` service in terms of its dependencies (`RIn`).

3. **Composable** — Because of their excellent **composition properties**, layers are the idiomatic way in ZIO to create services that depend on other services. We can define layers that are relying on each other.

4. **Effectful and Resourceful** — The construction of ZIO layers can be effectful and resourceful. They can be acquired effectfully and safely released when the services are done being utilized. For example, to create a recipe for a `Database` service, we should describe how the `Database` will be initialized using an acquisition action. In addition, it may contain information about how the `Database` releases its connection pools.

5. **Asynchronous** — Unlike class constructors which are blocking, `ZLayer` is fully asynchronous and non-blocking. Note that constructors in classes are always synchronous. This is a drawback for non-blocking applications because sometimes we might want to use something that is blocking the inside constructor.

  For example, when we are constructing some sort of Kafka streaming service, we might want to connect to the Kafka cluster in the constructor of our service, which takes some time. So that wouldn't be a good idea to block inside a constructor. There are some workarounds for fixing this issue, but they are not perfect as the ZIO solution.

  With ZIO ZLayer, our constructor could be asynchronous, and they could also block. We can acquire resources asynchronously or in a blocking fashion, and spend some time doing that, and we don't need to worry about it. That is not an anti-pattern. This is the best practice with ZIO. And that is because `ZLayer` has the full power of the `ZIO` data type, and as a result, we have strictly more power on our constructors with `ZLayer`.

Let's see how we can create a layer:

## Creation

There are many ways to create a ZLayer. Here's an incomplete list:
- `ZLayer.succeed` to create a layer from an existing service
- `ZLayer.succeedMany` to create a layer from a value that's one or more services
- `ZLayer.fromFunction` to create a layer from a function from the requirement to the service
- `ZLayer.fromEffect` to lift a `ZIO` effect to a layer requiring the effect environment
- `ZLayer.fromAcquireRelease` for a layer based on resource acquisition/release. The idea is the same as `ZManaged`.
- `ZLayer.identity` to express the requirement for a dependency
- `ZIO#toLayer` or `ZManaged#toLayer` to construct a layer from an effect

Where it makes sense, these methods have also variants to build a service effectfully (suffixed by `ZIO`), resourcefully (suffixed by `Managed`), or to create a combination of services (suffixed by `Many`).

Let's review some of the `ZLayer`'s most useful constructors:

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

### From Managed Resources

Some components of our applications need to be managed, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). As we stated before, the construction of ZIO layers can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

1. The `ZLayer` relies on the powerful `ZManaged` data type and this makes this process extremely simple. We can lift any `ZManaged` to `ZLayer` by providing a managed resource to the `ZIO.apply` or the `ZIO.fromManaged` constructor:

```scala mdoc:silent:nest
import zio._
import scala.io.BufferedSource

val fileLayer: ZLayer[Any, Throwable, BufferedSource] =
  // alternative: ZLayer.fromManaged
  ZLayer {
    ZManaged.fromAutoCloseable(
      ZIO.attempt(scala.io.Source.fromFile("file.txt"))
    )
  }
```

2. Also, every `ZIO` effect can be converted to `ZManaged` using `ZIO#toManagedAuto` or `ZIO#toManagedWith` and then can be converted to `ZLayer` by calling the `ZLayer#toLayer`:

```scala mdoc:compile-only
val managedFile: ZLayer[Any, Throwable, BufferedSource] =
  ZIO.attempt(scala.io.Source.fromFile("file.txt"))
    .toManagedAuto   // alternative: toManagedWith(b => UIO(b.close())
    .toLayer
```

3. We can create a `ZLayer` directly from `acquire` and `release` actions of a managed resource:

```scala mdoc:compile-only
import zio._
import java.io.{Closeable, FileInputStream}

def acquire: Task[FileInputStream] = ZIO.attempt(new FileInputStream("file.txt"))
def release(resource: Closeable): UIO[Unit] = ZIO.succeed(resource.close())

val inputStreamLayer: ZLayer[Any, Throwable, FileInputStream] =
  ZLayer.fromAcquireRelease(acquire)(release)
```

Let's see a real-world example of creating a layer from managed resources. Assume we have the following `UserRepository` service:

```scala mdoc:silent
import zio._
import scala.io.Source._
import java.io.{FileInputStream, FileOutputStream, Closeable}

trait DBConfig
trait Transactor
trait User

def dbConfig: Task[DBConfig] = Task.attempt(???)
def initializeDb(config: DBConfig): Task[Unit] = Task.attempt(???)
def makeTransactor(config: DBConfig): ZManaged[Any, Throwable, Transactor] = ZManaged.attempt(???)

trait UserRepository {
  def save(user: User): Task[Unit]
}

case class UserRepositoryLive(xa: Transactor) extends UserRepository {
  override def save(user: User): Task[Unit] = Task(???)
}
```

Assume we have written a managed `UserRepository`:

```scala mdoc:silent:nest
def managed: ZManaged[Console, Throwable, UserRepository] = 
  for {
    cfg <- dbConfig.toManaged
    _   <- initializeDb(cfg).toManaged
    xa  <- makeTransactor(cfg)
  } yield new UserRepositoryLive(xa)
```

We can convert that to `ZLayer` with `ZLayer.fromManaged` or `ZManaged#toLayer`:

```scala mdoc:nest
val usersLayer : ZLayer[Console, Throwable, UserRepository] = managed.toLayer
```

```scala mdoc:invisible:reset

```

### From ZIO Effects

We can create `ZLayer` from any `ZIO` effect by using `ZLayer.fromEffect` constructor, or calling `ZIO#toLayer` method:

```scala mdoc:compile-only
import zio._

val layer1: ZLayer[Any, Nothing, String] = ZLayer.fromZIO(ZIO.succeed("Hello, World!"))
val layer2: ZLayer[Any, Nothing, String] = ZIO.succeed("Hello, World!").toLayer
```

For example, assume we have a `ZIO` effect that read the application config from a file, we can create a layer from that:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)
  
def loadConfig : Task[AppConfig]      = Task.attempt(???)
val configLayer: TaskLayer[AppConfig] = ZLayer.fromZIO(loadConfig)
```

### From Functions

A `ZLayer[R, E, A]` can be thought of as a function from `R` to `A`. So we can convert functions to the `ZLayer`.

Let's say we have defined the following `Logging` service:

```scala mdoc:silent
import zio._

trait Logging {
  def log(line: String): UIO[Unit]
}
```

Assume we have the following function which creates a live layer for `Logging` service:

```scala mdoc:silent
import zio._

def loggingLive(console: Console, clock: Clock): Logging =
  new Logging {
    override def log(line: String): UIO[Unit] =
      for {
        time <- clock.currentDateTime
        _    <- console.printLine(s"$time —- $line").orDie
      } yield ()
  }
```

We can convert the `loggingLive` function to the `ZLayer` using `toLayer` extension method on functions:

```scala mdoc:compile-only
val layer: ZLayer[Console & Clock, Nothing, Logging] = (loggingLive _).toLayer
```

This is the same method we use in Module Pattern:

```scala mdoc:silent
import zio._

case class LoggingLive(console: Console, clock: Clock) extends Logging {
  override def log(line: String): UIO[Unit] =
    for {
      time <- clock.currentDateTime
      _    <- console.printLine(s"$time —- $line").orDie
    } yield ()
}

object LoggingLive {
  val layer: ZLayer[Console & Clock, Nothing, Logging] = (LoggingLive.apply _).toLayer
}
```

Other than the `toLayer` extension method, we can create a layer using `ZLayer.fromFunction` directly:

```scala mdoc:silent
val layer: ZLayer[Console & Clock, Nothing, Logging] =
  ZLayer.fromFunction(x => LoggingLive(x.get[Console], x.get[Clock]))
```

```scala mdoc:invisible:reset

```

## Building Dependency Graph

The `ZLayer` offers various operators for composing layers together to build the dependency graph required by our application. In this section, we will learn more about these operators.

### Manual Layer Composition

We said that we can think of the `ZLayer` as a more powerful _constructor_. Constructors are not composable, because they are not values. While a constructor is not composable, `ZLayer` has a nice facility to compose with other `ZLayer`s. So we can say that a `ZLayer` turns a constructor into values.

> **Note**:
>
> In a regular ZIO application we are not required to build the dependency graph through composing layers tougher. Instead, we can provide all dependencies to the ZIO application using `ZIO#provide`, and the ZIO will create the dependency graph manually under the hood. Therefore, use manual layer composition if you know what you're doing.

#### Vertical and Horizontal Composition

Assume we have several services with their dependencies, and we need a way to compose and wiring up these dependencies and create the dependency graph of our application. `ZLayer` is a ZIO solution for this problem, it allows us to build up the whole application dependency graph by composing layers horizontally and vertically.

```scala mdoc:invisible
trait A
trait B
trait C
trait D
```

1. **Horizontal Composition** — Layers can be composed together horizontally with the `++` operator. When we compose layers horizontally, the new layer requires all the services that both of them require and produces all services that both of them produce. Horizontal composition is a way of composing two layers side-by-side. It is useful when we combine two layers that they don't have any relationship with each other.

We can compose `fooLayer` and `barLayer` _horizontally_ to build a layer that has the requirements of both, to provide the capabilities of both, through `fooLayer ++ barLayer`:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???        // A ==> B
val barLayer: ZLayer[C, Nothing  , D] = ???        // C ==> D

val horizontal: ZLayer[A & C, Throwable, B & D] =  // A & C ==> B & D
  fooLayer ++ barLayer
```

2. **Vertical Composition** — If we have a layer that requires `A` and produces `B`, we can compose this with another layer that requires `B` and produces `C`; this composition produces a layer that requires `A` and produces `C`. The feed operator, `>>>`, stack them on top of each other by using vertical composition. This sort of composition is like _function composition_, feeding an output of one layer to an input of another:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A, Throwable, B] = ???  // A ==> B
val barLayer: ZLayer[B, Nothing  , C] = ???  // B ==> C

val horizontal: ZLayer[A, Throwable, C] =    // A ==> C
  fooLayer >>> barLayer
```

When doing this, the first layer must output all the services required by the second layer, but we can _defer_ creating some of these services and require them as part of the input of the final layer using `ZLayer.service/ZLayer.environment`.

For example, assume we have the these two layers:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A    , Throwable, B] = ???  // A     ==> B
val barLayer: ZLayer[B & C, Throwable, D] = ???  // B & C ==> D

val layer: ZLayer[A & B & C, Throwable, D] =     // A & B & C ==> B & D
  fooLayer >>> barLayer
```

We can defer the creation of the `C` layer using `ZLayer.service[C]`:

```scala mdoc:compile-only
import zio._

val fooLayer: ZLayer[A    , Throwable, B] = ??? // A ==> B 
val barLayer: ZLayer[B & C, Throwable, D] = ??? // B & C ==> D

val layer: ZLayer[A & C, Throwable, D] =        // A & C ==> D
  (fooLayer ++ ZLayer.service[C]) >>> barLayer  // ((A ==> B) ++ (C ==> C)) >>> (B & C ==> D)
```

We can think of `ZIO.service[C]` is an _identity function_ (`C ==> C`).

```scala mdoc:invisible:reset

```

Using these simple operators we can build complex dependency graphs.

#### Updating Local Dependencies

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

  val myApp: ZIO[Console with AppConfig, IOException, Unit] =
    for {
      config <- ZIO.service[AppConfig]
      _ <- Console.printLine(s"Application config after the update operation: $config")
    } yield ()


  val appLayers: ZLayer[Any, Nothing, AppConfig with Console] =
    UIO(AppConfig(5))
      .debug("Application config initialized")
      .toLayer ++ Console.live

  val updatedConfig: ZLayer[Any, Nothing, AppConfig with Console] =
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

  val myApp: ZIO[Console with AppConfig, IOException, Unit] =
    for {
      config <- ZIO.service[AppConfig]
      _      <- Console.printLine(s"Application config after the update operation: $config")
    } yield ()


  val appLayers: ZLayer[Any, Nothing, AppConfig with Console] =
    UIO(AppConfig(5))
      .debug("Application config initialized")
      .toLayer ++ Console.live

  val updatedConfig: ZLayer[Any, Nothing, AppConfig with Console] =
    appLayers ++ ZLayer.succeed(AppConfig(8))

  def run = myApp.provide(updatedConfig)
}
// Output:
// Application config initialized: AppConfig(5)
// Application config after the update operation: AppConfig(8)
```

## Dependency Propagation

When we write an application, our application has a lot of dependencies. We need a way to provide implementations and feeding and propagating all dependencies throughout the whole application. We can solve the propagation problem by using _ZIO environment_.

During the development of an application, we don't care about implementations. Incrementally, when we use various effects with different requirements on their environment, all part of our application composed together, and at the end of the day we have a ZIO effect which requires some services as an environment. Before running this effect by `unsafeRun` we should provide an implementation of these services into the ZIO Environment of that effect.

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
    UIO.succeed(println(line))
}

val effect = app.provideEnvironment(ZEnvironment(loggingImpl))
```

Most of the time, we don't use `ZIO#provideEnvironment` directly to provide our services, instead; we use `ZLayer` to construct the dependency graph of our application, then we use methods like `ZIO#provide`, `ZIO#provideSome` and `ZIO#provideCustom` to propagate dependencies into the environment of our ZIO effect.

#### Using `ZIO#provide` Method

Unlike the `ZIO#provideEnvironment` which takes a `ZEnvironment[R]`, the `ZIO#provide` takes a `ZLayer` to the ZIO effect and translates it to another level.

Assume we have written this piece of program that requires Clock and Console services:

```scala mdoc:silent:nest
import zio.Clock._
import zio.Console._
import zio.Random._

val myApp: ZIO[Random & Console & Clock, Nothing, Unit] = for {
  random  <- nextInt 
  _       <- printLine(s"A random number: $random").orDie
  current <- currentDateTime
  _       <- printLine(s"Current Data Time: $current").orDie
} yield ()
```

We provide implementation of `Random`, `Console` and `Clock` services to the `myApp` effect by using `ZIO#provide` method:

```scala mdoc:silent:nest
val mainEffect: ZIO[Any, Nothing, Unit] = 
  myApp.provide(Random.live, Console.live, Clock.live)
```

As we see, the type of our effect converted from `ZIO[Random & Console & Clock, Nothing, Unit]` which requires two services to `ZIO[Any, Nothing, Unit]` effect which doesn't require any services.

#### Using `ZIO#provideSome` Method

Sometimes we have written a program, and we don't want to provide all its requirements. In these cases, we can use `ZIO#provideSome` to partially apply some layers to the `ZIO` effect.

In the previous example, if we just want to provide the `Console`, we should use `ZIO#provideSome`:

```scala
val mainEffectSome: ZIO[Random & Clock, Nothing, Unit] = 
  myApp.provideSome[Random & Clock](Console.live)
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

val myApp: ZIO[Logging & Console & Clock, Nothing, Unit] = for {
  _       <- Logging.log("Application Started!")
  current <- Clock.currentDateTime
  _       <- Console.printLine(s"Current Data Time: $current").orDie
} yield ()
```

This program uses two ZIO built-in services, `Console` and `Clock`. We don't need to provide `Console` and `Clock` manually, to reduce some boilerplate, we use `ZEnv` to satisfy some common base requirements.

By using `ZIO#provideCustom` we only provide the `Logging` layer, and it returns a `ZIO` effect which only requires `ZEnv`:

```scala mdoc:silent
val mainEffect: ZIO[ZEnv, Nothing, Unit] = myApp.provideCustom(LoggingLive.layer)
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

val a: ZLayer[Any, Nothing, A] = UIO(new A {}).debug("initialized").toLayer
val b: ZLayer[A,   Nothing, B] = (BLive.apply _).toLayer[B]
val c: ZLayer[A,   Nothing, C] = (CLive.apply _).toLayer[C]
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

We can memoize the `A` layer manually using the `ZLayer#memoize` operator. It will return a managed effect that, if evaluated, will return the lazily computed result of this layer:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  val myApp: ZIO[Any, Nothing, Unit] =
    a.memoize.use { aLayer =>
      for {
        _ <- ZIO.service[A].provide(aLayer)
        _ <- ZIO.service[A].provide(aLayer)
      } yield ()
    }
    
  def run = myApp
}
// The output:
// initialized: MainApp$$anon$1@2bfc2bcc
```

```scala mdoc:invisible:reset

```

## Examples

### A ZIO Application with a Simple Dependency

This application demonstrates a ZIO program with a single dependency on a simple `AppConfig`:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)

object MainApp extends ZIOAppDefault {

  // Define our simple ZIO program
  val zio: ZIO[AppConfig, Nothing, Unit] = 
    for {
      config <- ZIO.service[AppConfig]
      _      <- UIO(println(s"Applicaiton started with config: $config"))
    } yield ()

  // Create a ZLayer that produces an AppConfig and can be used to satisfy the AppConfig 
  // dependency that the program has
  val defaultConfig: ULayer[AppConfig] = ZLayer.succeed(AppConfig(10))

  // Run the program, providing the `defaultConfig`
  def run = zio.provide(defaultConfig)
}

```

### A ZIO Application with Multiple dependencies

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
  override def letsGoA(v: Int): UIO[String] = UIO(s"done: v = $v ")
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

  val program: ZIO[Console & Clock & B, IOException, Unit] =
    for {
      _ <- Console.printLine(s"Welcome to ZIO!")
      _ <- Clock.sleep(1.second)
      r <- B.letsGoB(10)
      _ <- Console.printLine(r)
    } yield ()

  def run = program.provideCustom(ALive.layer, BLive.layer)

}

// The output: 
// Welcome to ZIO!
// done: v = 10 
```

### A ZIO Application with Transitive Dependency

In this example, we can see that the `C` service depends upon `A`, `B`, and `Clock`:

```scala mdoc:compile-only
import zio._

trait A

object A {
  val live: ZLayer[Any, Nothing, A] =
    ZLayer.succeed(new A {})
}

trait B

object B {
  val live: ZLayer[Any, Nothing, B] =
    ZLayer.succeed(new B {})
}

trait C {
  def foo: UIO[Int]
}

case class CLive(a: A, b: B) extends C {
  val foo: UIO[Int] = {
    // In real application we use A and B services to implement the C service
    val _ = a
    val _ = b
    UIO.succeed(42)
  }
}

object C {
  val live: ZLayer[A & B & Clock, Nothing, C] =
    ZLayer {
      for {
        a <- ZIO.service[A]
        b <- ZIO.service[B]
      } yield CLive(a, b)
    }

  val foo: URIO[C, Int] = ZIO.serviceWithZIO(_.foo)
}

object MainApp extends ZIOAppDefault {
  val myApp = 
    for {
      r <- C.foo
      _ <- Console.printLine(r)
    } yield ()

  def run = myApp.provideCustom(A.live, B.live, C.live)
}

// The Output:
// 42
```

### A Practical Example of How to Get Fresh Layers

Having covered the topic of [acquiring fresh layers](#acquiring-a-fresh-version), let's see an example of using the `ZLayer#fresh` operator. 

In the following example, we have two services named `DocumentRepo` and `UserRepo`. Both live versions of these services are dependent on an in-memory cache service. While sharing the cache service may cause some problems four our business logic, we should separate cache service for both `DocumentRepo` and `UserRepo` services:

```scala mdoc:compile-only
import zio._

trait User

trait UserRepo {
  def save(user: User): Task[Unit]
}
case class UserRepoLive(cache: Cache, database: Database) extends UserRepo {
  override def save(user: User): Task[Unit] = ???
}
object UserRepoLive {
  val layer: URLayer[Cache & Database, UserRepo] = (UserRepoLive.apply _).toLayer
}

trait Database
case class DatabaseLive() extends Database
object DatabaseLive {
  val layer: ZLayer[Any, Nothing, Database] = (DatabaseLive.apply _).toLayer
}

trait Cache {
  def save(key: String, value: Array[Byte]): Task[Unit]
  def get(key: String): Task[Array[Byte]]
  def remove(key: String): Task[Unit]
}
object InmemoryCacheLive {
  val layer: ZLayer[Any, Throwable, Cache] = ZIO(new Cache {
    override def save(key: String, value: Array[Byte]): Task[Unit] = ???

    override def get(key: String): Task[Array[Byte]] = ???

    override def remove(key: String): Task[Unit] = ???
  }).debug("initialized").toLayer
}

trait Document
trait DocumentRepo {
  def save(document: Document): Task[Unit]
}
case class DocumentRepoLive(cache: Cache, blobStorage: BlobStorage) extends DocumentRepo {
  override def save(document: Document): Task[Unit] = ???
}
object DocumentRepoLive {
  val layer: ZLayer[Cache & BlobStorage, Nothing, DocumentRepoLive] = (DocumentRepoLive.apply _).toLayer
}

trait BlobStorage {
  def store(key: String, value: Array[Byte]): Task[Unit]
}
case class BlobStorageLive() extends BlobStorage {
  override def store(key: String, value: Array[Byte]): Task[Unit] = ???
}
object BlobStorageLive {
  val layer = (BlobStorageLive.apply _ ).toLayer
}

object MainApp extends ZIOAppDefault {

  def myApp: ZIO[DocumentRepo & UserRepo, Nothing, Unit] =
    for {
      _ <- ZIO.service[UserRepo]
      _ <- ZIO.service[DocumentRepo]
    } yield ()

  val layers: ZLayer[Any, Throwable, UserRepo & DocumentRepoLive] =
    ((InmemoryCacheLive.layer.fresh ++ DatabaseLive.layer) >>> UserRepoLive.layer) ++
      ((InmemoryCacheLive.layer.fresh ++ BlobStorageLive.layer) >>> DocumentRepoLive.layer)

  def run = myApp.provideLayer(layers)
}
```

```scala mdoc:invisible:reset

```

### Example of Building Dependency Graph

Let's get into an example, assume we have these services with their implementations:

```scala mdoc:invisible:reset
import zio._
```

```scala mdoc:silent:nest
trait Logging
trait Database
trait BlobStorage
trait UserRepo
trait DocRepo

case class LoggerImpl(console: Console) extends Logging
case object DatabaseImp extends Database
case class UserRepoImpl(logging: Logging, database: Database) extends UserRepo
case class BlobStorageImpl(logging: Logging) extends BlobStorage
case class DocRepoImpl(logging: Logging, database: Database, blobStorage: BlobStorage) extends DocRepo
```

We can't compose these services together, because their constructors are not value. `ZLayer` can convert these services into values, then we can compose them together.

Let's assume we have lifted these services into `ZLayer`s:

```scala mdoc:silent
val logging:     URLayer[Console, Logging]                          = (LoggerImpl.apply _).toLayer
val database:    URLayer[Any, Database]                             = ZLayer.succeed(DatabaseImp)
val userRepo:    URLayer[Logging & Database, UserRepo]              = (UserRepoImpl(_, _)).toLayer
val blobStorage: URLayer[Logging, BlobStorage]                      = (BlobStorageImpl(_)).toLayer
val docRepo:     URLayer[Logging & Database & BlobStorage, DocRepo] = (DocRepoImpl(_, _, _)).toLayer
```

Please note that the following implementations can be used if the above ones are cryptic:

```scala mdoc:compile-only
val logging: URLayer[Console, Logging] =
  ZLayer(ZIO.serviceWith[Console](LoggerImpl))

val database: URLayer[Any, Database] =
  ZLayer.succeed(DatabaseImp)

val userRepo: URLayer[Logging & Database, UserRepo] =
  ZLayer {
    for {
      database <- ZIO.service[Database]
      logging  <- ZIO.service[Logging]
    } yield UserRepoImpl(logging, database)
  }

val blobStorage: URLayer[Logging, BlobStorage] =
  ZLayer(ZIO.serviceWith[Logging](BlobStorageImpl))

val docRepo: URLayer[Logging & Database & BlobStorage, DocRepo] = {
  ZLayer {
    for {
      logging     <- ZIO.service[Logging]
      database    <- ZIO.service[Database]
      blobStorage <- ZIO.service[BlobStorage]
    } yield DocRepoImpl(logging, database, blobStorage)
  }
}
```

Now, we can compose logging and database horizontally:

```scala mdoc:silent
val newLayer: ZLayer[Console, Throwable, Logging & Database] = logging ++ database
```

And then we can compose the `newLayer` with `userRepo` vertically:

```scala mdoc:silent
val myLayer: ZLayer[Console, Throwable, UserRepo] = newLayer >>> userRepo
```

#### Updating Local Dependencies

```scala mdoc:invisible:reset
import zio._

trait DBError

case class User(id: UserId, name: String)
case class UserId(value: Long)

trait UserRepo {
  def getUser(userId: UserId): IO[DBError, Option[User]]
  def createUser(user: User):  IO[DBError, Unit]
}

object UserRepo {
  // Accessor Methods
  def getUser(userId: UserId): ZIO[UserRepo, DBError, Option[User]] =
    ZIO.serviceWithZIO(_.getUser(userId))

  def createUser(user: User): ZIO[UserRepo, DBError, Unit] =
    ZIO.serviceWithZIO(_.createUser(user))
}

case class InmemoryUserRepo() extends UserRepo {
  override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
  override def createUser(user: User):  IO[DBError, Unit]         = UIO(???)
}

object InmemoryUserRepo {
  // This simple in-memory version has no dependencies.
  // This could be useful for tests where we don't want the additional
  // complexity of having to manage DB Connections.
  val layer: ZLayer[Any, Nothing, UserRepo] =
    (InmemoryUserRepo.apply _).toLayer[UserRepo]
}

trait Logging {
  def info(s: String): UIO[Unit]
  def error(s: String): UIO[Unit]
}

object Logging {
  //accessor methods
  def info(s: String): URIO[Logging, Unit] =
    ZIO.serviceWithZIO(_.info(s))

  def error(s: String): URIO[Logging, Unit] =
    ZIO.serviceWithZIO(_.error(s))
}

case class ConsoleLogger(console: Console) extends Logging {
  def info(s: String): UIO[Unit]  = console.printLine(s"info - $s").orDie
  def error(s: String): UIO[Unit] = console.printLine(s"error - $s").orDie
}

object ConsoleLogger {
  val layer: ZLayer[Console, Nothing, Logging] = 
    ZLayer {
      for {
        console <- ZIO.service[Console] 
      } yield ConsoleLogger(console) 
    }
}

trait Connection {
  def close(): Unit
}

object Connection {
  def makeConnection: UIO[Connection] = UIO(???)
  
  val layer: Layer[Nothing, Connection] =
    ZLayer.fromAcquireRelease(makeConnection)(c => UIO(c.close()))
}

case class PostgresUserRepo(connection: Connection) extends UserRepo {
  override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
  override def createUser(user: User):  IO[DBError, Unit]         = UIO(???)
}

object PostgresUserRepo {
  val layer: ZLayer[Connection, Nothing, UserRepo] =
    ZLayer {
      for {
        connection <- ZIO.service[Connection] 
      } yield PostgresUserRepo(connection) 
    }
}

val fullRepo: Layer[Nothing, UserRepo] = Connection.layer >>> PostgresUserRepo.layer 

val user2: User = User(UserId(123), "Tommy")

val makeUser: ZIO[Logging & UserRepo, DBError, Unit] = 
  for {
    _ <- Logging.info(s"inserting user")  // URIO[Logging, Unit]
    _ <- UserRepo.createUser(user2)       // ZIO[UserRepo, DBError, Unit]
    _ <- Logging.info(s"user inserted")   // URIO[Logging, Unit]
  } yield ()


// compose horizontally
val horizontal: ZLayer[Console, Nothing, Logging & UserRepo] = ConsoleLogger.layer ++ InmemoryUserRepo.layer

// fulfill missing services, composing vertically
val fullLayer: Layer[Nothing, Logging & UserRepo] = Console.live >>> horizontal

// provide the services to the program
makeUser.provide(fullLayer)
```

Given a layer, it is possible to update one or more components it provides. We update a dependency in two ways:

1. **Using the `update` Method** — This method allows us to replace one requirement with a different implementation:

```scala mdoc:silent:nest
val withPostgresService = horizontal.update[UserRepo]{ oldRepo  => 
    new UserRepo {
      override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
      override def createUser(user: User):  IO[DBError, Unit]         = UIO(???)
    }
  }
```

2. **Using Horizontal Composition** — Another way to update a requirement is to horizontally compose in a layer that provides the updated service. The resulting composition will replace the old layer with the new one:

```scala mdoc:silent:nest
val dbLayer: Layer[Nothing, UserRepo] = ZLayer.succeed(new UserRepo {
    override def getUser(userId: UserId): IO[DBError, Option[User]] = ???
    override def createUser(user: User):  IO[DBError, Unit]         = ???
  })

val updatedHorizontal2 = horizontal ++ dbLayer
```

#### Hidden Versus Passed Through Dependencies

One design decision regarding building dependency graphs is whether to hide or pass through the upstream dependencies of a service. `ZLayer` defaults to hidden dependencies but makes it easy to pass through dependencies as well.

To illustrate this, consider the Postgres-based repository discussed above:

```scala mdoc:silent:nest
val connection: ZLayer[Any, Nothing, Connection]      = Connection.layer
val userRepo:   ZLayer[Connection, Nothing, UserRepo] = PostgresUserRepo.layer
val layer:      ZLayer[Any, Nothing, UserRepo]        = Connection.layer >>> userRepo
```

Notice that in `layer`, the dependency `UserRepo` has on `Connection` has been "hidden", and is no longer expressed in the type signature. From the perspective of a caller, `layer` just outputs a `UserRepo` and requires no inputs. The caller does not need to be concerned with the internal implementation details of how the `UserRepo` is constructed.

To provide only some inputs, we need to explicitly define what inputs still need to be provided:

```scala mdoc:silent:nest
trait Configuration

val userRepoWithConfig: ZLayer[Configuration & Connection, Nothing, UserRepo] = 
  ZLayer.succeed(new Configuration{}) ++ PostgresUserRepo.layer
  
val partialLayer: ZLayer[Configuration, Nothing, UserRepo] = 
  (ZLayer.environment[Configuration] ++ connection) >>> userRepoWithConfig
``` 

In this example the requirement for a `Connection` has been satisfied, but `Configuration` is still required by `partialLayer`.

This achieves an encapsulation of services and can make it easier to refactor code. For example, say we want to refactor our application to use an in-memory database:

```scala mdoc:silent:nest
val updatedLayer: ZLayer[Any, Nothing, UserRepo] = dbLayer
```

No other code will need to be changed, because the previous implementation's dependency upon a `Connection` was hidden from users, and so they were not able to rely on it.

However, if an upstream dependency is used by many other services, it can be convenient to "pass through" that dependency, and include it in the output of a layer. This can be done with the `>+>` operator, which provides the output of one layer to another layer, returning a new layer that outputs the services of _both_.


```scala mdoc:silent:nest
val layer: ZLayer[Any, Nothing, Connection & UserRepo] = connection >+> userRepo
```

Here, the `Connection` dependency has been passed through, and is available to all downstream services. This allows a style of composition where the `>+>` operator is used to build a progressively larger set of services, with each new service able to depend on all the services before it.

```scala mdoc
trait Baker 
trait Ingredients
trait Oven
trait Dough
trait Cake

lazy val baker: ZLayer[Any, Nothing, Baker] = ???
lazy val ingredients: ZLayer[Any, Nothing, Ingredients] = ???
lazy val oven: ZLayer[Any, Nothing, Oven] = ???
lazy val dough: ZLayer[Baker & Ingredients, Nothing, Dough] = ???
lazy val cake: ZLayer[Baker & Oven & Dough, Nothing, Cake] = ???

lazy val all: ZLayer[Any, Nothing, Baker & Ingredients & Oven & Dough & Cake] =
  baker >+>       // Baker
  ingredients >+> // Baker & Ingredients
  oven >+>        // Baker & Ingredients & Oven
  dough >+>       // Baker & Ingredients & Oven & Dough
  cake            // Baker & Ingredients & Oven & Dough & Cake
```

`ZLayer` makes it easy to mix and match these styles. If you pass through dependencies and later want to hide them you can do so through a simple type ascription:

```scala mdoc:silent
lazy val hidden: ZLayer[Any, Nothing, Cake] = all
```

And if you do build your dependency graph more explicitly, you can be confident that dependencies used in multiple parts of the dependency graph will only be created once due to memoization and sharing.

#### Cyclic Dependencies

The `ZLayer` mechanism makes it impossible to build cyclic dependencies, making the initialization process very linear, by construction.

