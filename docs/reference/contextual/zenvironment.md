---
id: zenvironment 
title: "ZEnvironment"
---

A `ZEnvironment[R]` is a built-in type-level map for the `ZIO` data type which is responsible for maintaining the environment of a `ZIO` effect. The `ZIO` data type uses this map to maintain all the environmental services and their implementations.

For example, assume we have written a `ZEnvironment` containing all built-in services as below:

```scala mdoc:silent
import zio._

val environment: ZEnvironment[Console & Clock & Random & System] =
  ZEnvironment[Console, Clock, Random, System](
    Console.ConsoleLive,
    Clock.ClockLive,
    Random.RandomLive,
    System.SystemLive
  )
```

This map contains all built-in services and their corresponding implementations. If we evaluate the `ZEnvironment#toString` method, we can see the underlying type-level map something like this.

```scala
ZEnvironment(
  Map(
    Console -> (zio.Console$ConsoleLive$@76a3e297, 0),
    Clock   -> (zio.Clock$ClockLive$@4d3167f4, 1), 
    Random  -> (RandomScala(scala.util.Random$@4eb7f003), 2), 
    System  -> (zio.System$SystemLive$@eafc191, 3)
  )
)
```

From a ZIO environment point of view, we can think of `ZIO` as the following function:

```scala
type ZIO[R, E, A] = ZEnvironment[R] => Either[E, A]
or 
type ZIO[R, E, A] = ZEnvironment[R] => IO[E, A]
```

For example, the `ZIO[Foo & Bar, Throwable, String]` can be thought of as a function from `ZEnvironment[Foo & Bar]` to `Either[Throwable, String]`:

> **Note**:
>
> The `ZEnvironment` is useful for manually constructing and combining the ZIO environment. So, in most cases, we do not require working directly with this data type. So you can skip reading this page if you are not an advanced user.

We can eliminate the environment of `ZIO[R, E, A]` by providing `ZEnvironment[R]` to that effect. 

Also, we can access the **whole** environment using `ZIO.environment`:

```scala mdoc:compile-only
import zio._ 
import java.io.IOException

case class AppConfig(poolSize: Int)

val myApp: ZIO[AppConfig, IOException, Unit] =
  ZIO.environment[AppConfig].flatMap { env =>
    val config  = env.get[AppConfig]
    Console.printLine(s"Application started with config: $config")
  }

val eliminated: IO[IOException, Unit] =
  myApp.provideEnvironment(
    ZEnvironment(AppConfig(poolSize = 10))
  )
```

> **Note**: 
>
> In most cases, we do not require using `ZIO.environment` to access the whole environment or the `ZIO#provideEnvironment` to provide effect dependencies. Therefore, most of the time, we use `ZIO.service*` and other `ZIO#provide*` methods to access a specific service from the environment or provide services to a ZIO effect.

## Creation

To create an empty ZIO environment:

```scala mdoc:compile-only
import zio._

val empty: ZEnvironment[Any] = ZEnvironment.empty
```

To create a ZIO environment from a simple value:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)
val config: ZEnvironment[AppConfig] = ZEnvironment(AppConfig("localhost", 8080))
```

## Operations

To **combine** two or multiple environment we can use `union` or `++` operator:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)

val app: ZEnvironment[AppConfig] =
  ZEnvironment.empty ++ ZEnvironment(AppConfig("localhost", 8080))
```

To **add** a service to an environment:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)

val app: ZEnvironment[AppConfig] =
  ZEnvironment.empty.add(AppConfig("localhost", 8080))
```

To retrieve a service from the environment, we use `get` method:

```scala mdoc:compile-only
import zio._

case class AppConfig(host: String, port: Int)

val app: ZEnvironment[AppConfig] =
  ZEnvironment.empty.add(AppConfig("localhost", 8080))

val appConfig: AppConfig = app.get[AppConfig] 
```

## Providing Multiple Instance of the Same Interface

We can express an effect's dependency on multiple services of the type `A` which are keyed by type `K` with `Map[K, A]`. For example, the `ZIO[Map[String, Database], Throwable, Unit]` is an effect that depends on multiple `Database` versions.

To access the specified service corresponding to a specific key, we can use the `ZIO.serviceAt[Service](key)` constructor. For example, to access a `Database` service which is specified by the "inmemory" key, we can write:

```scala mdoc:invisible
import zio._
trait Database
```

```scala mdoc:silent:nest
val database: URIO[Map[String, Database], Option[Database]] =
  ZIO.serviceAt[Database]("inmemory")
```

A service can be updated at the specified key using the `ZIO#updateServiceAt` operator.


### Multiple Config Example

Let's see how we can create a layer comprising multiple instances of `AppConfig`:

```scala mdoc:silent
import zio._

case class AppConfig(host: String, port: Int)

object AppConfig {
  val layer: ULayer[Map[String, AppConfig]] =
    ZLayer.succeedEnvironment(
      ZEnvironment(
        Map(
          "prod" -> AppConfig("production.myapp", 80),
          "dev" -> AppConfig("development.myapp", 8080)
        )
      )
    )
}
```

And here is the application which uses different `AppConfig` from the ZIO environment based on the value of the `APP_ENV` environment variable:

```scala mdoc:compile-only
import zio._

object MultipleConfigExample extends ZIOAppDefault {

  val myApp: ZIO[Map[String, AppConfig], String, Unit] = for {
    env <- System.env("APP_ENV")
      .flatMap(x => ZIO.fromOption(x))
      .orElseFail("The environment variable APP_ENV cannot be found.")
    config <- ZIO.serviceAt[AppConfig](env)
      .flatMap(x => ZIO.fromOption(x))
      .orElseFail(s"The $env config cannot be found in the ZIO environment")
    _ <- ZIO.logInfo(s"Application started with: $config")
  } yield ()

  def run =
    myApp.provide(AppConfig.layer)

}
```

```scala mdoc:invisible:reset

```

### Multiple Database Example

Here is an example of providing multiple instances of the `Database` service to the ZIO environment:

```scala mdoc:compile-only
import zio._

import java.nio.charset.StandardCharsets

trait Database {
  def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit]
}

object Database {
  val layer: ULayer[Map[String, Database]] = {
    ZLayer.succeedEnvironment(
      ZEnvironment(
        Map(
          "persistent" -> PersistentDatabase.apply(),
          "inmemory" -> InmemoryDatabase.apply()
        )
      )
    )
  }
}

case class InmemoryDatabase() extends Database {
  override def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit] =
    ZIO.unit <* ZIO.logInfo(s"new $key added to the inmemory database")
}

case class PersistentDatabase() extends Database {
  override def add(key: String, value: Array[Byte]): ZIO[Any, Throwable, Unit] =
    ZIO.unit <* ZIO.logInfo(s"new $key added to the persistent database")
}

object MultipleDatabaseExample extends ZIOAppDefault {
  val myApp = for {
    inmemory <- ZIO.serviceAt[Database]("inmemory")
      .flatMap(x => ZIO.fromOption[Database](x))
      .orElseFail("failed to find an in-memory database in the ZIO environment")
    persistent <- ZIO.serviceAt[Database]("persistent")
      .flatMap(x => ZIO.fromOption[Database](x))
      .orElseFail("failed to find an persistent database in the ZIO environment")
    _ <- inmemory.add("key1", "value1".getBytes(StandardCharsets.UTF_8))
    _ <- persistent.add("key2", "value2".getBytes(StandardCharsets.UTF_8))
  } yield ()

  def run = myApp.provideLayer(Database.layer)
}
```
