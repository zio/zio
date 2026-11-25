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

```hocon
logger {
  # log format, default value: LogFormat.default
  # The pattern below is approximately LogFormat.default. The default also puts the
  # message in double quotes, and it writes the cause, with the space before it,
  # only when the cause is not empty.
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

See also [log format configuration](formatting-log-records.md#log-format-configuration) and [filter configuration](log-filter.md#configuration). Every key, with its type and default, is listed in [Configuration Keys Reference](reference-configuration.md#file-logger-keys).

## Async File Loggers

`zio.logging.fileAsyncLogger` and `zio.logging.fileAsyncJsonLogger` take the same `FileLoggerConfig`, or the same config path, as `fileLogger`. The difference is where the file write happens. The synchronous loggers write each line on the fiber that logs. The async loggers format and filter the line on that fiber, then put the write on a bounded queue of 1000 entries, and one background fiber takes the writes from the queue and performs them in order.

Use an async logger when slow file writes would otherwise hold up the fibers that log, for example on a busy service writing to a slow disk. Keep the synchronous `fileLogger` when every line must reach the file before the program moves on.

To create an async file logger layer from the `fileConfig` value above:

```scala
import zio.logging.fileAsyncLogger

val fileAsyncLoggerFromConfig = Runtime.removeDefaultLoggers >>> fileAsyncLogger(fileConfig)
```

The queue changes what happens under load and at shutdown:

- When the queue is full, the thread that logs blocks until the background fiber has written a line and freed a slot. The logger offers to the queue with `Runtime.default.unsafe.run`, so it blocks the underlying thread, not only the fiber.
- The background fiber lives in the layer's scope. When that scope closes, for example when the application stops, the fiber is interrupted, and lines still in the queue are not written.

## Flushing and Write Errors

The file logger writes through a `java.io.Writer`, which holds written text in a buffer until it is flushed. The `autoFlushBatchSize` key sets how many lines are written between two flushes, and `bufferedIOSize` adds a `java.io.BufferedWriter` of that size in front of the file.

The logger does not flush or close the writer when its layer's scope closes. With `autoFlushBatchSize` greater than `1`, the lines written since the last flush can therefore be lost when the application stops. Keep the default `autoFlushBatchSize = 1` when the last lines before a shutdown matter, such as the lines that explain a crash. With `autoFlushBatchSize = 1`, a `bufferedIOSize` does not hold lines back, because each flush also empties the buffered writer.

Set `autoFlushBatchSize` to `1` or more. The config loader accepts `0`, but with `0` the flush step throws on every line, the error is swallowed, and the writer is never flushed.

The logger opens the file when the layer is built. If the file cannot be opened at that point, for example because its directory does not exist, building the layer fails with a `java.io.FileNotFoundException` as a defect, not as a `Config.Error`, and the application does not start. After startup, the logger ignores write errors: it drops any exception other than a `VirtualMachineError`, and the line is lost without an error in the application.

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
