---
id: dependency-injection-in-zio
title: "Dependency Injection in ZIO"
---

## Getting Started With A Simple Example

Here is the minimum effort to get dependency injection working in ZIO:

:::caution
The following example is the simplest possible example of how dependency injection works in ZIO. So in this example, we are not going to use [Service Pattern](../service-pattern/service-pattern.md).
:::

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[Int, Nothing, Long] = // myApp requires a service of type Int
    for {
      a <- ZIO.service[Int] // Accessing a service of type Int
      _ <- ZIO.debug(s"received a value object of Int service from the environment: $a")
    } yield a.toLong * a.toLong

  def run =
    myApp
      .debug("result") // printing the result of the myApp
      .provide(         // providing (injecting) all required services that myApp needs
        ZLayer.succeed( // A simple layer that provides implementation of type Int
          5             // Implementation of Int service
        )              
      )
}
```

Here are the steps:
1. We started by writing our application logic. Whenever we wanted to use a service of type `Int` we accessed it from the environment using the `ZIO.service` method. So, we can continue to write our application logic without worrying about what implementation of the service we are using.
2. We created an implementation of Int service, the concrete `5` value.
3. We created a layer for the concrete implementation of `Int` service, `ZLayer.succeed(5)`.
4. Finally, we provided (injected) the layer to our application, `myApp.provide(ZLayer.succeed(5))`. This propagates the layer from bottom to top and provides the concrete implementation of `Int` service to each effect that needs it.

## Using Multiple Services

Similarly, if we wanted to use multiple services, we can obtain them from the environment using the `ZIO.service` method. Doing so will change the final type of our ZIO application. So, at the end of the day, we know what services we are using and what services we need to provide.

For example, In the following example, we are going to use two services of type `Int` and `String`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  // myApp requires two services: Int and String
  val myApp: ZIO[String with Int, Nothing, Unit] =
    for {
      a <- ZIO.service[Int] // Accessing a service of type Int
      _ <- ZIO.debug(s"received an instance of Int service from the environment: $a")
      b <- ZIO.service[String] // Accessing a service of type String
      _ <- ZIO.debug(s"received an instance of String service from the environment: $b")
    } yield ()

  def run =
    myApp
      .provide(         // providing (injecting) all required services that myApp needs
        ZLayer.succeed( // A simple layer that provides implementation of type Int
          5             // Implementation of Int service
        ),
        ZLayer.succeed( // A simple layer that provides implementation of type String
          "Hello"       // Implementation of String service
        )
      )
}
```

Note that the `ZIO#provide` method takes a list of required services as an argument and [automatically builds the dependency graph](automatic-layer-construction.md) using metaprogramming.

Alternatively, we can [manually build the dependency graph](manual-layer-construction.md) and finally pass it to the `ZIO#provideLayer` method, which doesn't perform any metaprogramming under the hood:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  // myApp requires two services: Int and String
  val myApp: ZIO[String with Int, Nothing, Unit] =
    for {
      a <- ZIO.service[Int] // Accessing a service of type Int
      _ <- ZIO.debug(s"received an instance of Int service from the environment: $a")
      b <- ZIO.service[String] // Accessing a service of type String
      _ <- ZIO.debug(s"received an instance of String service from the environment: $b")
    } yield ()

  def run =
    myApp
      .provide(        
        // Build the dependency graph manually using horizontal composition (++)
        ZLayer.succeed(5) ++ ZLayer.succeed("Hello")
      )
}
```

## Providing Different Implementations of a Service

One of the benefits of using dependency injection is that, we can write our application in a way that without modifying the application logic, we can provide different implementations of services to our application.

### Example 1: Config Service

In the next example, we have a ZIO application that uses the `AppConfig` service:

```scala mdoc:compile-only
import zio._

case class AppConfig(poolSize: Int)

object AppConfig {
  def poolSize: ZIO[AppConfig, Nothing, Int] =
    ZIO.serviceWith[AppConfig](_.poolSize)

  val appArgsLayer: ZLayer[ZIOAppArgs, Nothing, AppConfig] =
    ZLayer {
      ZIOAppArgs.getArgs
        .map(_.headOption.map(_.toInt).getOrElse(8))
        .map(poolSize => AppConfig(poolSize))
    }

  val systemEnvLayer: ZLayer[Any, SecurityException, AppConfig] =
    ZLayer.fromZIO(
      System
        .env("POOL_SIZE")
        .map(_.headOption.map(_.toInt).getOrElse(8))
        .map(poolSize => AppConfig(poolSize))
    )
}

object MainApp extends ZIOAppDefault {
  val myApp: ZIO[AppConfig, Nothing, Unit] =
    for {
      poolSize <- AppConfig.poolSize
      _        <- ZIO.debug(s"Application started with $poolSize pool size.")
    } yield ()

  def run = myApp.provideSome(AppConfig.appArgsLayer)
}
```

The `AppConfig` has two layers, `appArgsLayer` and `systemEnvLayer`. The first one uses command-line arguments to create the `AppConfig` and the second one uses environment variables. As we can see, without changing the core logic of our application, we can easily change the way we get the configuration:

```diff
object MainApp extends ZIOAppDefault {
  val myApp: ZIO[AppConfig, Nothing, Unit] =
    for {
      poolSize <- AppConfig.poolSize
      _        <- ZIO.debug(s"Application started with $poolSize pool size.")
    } yield ()

-  def run = myApp.provideLayer(AppConfig.appArgsLayer)
+  def run = myApp.provideLayer(AppConfig.systemEnvLayer)
}
```

### Example 2: Logging Service

In this example, we have a ZIO application that uses the `Logging` service. And we provided two implementations of the `Logging` service: `SimpleLogger` and `DateTimeLogger`:

```scala mdoc:compile-only
import zio._

import java.io.IOException

trait Logging {
  def log(msg: String): ZIO[Any, IOException, Unit]
}

object Logging {
  def log(msg: String): ZIO[Logging, IOException, Unit] =
    ZIO.serviceWithZIO[Logging](_.log(msg))
}

case class DateTimeLogger() extends Logging {
  override def log(msg: String): ZIO[Any, IOException, Unit] =
    for {
      dt <- Clock.currentDateTime
      _  <- Console.printLine(s"$dt: $msg")
    } yield ()
}

object DateTimeLogger {
  val live: ULayer[DateTimeLogger] =
    ZLayer.succeed(DateTimeLogger())
}

case class SimpleLogger() extends Logging {
  override def log(msg: String): ZIO[Any, IOException, Unit] =
    Console.printLine(msg)
}
object SimpleLogger {
  val live: ULayer[SimpleLogger] =
    ZLayer.succeed(SimpleLogger())
}
```

Now, let's write a ZIO application that uses the `Logging` service:

```scala mdoc:silent
val myApp: ZIO[Logging, IOException, Unit] =
  for {
    _ <- Logging.log("Application started.")
    _ <- Logging.log("Application ended.")
  } yield ()
```

Now, we can run our application, just by providing one of the implementations of the `Logging` service. Let's run it with the `SimpleLogger` implementation:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(SimpleLogger.live)
}
```

Now, we can see that, without changing the core logic of our application, we can easily change the logger implementation:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(DateTimeLogger.live)
}
```

## Example

### Providing Multiple Instance of an Operational Service

In the previous example, we discussed a simple configuration service that doesn't have any functionality. In this example, we are going to define a `KeyValueStore` service and implement two versions of it: one that uses in-memory storage and the other one that uses persistent storage:

```scala mdoc:silent
import zio._

import java.nio.charset.StandardCharsets

trait KeyValueStore {
  def put(key: String, value: Array[Byte]): ZIO[Any, String, Unit]
  def get(key: String): ZIO[Any, String, Option[Array[Byte]]]
}

object KeyValueStore {
  def put(key: String, value: Array[Byte]): ZIO[KeyValueStore, String, Unit] =
    ZIO.serviceWithZIO[KeyValueStore](_.put(key, value))

  def get(key: String): ZIO[KeyValueStore, String, Option[Array[Byte]]] =
    ZIO.serviceWithZIO[KeyValueStore](_.get(key))
}

case class InmemoryKeyValueStore(ref: Ref[Map[String, Array[Byte]]]) extends KeyValueStore {
  override def put(key: String, value: Array[Byte]): ZIO[Any, String, Unit] =
    ref.update(_ + (key -> value))

  override def get(key: String): ZIO[Any, String, Option[Array[Byte]]] =
    ref.get.map(_.get(key))
}

object InmemoryKeyValueStore {
  val layer: ZLayer[Any, Nothing, InmemoryKeyValueStore] =
    ZLayer {
      Ref.make(Map.empty[String, Array[Byte]]).map(new InmemoryKeyValueStore(_))
    }
}

trait RocksDB {
  // ...
}

case class RockDbLive() extends RocksDB
object RockDbLive {
  val layer: ULayer[RocksDB] =
    ZLayer.succeed(RockDbLive())
}

case class PersistentKeyValueStore(client: RocksDB) extends KeyValueStore {
  override def put(key: String, value: Array[Byte]): ZIO[Any, String, Unit] = ???
  override def get(key: String): ZIO[Any, String, Option[Array[Byte]]]      = ???
}

object PersistentKeyValueStore {
  val layer: ZLayer[RocksDB, Nothing, PersistentKeyValueStore] =
    ZLayer {
      for {
        rocksdb <- ZIO.service[RocksDB]
      } yield PersistentKeyValueStore(rocksdb)
    }
}
```

Now, we can write our application based on this service:

```scala mdoc:silent
val myApp: ZIO[KeyValueStore, Serializable, Unit] =
  for {
    _     <- KeyValueStore.put("john", "john@doe.com".getBytes(StandardCharsets.UTF_8))
    email <- KeyValueStore.get("john").someOrFail("d").map(new String(_, StandardCharsets.UTF_8))
    _     <- ZIO.debug(s"retrieved john's email from key value store: $email")
  } yield ()
```

To be able to run our application, we need to provide a layer that provides a `KeyValueStore` service. If we want to use in-memory storage we can provide the `InmemoryKeyValueStore` layer:

```scala mdoc:silent
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(InmemoryKeyValueStore.layer)
}
```

Otherwise, if we want to use persistent storage we can provide the `PersistentKeyValueStore` layer which itself depends on the `RocksDB` layer. So, we have two options to run `myApp` in this case:

1. Manually construct the dependency graph and provide it to `myApp`:

```scala mdoc:silent:nest
object MainApp extends ZIOAppDefault {
  val appLayer: ZLayer[Any, Nothing, PersistentKeyValueStore] =
    // Manually building the dependency graph using vertical composition (>>>)
    RockDbLive.layer >>> PersistentKeyValueStore.layer
    
  def run = myApp.provideLayer(appLayer)
}
```

2. Or, we can use automatic layer construction, by providing all required layers to the `ZIO#provide` operator. This will automatically generate the dependency graph and provide that to our application:

```scala mdoc:compile-only
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(RockDbLive.layer, PersistentKeyValueStore.layer)
}
```

:::note
To build the dependency graph we have two options:
1. [Manual layer construction](manual-layer-construction.md) (using `ZIO#provideLayer`)
2. [Automatic layer construction](automatic-layer-construction.md) (using`ZIO#provide`)

To learn more about this topic, we have a separate page dedicated to [building dependency graph](building-dependency-graph.md).
:::
