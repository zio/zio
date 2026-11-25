# Configuration Keys Reference

> Each built-in logger reads its settings from a configuration source through ZIO Config. This page lists every key for the console logger, the file logger, and the level filter they share. The keys are relative to a root path, which is `logger` by default. Each feature has its own page with examples: [Console Logger](./console-logger.md), [File Logger](./file-logger.md), and [Log Filter](./log-filter.md).

## Default Config Path

The default root path lives in `zio.logging.loggerConfigPath`, as the single segment `logger`. The `ConsoleLoggerConfig.load`, `ConsoleLoggerConfig.make`, `FileLoggerConfig.load`, and `FileLoggerConfig.make` functions, and the logger layers such as `zio.logging.consoleLogger` and `zio.logging.fileLogger`, read from this path unless you pass another path. The source declares the value as follows:

```scala
val loggerConfigPath: NonEmptyChunk[String] = NonEmptyChunk("logger")
```

A custom path is a `NonEmptyChunk[String]`. The first element is the top-level key, and the remaining elements are nested keys under it. For example, `NonEmptyChunk("app", "logging")` reads the keys from `app.logging`.

## Console Logger Keys

The console logger config is `zio.logging.ConsoleLoggerConfig`. It has a format and a level filter. The table lists its keys under the root path.

| Key                | Type                              | Default             | Meaning                                                                                                                                                            |
| ------------------ | --------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `format`           | Format string                     | `LogFormat.default` | The layout of each line. The string form is described in [Formatting Log Records](./formatting-log-records.md).                                                    |
| `filter.rootLevel` | Log level name                    | `INFO`              | The minimum level for every logger name that has no mapping.                                                                                                       |
| `filter.mappings`  | Table of logger name to log level | Empty table         | Minimum levels for logger names. A key is a dot-separated logger name, which also covers the names under it, or a glob pattern; see [Log Filter](./log-filter.md). |

## File Logger Keys

The file logger config is `zio.logging.FileLoggerConfig`. It adds a destination path and the options for writing and rolling the file. The table lists its keys under the root path.

| Key                  | Type                                          | Default             | Meaning                                                                                                                                                                                                 |
| -------------------- | --------------------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `path`               | URI string, such as `file:///var/log/app.log` | None, required      | The file to write. The value is read with `URI.create`, so it must be a URI, not a plain path. An invalid value fails the config load.                                                                  |
| `format`             | Format string                                 | `LogFormat.default` | The layout of each line. See [Formatting Log Records](./formatting-log-records.md).                                                                                                                     |
| `filter.rootLevel`   | Log level name                                | `INFO`              | The minimum level for every logger name that has no mapping.                                                                                                                                            |
| `filter.mappings`    | Table of logger name to log level             | Empty table         | Minimum levels for logger names, with the same rules as the console logger.                                                                                                                             |
| `charset`            | Charset name, such as `UTF-8`                 | `UTF-8`             | The character encoding of the file. The name is read with `Charset.forName`, and an unknown name fails the config load.                                                                                 |
| `autoFlushBatchSize` | Integer                                       | `1`                 | The number of lines written before the writer flushes its buffer. The value `1` flushes after every line. Use `1` or more; see [Flushing and Write Errors](./file-logger.md#flushing-and-write-errors). |
| `bufferedIOSize`     | Integer                                       | None, unbuffered    | When set, the writer wraps the file in a `java.io.BufferedWriter` with a buffer of this many characters.                                                                                                |
| `rollingPolicy.type` | Rolling policy name                           | None, no rolling    | When set, the file rolls over with the rolling policy. The only supported value is `TimeBasedRollingPolicy`.                                                                                            |

The `rollingPolicy.type` key is optional. When the key is absent, the file is not rolled.

## Log Level Filter Keys

The level filter config is `zio.logging.LogFilter.LogLevelByNameConfig`. The console and file loggers read it under the `filter` key, so the full keys are `filter.rootLevel` and `filter.mappings`. The class and its helper methods are described in [LogFilter and LogGroup Reference](./reference-log-filter.md#loglevelbynameconfig).

| Key         | Type                              | Default     | Meaning                                                                                                                                     |
| ----------- | --------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `rootLevel` | Log level name                    | `INFO`      | The minimum level for every event that matches no mapping.                                                                                  |
| `mappings`  | Table of logger name to log level | Empty table | Minimum levels for logger names. Dot-separated names select the mapping. The matching rules are described in [Log Filter](./log-filter.md). |

## Config Descriptors and Equal Instances

The layers and the `ConsoleLoggerConfig.load` and `FileLoggerConfig.load` functions are built on public values that you can also use directly. `ConsoleLoggerConfig.config`, `FileLoggerConfig.config` and the other descriptors below are ZIO `Config` descriptors for the keys in the tables above. A descriptor is not nested under any path; `ConsoleLoggerConfig.load` and `FileLoggerConfig.load` nest it under the root path for you. Use a descriptor when the logger settings are part of a larger config type of your own, or when you need the config value without building a logger. `ConsoleLoggerConfig.equal` and the other `Equal` values below are ZIO Prelude `Equal` instances, which [`ReconfigurableLogger.make`](./reconfigurable-logger.md) needs to decide whether a reloaded config has changed.

| Member                                      | Type                                         | Description                                                                                                                                   |
| ------------------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `ConsoleLoggerConfig.default`               | `ConsoleLoggerConfig`                        | `LogFormat.default` with `LogFilter.LogLevelByNameConfig.default`, which has root level `INFO` and no mappings.                               |
| `ConsoleLoggerConfig.config`                | `Config[ConsoleLoggerConfig]`                | Reads `format` (default `LogFormat.default`) and the `filter` keys.                                                                           |
| `ConsoleLoggerConfig.equal`                 | `Equal[ConsoleLoggerConfig]`                 | Two configs are equal when their formats are equal (`==`) and their filters are equal.                                                        |
| `FileLoggerConfig.config`                   | `Config[FileLoggerConfig]`                   | Reads every key in [File Logger Keys](#file-logger-keys), with the defaults listed there.                                                     |
| `FileLoggerConfig.equal`                    | `Equal[FileLoggerConfig]`                    | Two configs are equal when the destination, charset, `autoFlushBatchSize`, `bufferedIOSize`, rolling policy, format and filter are all equal. |
| `FileLoggerConfig.FileRollingPolicy.config` | `Config[FileLoggerConfig.FileRollingPolicy]` | Reads the `type` key. The only accepted value is `TimeBasedRollingPolicy`; any other value fails the config load.                             |
| `LogFilter.LogLevelByNameConfig.config`     | `Config[LogFilter.LogLevelByNameConfig]`     | Reads `rootLevel` (default `INFO`) and `mappings` (default empty).                                                                            |
| `LogFilter.LogLevelByNameConfig.equal`      | `Equal[LogFilter.LogLevelByNameConfig]`      | Structural equality of the root level and the mappings.                                                                                       |

`FileLoggerConfig` is defined on every platform, but `FileLoggerConfig.config` turns the `path` value into a file path with `java.nio.file.Paths`, which Scala.js does not provide. File configuration is therefore usable on the JVM and Scala Native only, the same platforms that have the file loggers.

To read the console logger settings as one field of an application config, nest the descriptor under the key you choose:

```scala
import zio.{ Config, ZIO }
import zio.logging.ConsoleLoggerConfig

case class AppConfig(port: Int, logging: ConsoleLoggerConfig)

val appConfig: Config[AppConfig] =
  (Config.int("port") ++ ConsoleLoggerConfig.config.nested("logging")).map { case (port, logging) =>
    AppConfig(port, logging)
  }

val loadAppConfig: ZIO[Any, Config.Error, AppConfig] = ZIO.config(appConfig)
```

With this descriptor, the logger keys are `logging.format`, `logging.filter.rootLevel` and `logging.filter.mappings`, next to the application's own `port` key.

## Log Level Names

Every level key accepts the names below. ZIO's `Config.logLevel` reader accepts them case-insensitively, so `info` and `INFO` both parse.

| Name    | Level       |
| ------- | ----------- |
| `ALL`   | Every event |
| `TRACE` | Trace       |
| `DEBUG` | Debug       |
| `INFO`  | Info        |
| `WARN`  | Warning     |
| `ERROR` | Error       |
| `FATAL` | Fatal       |
| `OFF`   | No events   |

The name `WARNING` is not accepted. Use `WARN`.

## Complete Example

The following HOCON configures the console logger with a custom level for one package. Only the keys in the tables above appear:

```hocon
logger {
  format = "%timestamp %level %name %message %cause"

  filter {
    rootLevel = INFO

    mappings {
      "zio.logging.example" = "DEBUG"
    }
  }
}
```

The file logger takes the same keys under a separate path, such as `logger-file`, when you pass that path to `FileLoggerConfig.make`:

```hocon
logger-file {
  path = "file:///var/log/app.log"
  charset = "UTF-8"
  autoFlushBatchSize = 100
  bufferedIOSize = 8192

  rollingPolicy {
    type = TimeBasedRollingPolicy
  }

  filter {
    rootLevel = INFO
  }
}
```

This example trades safety for throughput: with `autoFlushBatchSize = 100`, up to 99 lines can still be in the buffer when the application stops, and the logger does not flush them at shutdown.
