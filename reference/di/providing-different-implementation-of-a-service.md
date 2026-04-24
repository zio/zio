# Providing Different Implementation of a Service

> One of the benefits of using dependency injection is that, we can write our application in a way that without modifying the application logic, we can provide different implementations of services to our application.

One of the benefits of using dependency injection is that, we can write our application in a way that without modifying the application logic, we can provide different implementations of services to our application.

## Example 1: Config Service

In the next example, we have a ZIO application that uses the `AppConfig` service:

```scala

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

  def run = myApp.provideSome[ZIOAppArgs](AppConfig.appArgsLayer)
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

-  def run = myApp.provideSome[ZIOAppArgs](AppConfig.appArgsLayer)
+  def run = myApp.provide(AppConfig.systemEnvLayer)
}
```

## Example 2: Logging Service

In this example, we have a ZIO application that uses the `Logging` service. And we provided two implementations of the `Logging` service: `SimpleLogger` and `DateTimeLogger`:

```scala

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

```scala

val myApp: ZIO[Logging, IOException, Unit] =
  for {
    _ <- Logging.log("Application started.")
    _ <- Logging.log("Application ended.")
  } yield ()
```

Now, we can run our application, just by providing one of the implementations of the `Logging` service. Let's run it with the `SimpleLogger` implementation:

```scala
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(SimpleLogger.live)
}
```

Now, we can see that, without changing the core logic of our application, we can easily change the logger implementation:

```scala
object MainApp extends ZIOAppDefault {
  def run = myApp.provide(DateTimeLogger.live)
}
```
