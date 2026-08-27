---
id: "zlayer"
title: "ZLayer"
description: "ZLayer provides composable recipes for constructing services with type-safe dependency management and asynchronous resource handling."
keywords:
  - "Layer Composition"
  - "Service Construction"
  - "Dependency Management"
  - "Resource Management"
  - "Asynchronous Construction"
  - "ZLayer Combinators"
---

A `ZLayer[-RIn, +E, +ROut]` describes a layer of an application: every layer in an application requires some services as input `RIn` and produces some services as the output `ROut`.

We can think of a layer as mental model of an asynchronous function from `RIn` to the `Either[E, ROut]`:

```scala
type ZLayer[-RIn, +E, +ROut] = RIn => async Either[E, ROut]
```

For example, a `ZLayer[Socket & Persistence, Throwable, Database]` can be thought of as a function that map `Socket` and `Persistence` services into `Database` service:

```scala
(Socket, Persistence) => Database
```

So we can say that the `Database` service has two dependencies: `Socket` and `Persistence` services.

In some cases, a `ZLayer` may not have any dependencies or requirements from the environment. In this case, we can specify `Any` for the `RIn` type parameter. The [`Layer`](layer.md) type alias provided by ZIO is a convenient way to define a layer without requirements.

ZLayers are:

1. **Recipes for Creating Services** — They describe how to create services from given dependencies. For example, the `ZLayer[Socket & Database, Throwable, UserRepo]` is a recipe for building a service that requires `Socket` and `Database` service, and it produces a `UserRepo` service.

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

trait EmailService {
  def send(email: String, content: String): UIO[Unit]
}

object EmailService {
  val layer: ZLayer[Any, Nothing, EmailService] = 
    ZLayer.succeed( 
      new EmailService {
        override def send(email: String, content: String): UIO[Unit] = ???
      }
    )
}
```

### From Non-resourceful Effects

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

### Avoiding Accidental Scope Leaks

`ZLayer.apply`/`ZLayer.fromZIO` accept any `ZIO[R, E, A]`, including an effect whose `R` contains `Scope`. This means a resourceful effect can end up in them by mistake, and instead of the resource being released when the layer finishes constructing, the `Scope` requirement leaks into the layer's own `RIn`.

#### Passing a Resourceful Effect to `ZLayer.fromZIO`

For example, suppose we build a `Connection` using `ZIO.acquireRelease`, which produces a `ZIO[Scope, E, A]`, and pass it directly to `ZLayer.fromZIO`:

```scala mdoc:compile-only
import zio._

trait Connection {
  def close: UIO[Unit]
}

object Connection {
  def open: ZIO[Any, Throwable, Connection] = ???
}

val acquireConnection: ZIO[Scope, Throwable, Connection] =
  ZIO.acquireRelease(Connection.open)(_.close)

val leaky: ZLayer[Scope, Throwable, Connection] =
  ZLayer.fromZIO(acquireConnection)
```

This compiles, and `leaky` ends up with type `ZLayer[Scope, Throwable, Connection]`: the `Scope` was never closed by the layer itself, it was pushed into `RIn` instead. Providing this layer now requires supplying a `Scope` from somewhere, and the connection stays open for as long as that `Scope` stays open, not just for as long as the layer's own construction takes:

```scala mdoc:compile-only
import zio._

trait Connection {
  def close: UIO[Unit]
}

object Connection {
  def open: ZIO[Any, Throwable, Connection] = ???
}

val acquireConnection: ZIO[Scope, Throwable, Connection] =
  ZIO.acquireRelease(Connection.open)(_.close)

val leaky: ZLayer[Scope, Throwable, Connection] =
  ZLayer.fromZIO(acquireConnection)

val program: ZIO[Connection, Throwable, Unit] = ZIO.unit

val runnable: ZIO[Any, Throwable, Unit] =
  program.provide(leaky, Scope.default)
```

Here `Scope.default` lives for the entire remaining execution of `runnable`, so the connection is held open for the whole program instead of being closed right after `program` is done with it. The fix is to build the layer with `ZLayer.scoped` instead of `ZLayer.fromZIO`, which consumes the `Scope` internally and ties the release to the layer's own lifetime:

```scala mdoc:compile-only
import zio._

trait Connection {
  def close: UIO[Unit]
}

object Connection {
  def open: ZIO[Any, Throwable, Connection] = ???
}

val connectionLayer: ZLayer[Any, Throwable, Connection] =
  ZLayer.scoped {
    ZIO.acquireRelease(Connection.open)(_.close)
  }

val program: ZIO[Connection, Throwable, Unit] = ZIO.unit

val runnable: ZIO[Any, Throwable, Unit] =
  program.provide(connectionLayer)
```

`connectionLayer` no longer requires a `Scope` in `RIn`, and `runnable` closes the connection as soon as `program` finishes with it.

:::note[Current Behavior]
`ZLayer.fromZIO` and `ZLayer.apply` do not currently require evidence that `R` excludes `Scope`, so the compiler does not flag the pattern above. Prefer `ZLayer.scoped` whenever the effect you give to a layer constructor requires a `Scope`.
:::

#### Extending a Layer's Scope on Purpose

Sometimes a layer's resource is meant to outlive the scope in which it was built, for example when it should be shared for the entire remaining lifetime of an application rather than closed as soon as it is constructed. `ZLayer#extendScope` exists for this: it takes a layer built with `ZLayer.scoped` and returns a new layer that ties the resource's release to an outer `Scope` supplied by the caller, instead of releasing it when construction completes:

```scala mdoc:compile-only
import zio._

trait Connection {
  def close: UIO[Unit]
}

object Connection {
  def open: ZIO[Any, Throwable, Connection] = ???
}

val connectionLayer: ZLayer[Any, Throwable, Connection] =
  ZLayer.scoped {
    ZIO.acquireRelease(Connection.open)(_.close)
  }

val extendedLayer: ZLayer[Scope, Throwable, Connection] =
  connectionLayer.extendScope
```

`extendedLayer` has the same `Scope` requirement in `RIn` as the leaky layer earlier in this section, but here it is intentional: calling `ZLayer#extendScope` documents in the code itself that the resource's lifetime is meant to extend beyond this layer's own construction, rather than being an accidental side effect of using `ZLayer.fromZIO` on a resourceful effect.

`Spec#provideLayerShared` in ZIO Test builds on exactly this pattern to share one expensive layer across every test in a suite: it acquires the layer once using `ZLayer#extendScope`, ties the layer's release to the scope of the whole suite, and reuses the already-built environment for each test underneath instead of rebuilding the layer per test. The same technique applies outside of testing whenever several independent operations should share one resource. In the following example, two queries reuse the same `Database` connection, and the connection closes only once, after both queries finish, because `ZIO.scoped` supplies the outer `Scope` that `ZLayer#extendScope` extends into:

```scala mdoc:compile-only
import zio._

trait Database {
  def query(sql: String): Task[Int]
}

object Database {
  def connect: ZIO[Scope, Throwable, Database] = ???
}

val sharedDatabase: ZLayer[Any, Throwable, Database] =
  ZLayer.scoped(Database.connect)

val program: ZIO[Any, Throwable, (Int, Int)] =
  ZIO.scoped {
    sharedDatabase.extendScope.build.flatMap { env =>
      val db = env.get[Database]
      db.query("SELECT 1").zip(db.query("SELECT 2"))
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

case class DatabaseConfig()

object DatabaseConfig {
  val live = ZLayer.succeed(DatabaseConfig())
}

case class Database(databaseConfig: DatabaseConfig)

object Database {
  val live: ZLayer[DatabaseConfig, Nothing, Database] =
    ZLayer.fromFunction(Database.apply _)
}

case class Analytics()

object Analytics {
  val live: ULayer[Analytics] = ZLayer.succeed(Analytics())
}

case class Users(database: Database, analytics: Analytics)

object Users {
  val live = ZLayer.fromFunction(Users.apply _)
}

case class App(users: Users, analytics: Analytics) {
  def execute: UIO[Unit] =
    ZIO.debug(s"This app is made from ${users} and ${analytics}")
}

object App {
  val live = ZLayer.fromFunction(App.apply _)
}

object MainApp extends ZIOAppDefault {

  def run =
    ZIO
      .serviceWithZIO[App](_.execute)
      .provide(
        (((DatabaseConfig.live >>> Database.live) ++ Analytics.live >>> Users.live) ++ Analytics.live) >>> App.live
      )
}
```

```scala mdoc:invisible:reset

```

### Automatic Derivation

Simple layers can be derived using `ZLayer.derive`. See [Automatic ZLayer Derivation](./automatic-zlayer-derivation.md).


## Converting a Layer to a Scoped Value

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

## Falling Back to an Alternate Layer

If a layer fails, we can provide an alternative layer by using `ZLayer#orElse` so it will fall back to the second layer:

```scala mdoc:compile-only
import zio._

trait Database

val postgresDatabaseLayer: ZLayer[Any, Throwable, Database] = ???
val inmemoryDatabaseLayer: ZLayer[Any, Throwable, Database] = ???

val databaseLayer: ZLayer[Any, Throwable, Database] =
  postgresDatabaseLayer.orElse(inmemoryDatabaseLayer)
```

## Converting a Layer to a ZIO Application

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

## Retrying

We can retry constructing a layer in case of failure:

```scala mdoc:invisible
trait DatabaseConnection
```

```scala mdoc:compile-only
import zio._

val databaseLayer: ZLayer[Any, Throwable, DatabaseConnection]   = ???

val retriedLayer : ZLayer[Clock, Throwable, DatabaseConnection] = databaseLayer.retry(Schedule.fibonacci(1.second))
```

## Layer Projection

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

## Tapping

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
