# How to Log to a File With Rotation

> You want your application to write logs to a file on disk, with a new file for each day, so that each file stays small and old days are easy to archive or delete. A single file that grows forever is hard to move, search, and clean up. ZIO Logging writes to a file with `fileLogger`, and its only rolling policy, `TimeBasedRollingPolicy`, starts a new file each day.

The file logger is available on the JVM and Native platforms only. Scala.js has no file logger, so this guide applies to JVM and Native builds.

The steps are:

1. Choose the file settings.
2. Write the settings in a HOCON file.
3. Load the file in the `bootstrap` layer.
4. Understand the file names and when a new file starts.

## Prerequisites

Add the core module to your build. The file logger is part of the JVM and Native artifact, and the examples below assume a JVM build:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging"         % "2.5.4",
  "dev.zio" %% "zio-config-typesafe" % "2.5.4"
)
```

The examples use these imports:

```scala
import zio.{ Config, ConfigProvider, ExitCode, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ FileLoggerConfig, LogFilter, LogFormat, fileLogger }
```

## Choose the File Settings

The `logger` configuration controls the file logger. Each key below maps to a field of `FileLoggerConfig`:

| Key                  | Default             | Meaning                                                                                  |
| -------------------- | ------------------- | ---------------------------------------------------------------------------------------- |
| `path`               | none, required      | Destination as a `file:///` URI, for example `file:///var/log/orders.log`.               |
| `format`             | `LogFormat.default` | Text layout of each line. See [Formatting Log Records](./formatting-log-records.md).     |
| `charset`            | `UTF-8`             | Character encoding of the file. An unknown name is a configuration error.                |
| `autoFlushBatchSize` | `1`                 | The writer flushes after this many lines. `1` flushes every line.                        |
| `bufferedIOSize`     | none                | Size of the character buffer in front of the file writer. When unset, no buffer is added. |
| `rollingPolicy.type` | none                | Rolling policy. The only supported value is `TimeBasedRollingPolicy`.                    |

The `filter` block sets the levels, as in [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md). When `FileLoggerConfig#rollingPolicy` is `None`, the logger appends to the single file named by `path`, and nothing rotates.

## Write the Settings in a Config File

We write the settings in HOCON under the `logger` key. The `path` value must be a `file:///` URI, because `FileLoggerConfig` parses it with `Paths.get(URI.create(...))`. A plain path such as `/var/log/orders.log` is rejected as an invalid path. The settings file uses the URI form:

```hocon
logger {
  format = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause"
  path = "file:///var/log/orders.log"
  charset = "UTF-8"
  autoFlushBatchSize = 1
  bufferedIOSize = 8192
  rollingPolicy {
    type = TimeBasedRollingPolicy
  }
  filter {
    rootLevel = INFO
  }
}
```

The directory `/var/log` must exist and be writable by the process. The logger does not create directories.

## Load the Config in the Bootstrap Layer

We load the HOCON text with `TypesafeConfigProvider.fromHoconString` and install the file logger in the `bootstrap` layer, the same way the `FileApp` example does. The `fileLogger()` layer reads the `logger` key by default. The application installs it as follows:

```scala
object OrdersFileLoggingApp extends ZIOAppDefault {

  val configString: String =
    """logger {
      |  format = "%timestamp{yyyy-MM-dd'T'HH:mm:ssZ} %fixed{7}{%level} [%fiberId] %name:%line %message %cause"
      |  path = "file:///var/log/orders.log"
      |  bufferedIOSize = 8192
      |  rollingPolicy {
      |    type = TimeBasedRollingPolicy
      |  }
      |  filter {
      |    rootLevel = INFO
      |  }
      |}
      |""".stripMargin

  override val bootstrap: ZLayer[ZIOAppArgs, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>>
      Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconString(configString)) >>>
      fileLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Order service started")
      _ <- ZIO.logInfo("Order accepted")
    } yield ExitCode.success
}
```

The file logger can also be built in code. The following value sets the same destination and rolling policy as a `FileLoggerConfig`, and prints two of its fields so that you can see them:

```scala
import java.net.URI
import java.nio.file.Paths

val ordersFileConfig: FileLoggerConfig = FileLoggerConfig(
  destination = Paths.get(URI.create("file:///var/log/orders.log")),
  format = LogFormat.default,
  filter = LogFilter.LogLevelByNameConfig.default,
  bufferedIOSize = Some(8192),
  rollingPolicy = Some(FileLoggerConfig.FileRollingPolicy.TimeBasedRollingPolicy)
)
```

Printing the two fields shows the destination and the rolling policy:

```scala
println(ordersFileConfig.destination)
// /var/log/orders.log
println(ordersFileConfig.rollingPolicy)
// Some(TimeBasedRollingPolicy)
```

To install the file logger from this value, pass it to `fileLogger(config)` in a layer, as `fileLogger(ordersFileConfig)`.

## Understand the File Names and When a New File Starts

With `TimeBasedRollingPolicy`, the logger does not write to the file named in `path`. It inserts the date, in the form `yyyy-MM-dd`, before the last extension of the file name. The directory stays the same:

```text
file:///var/log/orders.log   ->  /var/log/orders-2026-11-10.log
file:///var/log/app.v2.log   ->  /var/log/app.v2-2026-11-10.log
file:///var/log/audit        ->  /var/log/audit-2026-11-10
```

The date comes from the local time zone of the JVM. The logger checks the date on every write. When the date has changed, it closes the current file, opens the file for the new date, and writes the line there. A new file therefore starts at the first log line after local midnight. A process that logs nothing does not create the new file until it logs again.

The library does not delete old files. Its source contains no code that removes files, so keeping a fixed number of days needs a separate job, such as `logrotate` or a scheduled cleanup.

:::warning
The file logger ignores write errors. Each write is wrapped in a `try` block that drops any exception other than a `VirtualMachineError`. If the directory is missing or the disk is full, log lines disappear without an error in the application. Monitor the file, or check its size and modification time, to detect this.
:::

## See also

- [File Logger](./file-logger.md) for the file logger configuration keys and the async variant `fileAsyncLogger`.
- [How to Log JSON](./how-to-json-logs.md) for writing the same file as JSON with `fileJsonLogger`.
- [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md) for the `filter` block used by the file logger.
- [Formatting Log Records](./formatting-log-records.md) for the `format` key.
