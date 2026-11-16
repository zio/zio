# Reconfigurable Logger

> `ReconfigurableLogger` adds support for updating the logger configuration at application runtime.

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

`ReconfigurableLogger.make` runs the load configuration function on the given `Schedule`. It recreates the logger when the loaded configuration changes.

**NOTE:** Consider whether your application needs this feature, as it may affect performance (see [benchmarks](https://github.com/zio/zio-logging/blob/master/benchmarks/src/main/scala/zio/logging/FilterBenchmarks.scala)).

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples).

### Console Logger With Re-configuration From Configuration File In Runtime

This example from `examples/core/jvm/src/main/scala/zio/logging/example/LoggerReconfigureApp.scala` reloads the logger configuration when the `logger.conf` file changes. It is an application, not library API.

Configuration:

```
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

Configuration:

```
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

**NOTE:** `ConfigurableLogger` and `ApiHandlers` are currently implemented in examples.
They may move into the official `zio-logging` implementation in the future, once there is an official stable `zio-http` release.
If you want to use them in your app, copy them from the example project.

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
