# File Logger

> The file logger writes formatted log lines to a file. It is available on the JVM and Native platforms only. You can configure it from a `ConfigProvider` or from a `FileLoggerConfig` value.

To create a file logger layer that reads its configuration from a `ConfigProvider`, define the `logger/path` key and the other keys that the layer needs:

```scala
import zio.logging.{ fileLogger, FileLoggerConfig, LogFilter, LogFormat }
import zio.{ ConfigProvider, Runtime }

val fileConfigProvider: ConfigProvider = ConfigProvider.fromMap(
  Map("logger/path" -> "file:///tmp/console_app.log"),
  "/"
)

val fileLoggerFromProvider = Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(fileConfigProvider) >>> fileLogger()
```

To create a file logger layer from an explicit `FileLoggerConfig`, pass the destination path, format and filter:

```scala
import zio.logging.{ fileLogger, FileLoggerConfig, LogFilter, LogFormat }
import zio.Runtime

val fileConfig: FileLoggerConfig = FileLoggerConfig(
  destination = java.nio.file.Paths.get("/tmp/console_app.log"),
  format = LogFormat.default,
  filter = LogFilter.LogLevelByNameConfig.default
)

val fileLoggerFromConfig = Runtime.removeDefaultLoggers >>> fileLogger(fileConfig)
```

The module also provides other file logger layers:

* `zio.logging.fileJsonLogger` - writes log lines in JSON format
* File async loggers, which write through a bounded queue on a background fiber:
    * `zio.logging.fileAsyncLogger` - writes log lines in string format
    * `zio.logging.fileAsyncJsonLogger` - writes log lines in JSON format

## Configuration

The configuration for the file logger (`zio.logging.FileLoggerConfig`) has the following structure. The default config path is `logger`:

```
logger {
  # log format, default value: LogFormat.default
  format = "%label{timestamp}{%fixed{32}{%timestamp}} %label{level}{%level} %label{thread}{%fiberId} %label{message}{%message} %label{cause}{%cause}"

  # URI to file
  path = "file:///tmp/console_app.log"

  # charset configuration, default value: UTF-8
  charset = "UTF-8"

  # auto flush batch size, default value: 1
  autoFlushBatchSize = 1

  # if defined, buffered writer is used, with given buffer size
  # bufferedIOSize = 8192

  # if defined, file log rolling policy is used
  rollingPolicy {
    type = TimeBasedRollingPolicy # time based file rolling policy based on date - currently only this one is supported
  }

  # log filter
  filter {
    # see filter configuration
    rootLevel = INFO
  }
}
```

See also [log format configuration](formatting-log-records.md#log-format-configuration) and [filter configuration](log-filter.md#configuration).

## Examples

You can find the source code [here](https://github.com/zio/zio-logging/tree/master/examples).



The `FileApp` example from `examples/core/jvm/src/main/scala/zio/logging/example/FileApp.scala` loads its configuration from a HOCON string and writes to `/tmp`. It depends on `zio-config-typesafe` and is not part of the library API:

```scala
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.fileLogger
import zio.{ Config, ConfigProvider, ExitCode, Runtime, Scope, ZIO, ZIOAppDefault, ZLayer }

object FileApp extends ZIOAppDefault {

  val configString: String =
    s"""
       |logger {
       |  format = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause"
       |  path = "file:///tmp/file_app.log"
       |  rollingPolicy {
       |    type = TimeBasedRollingPolicy
       |  }
       |}
       |""".stripMargin

  val configProvider: ConfigProvider = TypesafeConfigProvider.fromHoconString(configString)

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> fileLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Start")
      _ <- ZIO.fail("FAILURE")
      _ <- ZIO.logInfo("Done")
    } yield ExitCode.success
}
```

Expected file name: `file_app-2023-03-05.log`

Expected file content:

```
2023-03-05T12:37:31+0100 INFO    [zio-fiber-4] zio.logging.example.FileApp:40 Start
2023-03-05T12:37:31+0100 ERROR   [zio-fiber-0] zio-logger:  Exception in thread "zio-fiber-4" java.lang.String: FAILURE
        at zio.logging.example.FileApp.run(FileApp.scala:41)
```
