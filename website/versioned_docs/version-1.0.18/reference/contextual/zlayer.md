---
id: zlayer
title: "ZLayer"
---

A `ZLayer[-RIn, +E, +ROut]` describes a layer of an application: every layer in an application requires some services as input `RIn` and produces some services as the output `ROut`. 

ZLayers are:

1. **Recipes for Creating Services** — They describe how a given dependencies produces another services. For example, the `ZLayer[Logging with Database, Throwable, UserRepo]` is a recipe for building a service that requires `Logging` and `Database` service, and it produces a `UserRepo` service.

2. **An Alternative to Constructors** — We can think of `ZLayer` as a more powerful version of a constructor, it is an alternative way to represent a constructor. Like a constructor, it allows us to build the `ROut` service in terms of its dependencies (`RIn`).

3. **Composable** — Because of their excellent **composition properties**, layers are the idiomatic way in ZIO to create services that depend on other services. We can define layers that are relying on each other. 

4. **Effectful and Resourceful** — The construction of ZIO layers can be effectful and resourceful, they can be acquired and safely released when the services are done being utilized.

5. **Asynchronous** — Unlike class constructors which are blocking, ZLayer is fully asynchronous and non-blocking.

For example, a `ZLayer[Blocking with Logging, Throwable, Database]` can be thought of as a function that map `Blocking` and `Logging` services into `Database` service: 

```scala
(Blocking, Logging) => Database
```

So we can say that the `Database` service has two dependencies: `Blocking` and `Logging` services.

Let's see how we can create a layer:

## Creation

`ZLayer` is an **alternative to a class constructor**, a recipe to create a service. This recipe may contain the following information:

1. **Dependencies** — To create a service, we need to indicate what other service we are depending on. For example, a `Database` service might need `Socket` and `Blocking` services to perform its operations.

2. **Acquisition/Release Action** — It may contain how to initialize a service. For example, if we are creating a recipe for a `Database` service, we should provide how the `Database` will be initialized, via acquisition action. Also, it may contain how to release a service. For example, how the `Database` releases its connection pools.

In some cases, a `ZLayer` may not have any dependencies or requirements from the environment. In this case, we can specify `Any` for the `RIn` type parameter. The `Layer` type alias provided by ZIO is a convenient way to define a layer without requirements.

There are many ways to create a ZLayer. Here's an incomplete list:
 - `ZLayer.succeed` to create a layer from an existing service
 - `ZLayer.succeedMany` to create a layer from a value that's one or more services
 - `ZLayer.fromFunction` to create a layer from a function from the requirement to the service
 - `ZLayer.fromEffect` to lift a `ZIO` effect to a layer requiring the effect environment
 - `ZLayer.fromAcquireRelease` for a layer based on resource acquisition/release. The idea is the same as `ZManaged`.
 - `ZLayer.fromService` to build a layer from a service
 - `ZLayer.fromServices` to build a layer from a number of required services
 - `ZLayer.identity` to express the requirement for a layer
 - `ZIO#toLayer` or `ZManaged#toLayer` to construct a layer from an effect

Where it makes sense, these methods have also variants to build a service effectfully (suffixed by `M`), resourcefully (suffixed by `Managed`), or to create a combination of services (suffixed by `Many`).

Let's review some of the `ZLayer`'s most useful constructors:

### From Simple Values

With `ZLayer.succeed` we can construct a `ZLayer` from a value. It returns a `ULayer[Has[A]]` value, which represents a layer of application that _has_ a service of type `A`:

```scala
def succeed[A: Tag](a: A): ULayer[Has[A]]
```

In the following example, we are going to create a `nameLayer` that provides us the name of `Adam`.


```scala
val nameLayer: ULayer[Has[String]] = ZLayer.succeed("Adam")
```

In most cases, we use `ZLayer.succeed` to provide a layer of service of type `A`.

For example, assume we have written the following service:

```scala
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

```scala
import terminal._
val live: ZLayer[Any, Nothing, Terminal] = ZLayer.succeed(Terminal.Service.live)
```

### From Managed Resources

Some components of our applications need to be managed, meaning they undergo a resource acquisition phase before usage, and a resource release phase after usage (e.g. when the application shuts down). 

Fortunately, the construction of ZIO layers can be effectful and resourceful, this means they can be acquired and safely released when the services are done being utilized.

`ZLayer` relies on the powerful `ZManaged` data type and this makes this process extremely simple.

We can lift any `ZManaged` to `ZLayer` by providing a managed resource to the `ZLayer.fromManaged` constructor:


```scala
val managedFile = ZManaged.fromAutoCloseable(
  ZIO.effect(scala.io.Source.fromFile("file.txt"))
)
val fileLayer: ZLayer[Any, Throwable, Has[BufferedSource]] = 
  ZLayer.fromManaged(managedFile)
```

Also, every `ZManaged` can be converted to `ZLayer` by calling `ZLayer#toLayer`:

```scala
val fileLayer: ZLayer[Any, Throwable, Has[BufferedSource]] = managedFile.toLayer
```

Let's see another real-world example of creating a layer from managed resources. Assume we have written a managed `UserRepository`:


```scala
def userRepository: ZManaged[Blocking with Console, Throwable, UserRepository] = for {
  cfg <- dbConfig.toManaged_
  _ <- initializeDb(cfg).toManaged_
  xa <- makeTransactor(cfg)
} yield new UserRepository(xa)
```

We can convert that to `ZLayer` with `ZLayer.fromManaged` or `ZManaged#toLayer`:

```scala
val usersLayer  = userRepository.toLayer
// usersLayer: ZLayer[Blocking with Console, Throwable, Has[UserRepository]] = Managed(
//   self = zio.ZManaged$$anon$2@7b8f02a3
// )
val usersLayer_ = ZLayer.fromManaged(userRepository)
// usersLayer_: ZLayer[Blocking with Console, Throwable, Has[UserRepository]] = Managed(
//   self = zio.ZManaged$$anon$2@1ea831da
// )
```

Also, we can create a `ZLayer` directly from `acquire` and `release` actions of a managed resource:

```scala
def acquire = ZIO.effect(new FileInputStream("file.txt"))
def release(resource: Closeable) = ZIO.effectTotal(resource.close())

val inputStreamLayer = ZLayer.fromAcquireRelease(acquire)(release)
// inputStreamLayer: ZLayer[Any, Throwable, Has[FileInputStream]] = Managed(
//   self = zio.ZManaged$$anon$2@69e80340
// )
```

### From ZIO Effects

We can create `ZLayer` from any `ZIO` effect by using `ZLayer.fromEffect` constructor, or calling `ZIO#toLayer` method:

```scala
val layer = ZLayer.fromEffect(ZIO.succeed("Hello, World!"))
// layer: ZLayer[Any, Nothing, Has[String]] = Managed(
//   self = zio.ZManaged$$anon$2@684ca4c9
// )
val layer_ = ZIO.succeed("Hello, World!").toLayer
// layer_: ZLayer[Any, Nothing, Has[String]] = Managed(
//   self = zio.ZManaged$$anon$2@6bd53979
// )
```

Assume we have a `ZIO` effect that read the application config from a file, we can create a layer from that:


```scala
def loadConfig: Task[AppConfig] = Task.effect(???)
val configLayer = ZLayer.fromEffect(loadConfig)
// configLayer: ZLayer[Any, Throwable, Has[AppConfig]] = Managed(
//   self = zio.ZManaged$$anon$2@34ace338
// )
```

### From another Service

Every `ZLayer` describes an application that requires some services as input and produces some services as output. Sometimes when we are writing a new layer, we may need to access and depend on one or several services.

The `ZLayer.fromService` construct a layer that purely depends on the specified service:

```scala
def fromService[A: Tag, B: Tag](f: A => B): ZLayer[Has[A], Nothing, Has[B]]
```

Assume we want to write a `live` version of the following logging service:

```scala
object logging {
  type Logging = Has[Logging.Service]

  object Logging {
    trait Service {
      def log(msg: String): UIO[Unit]
    }
  }
}
```

We can create that by using `ZLayer.fromService` constructor, which depends on `Console` service:


```scala
val live: ZLayer[Console, Nothing, Logging] = ZLayer.fromService(console =>
  new Service {
    override def log(msg: String): UIO[Unit] = console.putStrLn(msg).orDie
  }
)
```

## Vertical and Horizontal Composition

We said that we can think of the `ZLayer` as a more powerful _constructor_. Constructors are not composable, because they are not values. While a constructor is not composable, `ZLayer` has a nice facility to compose with other `ZLayer`s. So we can say that a `Zlayer` turns a constructor into values.

`ZLayer`s can be composed together horizontally or vertically:

1. **Horizontal Composition** — They can be composed together horizontally with the `++` operator. When we compose two layers horizontally, the new layer that this layer requires all the services that both of them require, also this layer produces all services that both of them produces. Horizontal composition is a way of composing two layers side-by-side. It is useful when we combine two layers that they don't have any relationship with each other. 

2. **Vertical Composition** — If we have a layer that requires `A` and produces `B`, we can compose this layer with another layer that requires `B` and produces `C`; this composition produces a layer that requires `A` and produces `C`. The feed operator, `>>>`, stack them on top of each other by using vertical composition. This sort of composition is like _function composition_, feeding an output of one layer to an input of another.

Let's get into an example, assume we have these services with their implementations:


```scala
trait Logging { }
trait Database { }
trait BlobStorage { }
trait UserRepo { }
trait DocRepo { }

case class LoggerImpl(console: Console.Service) extends Logging { }
case class DatabaseImp(blocking: Blocking.Service) extends Database { }
case class UserRepoImpl(logging: Logging, database: Database) extends UserRepo { } 
case class BlobStorageImpl(logging: Logging) extends BlobStorage { }
case class DocRepoImpl(logging: Logging, database: Database, blobStorage: BlobStorage) extends DocRepo { }
```

We can't compose these services together, because their constructors are not value. `ZLayer` can convert these services into values, then we can compose them together.

Let's assume we have lifted these services into `ZLayer`s:

```scala
val logging: URLayer[Has[Console.Service], Has[Logging]] = 
  (LoggerImpl.apply _).toLayer
val database: URLayer[Has[Blocking.Service], Has[Database]] = 
  (DatabaseImp(_)).toLayer
val userRepo: URLayer[Has[Logging] with Has[Database], Has[UserRepo]] = 
  (UserRepoImpl(_, _)).toLayer
val blobStorage: URLayer[Has[Logging], Has[BlobStorage]] = 
  (BlobStorageImpl(_)).toLayer
val docRepo: URLayer[Has[Logging] with Has[Database] with Has[BlobStorage], Has[DocRepo]] = 
  (DocRepoImpl(_, _, _)).toLayer
```

Now, we can compose logging and database horizontally:

```scala
val newLayer: ZLayer[Has[Console.Service] with Has[Blocking.Service], Throwable, Has[Logging] with Has[Database]] = logging ++ database
```

And then we can compose the `newLayer` with `userRepo` vertically:

```scala
val myLayer: ZLayer[Has[Console.Service] with Has[Blocking.Service], Throwable, Has[UserRepo]] = newLayer >>> userRepo
```

## Layer Memoization

One important feature of `ZIO` layers is that **they are shared by default**, meaning that if the same layer is used twice, the layer will only be allocated a single time. 

For every layer in our dependency graph, there is only one instance of it that is shared between all the layers that depend on it. 

If we don't want to share a module, we should create a fresh, non-shared version of it through `ZLayer#fresh`.

## Updating Local Dependencies


Given a layer, it is possible to update one or more components it provides. We update a dependency in two ways:

1. **Using the `update` Method** — This method allows us to replace one requirement with a different implementation:

```scala
val withPostgresService = horizontal.update[UserRepo.Service]{ oldRepo  => new UserRepo.Service {
      override def getUser(userId: UserId): IO[DBError, Option[User]] = UIO(???)
      override def createUser(user: User): IO[DBError, Unit] = UIO(???)
    }
  }
```

2. **Using Horizontal Composition** — Another way to update a requirement is to horizontally compose in a layer that provides the updated service. The resulting composition will replace the old layer with the new one:

```scala
val dbLayer: Layer[Nothing, UserRepo] = ZLayer.succeed(new UserRepo.Service {
    override def getUser(userId: UserId): IO[DBError, Option[User]] = ???
    override def createUser(user: User): IO[DBError, Unit] = ???
  })

val updatedHorizontal2 = horizontal ++ dbLayer
```

## Hidden Versus Passed Through Dependencies

One design decision regarding building dependency graphs is whether to hide or pass through the upstream dependencies of a service. `ZLayer` defaults to hidden dependencies but makes it easy to pass through dependencies as well.

To illustrate this, consider the Postgres-based repository discussed above:

```scala
val connection: ZLayer[Any, Nothing, Has[Connection]] = connectionLayer
val userRepo: ZLayer[Has[Connection], Nothing, UserRepo] = postgresLayer
val layer: ZLayer[Any, Nothing, UserRepo] = connection >>> userRepo
```

Notice that in `layer`, the dependency `UserRepo` has on `Connection` has been "hidden", and is no longer expressed in the type signature. From the perspective of a caller, `layer` just outputs a `UserRepo` and requires no inputs. The caller does not need to be concerned with the internal implementation details of how the `UserRepo` is constructed.

To provide only some inputs, we need to explicitly define what inputs still need to be provided:

```scala
trait Configuration

val userRepoWithConfig: ZLayer[Has[Configuration] with Has[Connection], Nothing, UserRepo] = 
  ZLayer.succeed(new Configuration{}) ++ postgresLayer
val partialLayer: ZLayer[Has[Configuration], Nothing, UserRepo] = 
  (ZLayer.identity[Has[Configuration]] ++ connection) >>> userRepoWithConfig
``` 

In this example the requirement for a `Connection` has been satisfied, but `Configuration` is still required by `partialLayer`.

This achieves an encapsulation of services and can make it easier to refactor code. For example, say we want to refactor our application to use an in-memory database:

```scala
val updatedLayer: ZLayer[Any, Nothing, UserRepo] = dbLayer
```

No other code will need to be changed, because the previous implementation's dependency upon a `Connection` was hidden from users, and so they were not able to rely on it.

However, if an upstream dependency is used by many other services, it can be convenient to "pass through" that dependency, and include it in the output of a layer. This can be done with the `>+>` operator, which provides the output of one layer to another layer, returning a new layer that outputs the services of _both_ layers.


```scala
val layer: ZLayer[Any, Nothing, Has[Connection] with UserRepo] = connection >+> userRepo
```

Here, the `Connection` dependency has been passed through, and is available to all downstream services. This allows a style of composition where the `>+>` operator is used to build a progressively larger set of services, with each new service able to depend on all the services before it.


```scala
lazy val baker: ZLayer[Any, Nothing, Baker] = ???
lazy val ingredients: ZLayer[Any, Nothing, Ingredients] = ???
lazy val oven: ZLayer[Any, Nothing, Oven] = ???
lazy val dough: ZLayer[Baker with Ingredients, Nothing, Dough] = ???
lazy val cake: ZLayer[Baker with Oven with Dough, Nothing, Cake] = ???

lazy val all: ZLayer[Any, Nothing, Baker with Ingredients with Oven with Dough with Cake] =
  baker >+>       // Baker
  ingredients >+> // Baker with Ingredients
  oven >+>        // Baker with Ingredients with Oven
  dough >+>       // Baker with Ingredients with Oven with Dough
  cake            // Baker with Ingredients with Oven with Dough with Cake
```

`ZLayer` makes it easy to mix and match these styles. If you pass through dependencies and later want to hide them you can do so through a simple type ascription:

```scala
lazy val hidden: ZLayer[Any, Nothing, Cake] = all
```

And if you do build your dependency graph more explicitly, you can be confident that layers used in multiple parts of the dependency graph will only be created once due to memoization and sharing.

## Cyclic Dependencies

The `ZLayer` mechanism makes it impossible to build cyclic dependencies, making the initialization process very linear, by construction.

## Asynchronous Service Construction

Another important note about `ZLayer` is that, unlike constructors which are synchronous, `ZLayer` is _asynchronous_. Constructors in classes are always synchronous. This is a drawback for non-blocking applications. Because sometimes we might want to use something that is blocking the inside constructor.

For example, when we are constructing some sort of Kafka streaming service, we might want to connect to the Kafka cluster in the constructor of our service, which takes some time. So that wouldn't be a good idea to blocking inside a constructor. There are some workarounds for fixing this issue, but they are not perfect as the ZIO solution.

Well, with ZIO ZLayer, our constructor could be asynchronous, and they also can block definitely. And that is because `ZLayer` has the full power of ZIO. And as a result, we have strictly more power on our constructors with ZLayer. 

We can acquire resources asynchronously or in a blocking fashion, and spend some time doing that, and we don't need to worry about it. That is not an anti-pattern. This is the best practice with ZIO.

## Examples

### The simplest ZLayer application

This application demonstrates a ZIO program with a single dependency on a simple string value:

```scala
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

```scala
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
  val program: ZIO[Console with Clock with ModuleB, IOException, Unit] =
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

```scala
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
