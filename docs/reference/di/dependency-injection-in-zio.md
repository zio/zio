---
id: dependency-injection-in-zio
title: "Dependency Injection in ZIO"
---

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

## Providing Multiple Instances of a Service

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

  def run = myApp.provideLayer(AppConfig.appArgsLayer)
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
