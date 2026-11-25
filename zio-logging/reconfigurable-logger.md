# Reconfigurable Logger

> `ReconfigurableLogger` is a `ZLogger` whose configuration and underlying logger can be replaced while the application runs. Every log call goes to the logger that is current at that moment, so a change takes effect on the next log line without a restart.

There are two ways to change it. With `ReconfigurableLogger.make`, the logger pulls its configuration: it reloads the configuration on a schedule and rebuilds the underlying logger when the configuration has changed. With `ReconfigurableLogger.apply`, your own code pushes a new configuration and logger with `ReconfigurableLogger#set`, for example from an admin endpoint. For a task-oriented walk-through of both, see [How to Change Log Levels at Runtime](how-to-change-levels-at-runtime.md).

## Reloading the Configuration on a Schedule

To create a console logger layer that reloads its configuration from a `ConfigProvider` every 5 seconds, wrap [console logger](console-logger.md) construction in `ReconfigurableLogger.make`:

```scala
import zio.logging.{ ConsoleLoggerConfig, ReconfigurableLogger, makeConsoleLogger }
import zio._

val reconfigurableConfigProvider: ConfigProvider =
  ConfigProvider.fromMap(Map("logger/filter/rootLevel" -> "INFO"), "/")

val reconfigurableLoggerLayer = Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(reconfigurableConfigProvider) >>> ReconfigurableLogger
  .make[Any, Nothing, String, Any, ConsoleLoggerConfig](
    ConsoleLoggerConfig.load().orDie, // loading current configuration
    (config, _) => makeConsoleLogger(config), // make logger from loaded configuration
    Schedule.fixed(5.second) // default is 10 seconds
  )
  .installUnscoped[Any]
```

`ReconfigurableLogger.make` returns a `ZIO` that needs a `Scope`, because it starts a background fiber that reloads the configuration, and that fiber must stop when the logger is no longer used. The `installUnscoped` syntax turns this effect into a layer: the layer supplies the `Scope` itself, installs the logger for as long as the layer lives, and stops the reload fiber when the layer is released. The `install`, `installUnscoped` and `installScoped` helpers are described in [Logger Layers and Constructors Reference](reference-logger-layers.md).

## How `ReconfigurableLogger.make` Works

`ReconfigurableLogger.make` is defined as follows:

```scala
object ReconfigurableLogger {
  def make[R, E, M, O, C: Equal](
    loadConfig: => ZIO[R, E, C],
    makeLogger: (C, Option[ZLogger[M, O]]) => ZIO[R, E, ZLogger[M, O]],
    updateLogger: Schedule[R, Any, Any] = Schedule.fixed(10.seconds)
  ): ZIO[R with Scope, E, ReconfigurableLogger[M, O, C]]
}
```

The type parameters are the environment `R` and error `E` of loading and building, the message type `M` and output type `O` of the logger, and the configuration type `C`. The function works in these steps:

1. It runs `loadConfig` once and calls `makeLogger(config, None)` to build the first logger. If either fails, `ReconfigurableLogger.make` fails with that error.
2. It forks a fiber in the scope that runs `loadConfig` again on the `updateLogger` schedule. The first reload happens after the schedule's first interval, not at startup, so with the default schedule the first reload is 10 seconds after the logger is built.
3. On each reload, it compares the new configuration with the current one. Only when they differ does it call `makeLogger(newConfig, Some(currentLogger))` and switch to the new logger. When they are equal, nothing is rebuilt.

The comparison uses the ZIO Prelude `Equal` instance for `C`, which is why `C` needs one. ZIO Logging provides `ConsoleLoggerConfig.equal`, `FileLoggerConfig.equal`, `LogFilter.LogLevelByNameConfig.equal` and `LogFilter.equal` (see [Configuration Keys Reference](reference-configuration.md#config-descriptors-and-equal-instances)). For a configuration type of your own, define an implicit `Equal`, for example `Equal.default` for a case class whose fields compare correctly with `==`.

The second argument of `makeLogger` is the logger that is about to be replaced: `None` when the first logger is built, and `Some(previousLogger)` on every rebuild. Use it when the new logger can reuse something from the old one, or ignore it with `(config, _) =>` as in the example above.

When the schedule ends, for example with `Schedule.recurs(3)`, the reloading stops and the last logger stays in place.

:::caution
Reloading stops at the first failure. If `loadConfig` or `makeLogger` fails during a reload, for example because the configuration source holds an invalid value for a moment, the reload fiber ends, the current logger stays in place, and later configuration changes are not picked up until the application restarts. Handle errors inside `loadConfig` and `makeLogger` if the reloading must survive them.

The replaced logger is not closed either. A logger that holds a resource, such as a file logger with its open file, keeps that resource after it has been replaced.
:::

## Pushing a New Configuration

`ReconfigurableLogger.apply` creates a reconfigurable logger with no reload fiber. It starts with the configuration and logger you pass, and your code replaces them by calling `ReconfigurableLogger#set`. Use it when the change comes from inside the application, such as an admin endpoint or a feature flag, rather than from a configuration source that can be polled. The trait has these members:

```scala
sealed trait ReconfigurableLogger[-Message, +Output, Config] extends ZLogger[Message, Output] {
  def get: (Config, ZLogger[Message, Output])
  def set[M <: Message, O >: Output](config: Config, logger: ZLogger[M, O]): Unit
}

object ReconfigurableLogger {
  def apply[Message, Output, Config](
    config: Config,
    logger: ZLogger[Message, Output]
  ): ReconfigurableLogger[Message, Output, Config]
}
```

`ReconfigurableLogger#get` returns the current configuration and logger, and `ReconfigurableLogger#set` replaces both at once. Both are plain methods, not effects, and they work on an atomic reference, so they are safe to call from any fiber. A `get` followed by a `set` is not one atomic step, though: two fibers that update the logger at the same time can overwrite each other's change. `ReconfigurableLogger#set` does not compare configurations, so it needs no `Equal` instance.

The following application installs a console logger that starts at the `INFO` level, exposes the `ReconfigurableLogger` as a service, and lowers the root level to `DEBUG` from inside the program:

```scala
import zio._
import zio.logging.{ ConsoleLoggerConfig, ReconfigurableLogger, makeConsoleLogger }

object LevelSwitchApp extends ZIOAppDefault {

  type ConsoleReconfigurableLogger = ReconfigurableLogger[String, Any, ConsoleLoggerConfig]

  val reconfigurableConsole: ZLayer[Any, Nothing, ConsoleReconfigurableLogger] =
    ZLayer.scoped {
      for {
        initial <- makeConsoleLogger(ConsoleLoggerConfig.default)
        logger   = ReconfigurableLogger[String, Any, ConsoleLoggerConfig](ConsoleLoggerConfig.default, initial)
        _       <- ZIO.withLoggerScoped(logger)
      } yield logger
    }

  def setRootLevel(level: LogLevel): URIO[ConsoleReconfigurableLogger, Unit] =
    ZIO.serviceWithZIO[ConsoleReconfigurableLogger] { logger =>
      val (config, _) = logger.get
      val newConfig   = config.copy(filter = config.filter.withRootLevel(level))
      makeConsoleLogger(newConfig).map(newLogger => logger.set(newConfig, newLogger))
    }

  override def run: ZIO[Any, Nothing, Unit] =
    (for {
      _ <- ZIO.logDebug("Not written: the root level is INFO")
      _ <- setRootLevel(LogLevel.Debug)
      _ <- ZIO.logDebug("Written: the root level is now DEBUG")
    } yield ()).provideLayer(Runtime.removeDefaultLoggers >>> reconfigurableConsole)
}
```

`ZIO.withLoggerScoped` adds the reconfigurable logger to the loggers of the layer's scope, and the layer also returns it as a service, so `setRootLevel` can reach it. The first `ZIO.logDebug` call is dropped by the `INFO` filter; after `ReconfigurableLogger#set`, the second one is written.

## Performance

Consider whether your application needs this feature, as it may affect performance (see [benchmarks](https://github.com/zio/zio-logging/blob/master/benchmarks/src/main/scala/zio/logging/FilterBenchmarks.scala)).

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples).

### Console Logger With Re-configuration From Configuration File In Runtime

This example from `examples/core/jvm/src/main/scala/zio/logging/example/LoggerReconfigureApp.scala` reloads the logger configuration when the `logger.conf` file changes. It is an application, not library API.

The `logger.conf` configuration:

```hocon
logger {
  format = "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"
  filter {
    rootLevel = "INFO"
    mappings {
      "zio.logging.example" = "DEBUG"
    }
  }
}
```

Application:

```scala
import com.typesafe.config.ConfigFactory
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ ConsoleLoggerConfig, LogAnnotation, ReconfigurableLogger, makeConsoleLogger }

import java.util.UUID

object LoggerReconfigureApp extends ZIOAppDefault {

  def configuredLogger(
    loadConfig: => ZIO[Any, Config.Error, ConsoleLoggerConfig]
  ): ZLayer[Any, Config.Error, Unit] =
    ReconfigurableLogger
      .make[Any, Config.Error, String, Any, ConsoleLoggerConfig](
        loadConfig,
        (config, _) => makeConsoleLogger(config),
        Schedule.fixed(500.millis)
      )
      .installUnscoped[Any]

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> configuredLogger(
      for {
        config       <- ZIO.succeed(ConfigFactory.load("logger.conf"))
        _            <- Console.printLine(config.getConfig("logger")).orDie
        loggerConfig <- ConsoleLoggerConfig.load().withConfigProvider(TypesafeConfigProvider.fromTypesafeConfig(config))
      } yield loggerConfig
    )

  def exec(): ZIO[Any, Nothing, Unit] =
    for {
      ok      <- Random.nextBoolean
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.logDebug("Start") @@ LogAnnotation.TraceId(traceId)
      userIds <- ZIO.succeed(List.fill(2)(UUID.randomUUID().toString))
      _       <- ZIO.foreachPar(userIds) { userId =>
        {
          ZIO.logDebug("Starting operation") *>
            ZIO.logInfo("OK operation").when(ok) *>
            ZIO.logError("Error operation").when(!ok) *>
            ZIO.logDebug("Stopping operation")
        } @@ LogAnnotation.UserId(userId)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logDebug("Done") @@ LogAnnotation.TraceId(traceId)
    } yield ()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success

}
```

When the configuration for `logger/filter/mappings/zio.logging.example` changes from `DEBUG` to `WARN`, the application prints the loaded configuration and then logs only `WARN` and higher for that logger:

```
Config(SimpleConfigObject({"filter":{"mappings":{"zio.logging.example":"DEBUG"},"rootLevel":"INFO"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:51 Start trace_id=87ead38c-8b42-43ea-9905-039d0263026d  
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-36] zio.logging.example.LoggerReconfigureApp:55 Starting operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=dfa05247-ec27-46f7-a4e0-bb86f2d501e9 
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-37] zio.logging.example.LoggerReconfigureApp:55 Starting operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=19693c77-0896-4fae-a830-67d5fe370b05 
2023-12-26T10:10:26+0100 ERROR   [zio-fiber-36] zio.logging.example.LoggerReconfigureApp:57 Error operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=dfa05247-ec27-46f7-a4e0-bb86f2d501e9 
2023-12-26T10:10:26+0100 ERROR   [zio-fiber-37] zio.logging.example.LoggerReconfigureApp:57 Error operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=19693c77-0896-4fae-a830-67d5fe370b05 
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-36] zio.logging.example.LoggerReconfigureApp:58 Stopping operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=dfa05247-ec27-46f7-a4e0-bb86f2d501e9 
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-37] zio.logging.example.LoggerReconfigureApp:58 Stopping operation trace_id=87ead38c-8b42-43ea-9905-039d0263026d user_id=19693c77-0896-4fae-a830-67d5fe370b05 
2023-12-26T10:10:26+0100 DEBUG   [zio-fiber-5] zio.logging.example.LoggerReconfigureApp:61 Done trace_id=87ead38c-8b42-43ea-9905-039d0263026d  
Config(SimpleConfigObject({"filter":{"mappings":{"zio.logging.example":"WARN"},"rootLevel":"INFO"},"format":"%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"}))
2023-12-26T10:10:27+0100 ERROR   [zio-fiber-40] zio.logging.example.LoggerReconfigureApp:57 Error operation trace_id=e2d8bbb4-5ad0-4952-b035-03da7689ab56 user_id=0f6452da-3f7e-40ff-b8b4-6b4c731903fb 
2023-12-26T10:10:27+0100 ERROR   [zio-fiber-41] zio.logging.example.LoggerReconfigureApp:57 Error operation trace_id=e2d8bbb4-5ad0-4952-b035-03da7689ab56 user_id=c4a86b38-90d7-4bb6-9f49-73bc5701e1ef  
```

### Console Logger With Configuration By Http APIs

This example from `examples/core/jvm/src/main/scala/zio/logging/example/ConfigurableLoggerApp.scala` changes the logger configuration through HTTP endpoints. It is example-project code, not library API. It depends on zio-http and on the classes `ConfigurableLogger`, `LoggerConfigurer` and `ApiHandlers`, which live in the example project under `examples/core/shared/src/main/scala/zio/logging/ConfigurableLogger.scala` and `examples/core/jvm/src/main/scala/zio/logging/api/http/`. It is not type-checked by mdoc.

Logger configurations APIs:
* get logger configurations
 ```bash
  curl -u "admin:admin" 'http://localhost:8080/example/logger'
 ```
* get `root` logger configuration
 ```bash
  curl -u "admin:admin" 'http://localhost:8080/example/logger/root'
 ```
* set `root` logger configuration
 ```bash
  curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/root' --header 'Content-Type: application/json' --data-raw '"WARN"'
 ```
* set `zio.logging.example` logger configuration
 ```bash
  curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/zio.logging.example' --header 'Content-Type: application/json' --data-raw '"WARN"'
 ```

The `logger.conf` configuration:

```hocon
logger {
  format = "%highlight{%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %kv{trace_id} %kv{user_id} %cause}"
  filter {
    rootLevel = "INFO"
    mappings {
      "zio.logging.example" = "DEBUG"
    }
  }
}
```

Application:

```scala
package zio.logging.example

import com.typesafe.config.ConfigFactory
import zio.config.typesafe.TypesafeConfigProvider
import zio.http._
import zio.logging.api.http.ApiHandlers
import zio.logging.{ ConfigurableLogger, ConsoleLoggerConfig, LogAnnotation, LoggerConfigurer, makeSystemOutLogger }
import zio.{ ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, _ }

import java.util.UUID

object ConfigurableLoggerApp extends ZIOAppDefault {

  def configurableLogger(): ZLayer[Any, Config.Error, Unit] =
    ConsoleLoggerConfig
      .load()
      .flatMap { config =>
        makeSystemOutLogger(config.format.toLogger).map { logger =>
          ConfigurableLogger.make(logger, config.filter)
        }
      }
      .install

  val configProvider: ConfigProvider = TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load("logger.conf"))

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> configurableLogger()

  def exec(): ZIO[Any, Nothing, Unit] =
    for {
      ok      <- Random.nextBoolean
      traceId <- ZIO.succeed(UUID.randomUUID())
      _       <- ZIO.logDebug("Start") @@ LogAnnotation.TraceId(traceId)
      userIds <- ZIO.succeed(List.fill(2)(UUID.randomUUID().toString))
      _       <- ZIO.foreachPar(userIds) { userId =>
        {
          ZIO.logDebug("Starting operation") *>
            ZIO.logInfo("OK operation").when(ok) *>
            ZIO.logError("Error operation").when(!ok) *>
            ZIO.logDebug("Stopping operation")
        } @@ LogAnnotation.UserId(userId)
      } @@ LogAnnotation.TraceId(traceId)
      _       <- ZIO.logDebug("Done") @@ LogAnnotation.TraceId(traceId)
    } yield ()

  val httpApp: Routes[LoggerConfigurer with Any, Nothing] =
    ApiHandlers.routes("example" :: Nil) @@ Middleware.basicAuth("admin", "admin")

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- Server.serve(httpApp).fork
      _ <- exec().repeat(Schedule.fixed(500.millis))
    } yield ExitCode.success).provide(LoggerConfigurer.layer ++ Server.default)

}
```

:::note
`ConfigurableLogger`, `LoggerConfigurer` and `ApiHandlers` live in the example project, not in the published `zio-logging` artifacts. If you want to use them in your app, copy them from the example project. For the same effect with the library alone, push a new configuration with `ReconfigurableLogger#set` as shown in [Pushing a New Configuration](#pushing-a-new-configuration).
:::

When the configuration for `logger/filter/mappings/zio.logging.example` changes from `DEBUG` to `WARN`, send the following request:

```bash
curl -u "admin:admin" --location --request PUT 'http://localhost:8080/example/logger/zio.logging.example' --header 'Content-Type: application/json' --data-raw '"WARN"'
```

The application then logs only `WARN` and higher for that logger:

```
2023-12-26T10:49:27+0100 DEBUG   [zio-fiber-73] zio.logging.example.ConfigurableLoggerApp:62 Starting operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=5d35778f-78ff-48a8-aa6a-73114ec719b5 
2023-12-26T10:49:27+0100 DEBUG   [zio-fiber-72] zio.logging.example.ConfigurableLoggerApp:62 Starting operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=9e17bd97-aa18-4b09-a423-9de28241a20b 
2023-12-26T10:49:27+0100 INFO    [zio-fiber-73] zio.logging.example.ConfigurableLoggerApp:63 OK operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=5d35778f-78ff-48a8-aa6a-73114ec719b5 
2023-12-26T10:49:27+0100 INFO    [zio-fiber-72] zio.logging.example.ConfigurableLoggerApp:63 OK operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=9e17bd97-aa18-4b09-a423-9de28241a20b 
2023-12-26T10:49:27+0100 DEBUG   [zio-fiber-73] zio.logging.example.ConfigurableLoggerApp:65 Stopping operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=5d35778f-78ff-48a8-aa6a-73114ec719b5 
2023-12-26T10:49:27+0100 DEBUG   [zio-fiber-72] zio.logging.example.ConfigurableLoggerApp:65 Stopping operation trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045 user_id=9e17bd97-aa18-4b09-a423-9de28241a20b 
2023-12-26T10:49:27+0100 DEBUG   [zio-fiber-4] zio.logging.example.ConfigurableLoggerApp:68 Done trace_id=dcf30228-dc00-4c1f-ab94-20c9f8116045  
2023-12-26T10:49:28+0100 ERROR   [zio-fiber-77] zio.logging.example.ConfigurableLoggerApp:64 Error operation trace_id=7da8765e-2e27-42c6-8834-16d15d21c72c user_id=4395d188-5971-4839-a721-278d07a2881b 
2023-12-26T10:49:28+0100 ERROR   [zio-fiber-78] zio.logging.example.ConfigurableLoggerApp:64 Error operation trace_id=7da8765e-2e27-42c6-8834-16d15d21c72c user_id=27af6873-2f7b-4b9a-ad2b-6bd12479cace  
```
