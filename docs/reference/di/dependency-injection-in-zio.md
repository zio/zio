---
id: dependency-injection-in-zio
title: "Dependency Injection in ZIO"
---

We can achieve dependency injection through these three simple steps:
1. Accessing services from the ZIO environment through the `ZIO.service` operation.
2. Building the dependency graph using manual or automatic layer construction.
3. Providing dependencies to the ZIO environment through the `ZIO.provideXYZ` operation.

## Step 1: Accessing Services From The ZIO Environment

To write application logic, we need to access services from the ZIO environment. We can do this by using the `ZIO.service` operation.

- When we call `ZIO.service[A]`, we are asking the ZIO environment for the `A` service. So then we can access all the functionality of the `A` service.
- Accordingly, we can access all services that we need and use them in our application. Because the ZIO environment is composable, finally the type of our application will denote all the services that it needs.

For example, assume we have the following services:

```scala mdoc:silent
import zio._

trait A {
  def foo(): UIO[String]
}

trait B {
  def bar(i: String): UIO[Int]
}
```

We can write the following application logic:

```scala mdoc:silent
import zio._

// myApp requires A and B services to fulfill its functionality
val myApp: ZIO[A with B, Nothing, Int] =
  for {
    a <- ZIO.service[A] 
    r <- a.foo()
    b <- ZIO.service[B]
    o <- b.bar(r)
  } yield o
```

## Step 2: Building The Dependency Graph

To be able to run our application, we need to build the dependency graph that it needs. This can be done using the `ZLayer` data type. It allows us to build up the whole application's dependency graph by composing layers manually or automatically.

Assume each of these services has its own layer like the bellow:

```scala mdoc:silent
object A {
  val layer: ZLayer[Any, Nothing, A] = 
    ZLayer.succeed(???) 
}

object B {
  val layer: ZLayer[Any, Nothing, B] = 
    ZLayer.succeed(???)
}
```

In the previous example, the `myApp` application requires the `A` and `B` services. We can build that manually by composing two `A` and `B` layers horizontally:

```scala mdoc:silent
val appLayer: ZLayer[Any, Nothing, A with B] = 
  A.layer ++ B.layer
```

Or we can use automatic layer construction:

```scala mdoc:compile-only
val appLayer: ZLayer[Any, Nothing, A with B] =
  ZLayer.make[A with B](A.layer, B.layer) 
```

:::note
Automatic layer construction is useful when the dependency graph is large and complex. So in simple cases, it doesn't demonstrate the power of automatic layer construction.
:::

## Step 3: Providing Dependencies to the ZIO Environment

To run our application, we need to provide (inject) all dependencies to the ZIO environment. This can be done by using one of the `ZIO.provideXYZ` operations. This allows us to propagate dependencies from button to top:

Let's provide our application with the `appLayer`:

```scala mdoc:silent
val result: ZIO[Any, Nothing, Int] = myApp.provideLayer(appLayer)
```

Here the `ZLayer` data types act as a dependency/environment eliminator. By providing required dependencies to our ZIO application, `ZLayer` eliminates all dependencies from the environment of our application.

That's it! Now we can run our application:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = result
}
```

Usually, when we use automatic layer construction, we skip the second step and instead provide all dependencies directly to the `ZIO.provide` operation. It takes care of building the dependency graph and providing the dependency graph to our ZIO application:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = myApp.provide(A.layer, B.layer)
}
```

```scala mdoc:invisible:reset

```


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

```scala mdoc:silent
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
import zio._

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
