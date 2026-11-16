# Configuration Keys Reference

> Each built-in logger reads its settings from a configuration source through ZIO Config. This page lists every key for the console logger, the file logger, and the level filter they share. The keys are relative to a root path, which is `logger` by default. Each feature has its own page with examples: [Console Logger](./console-logger.md), [File Logger](./file-logger.md), and [Log Filter](./log-filter.md).

## Default Config Path

The default root path lives in `zio.logging.loggerConfigPath`, as the single segment `logger`. The `ConsoleLoggerConfig.load`, `ConsoleLoggerConfig.make`, `FileLoggerConfig.load`, and `FileLoggerConfig.make` functions, and the logger layers such as `LoggerLayers.consoleLogger`, read from this path unless you pass another path. The source declares the value as follows:

```scala
val loggerConfigPath: NonEmptyChunk[String] = NonEmptyChunk("logger")
```

A custom path is a `NonEmptyChunk[String]`. The first element is the top-level key, and the remaining elements are nested keys under it. For example, `NonEmptyChunk("app", "logging")` reads the keys from `app.logging`.

## Console Logger Keys

The console logger config is `zio.logging.ConsoleLoggerConfig`. It has a format and a level filter. The table lists its keys under the root path.

| Key                  | Type                             | Default           | Meaning                                                                                              |
| -------------------- | -------------------------------- | ----------------- | ---------------------------------------------------------------------------------------------------- |
| `format`             | Format string                    | `LogFormat.default` | The layout of each line. The string form is described in [Formatting Log Records](./formatting-log-records.md). |
| `filter.rootLevel`   | Log level name                   | `INFO`            | The minimum level for every logger name that has no mapping.                                         |
| `filter.mappings`    | Table of logger name to log level | Empty table       | Minimum levels for logger names. A key is a dot-separated name prefix, and it applies to all names under it. |

## File Logger Keys

The file logger config is `zio.logging.FileLoggerConfig`. It adds a destination path and the options for writing and rolling the file. The table lists its keys under the root path.

| Key                  | Type                             | Default             | Meaning                                                                                                                                  |
| -------------------- | -------------------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `path`               | URI string, such as `file:///var/log/app.log` | None, required | The file to write. The value is read with `URI.create`, so it must be a URI, not a plain path. An invalid value fails the config load. |
| `format`             | Format string                    | `LogFormat.default` | The layout of each line. See [Formatting Log Records](./formatting-log-records.md).                                                         |
| `filter.rootLevel`   | Log level name                   | `INFO`              | The minimum level for every logger name that has no mapping.                                                                             |
| `filter.mappings`    | Table of logger name to log level | Empty table         | Minimum levels for logger names, with the same rules as the console logger.                                                              |
| `charset`            | Charset name, such as `UTF-8`    | `UTF-8`             | The character encoding of the file. The name is read with `Charset.forName`, and an unknown name fails the config load.                  |
| `autoFlushBatchSize` | Integer                          | `1`                 | The number of lines written before the writer flushes its buffer. The value `1` flushes after every line.                                |
| `bufferedIOSize`     | Integer                          | None, unbuffered    | When set, the writer wraps the file in a `java.io.BufferedWriter` with a buffer of this many characters.                                 |
| `rollingPolicy.type` | Rolling policy name              | None, no rolling    | When set, the file rolls over with the rolling policy. The only supported value is `TimeBasedRollingPolicy`.                              |

The `rollingPolicy.type` key is optional. When the key is absent, the file is not rolled.

## Log Level Filter Keys

The level filter config is `zio.logging.LogFilter.LogLevelByNameConfig`. The console and file loggers read it under the `filter` key, so the full keys are `filter.rootLevel` and `filter.mappings`. The class and its helper methods are described in [LogFilter and LogGroup Reference](./reference-log-filter.md#loglevelbynameconfig).

| Key          | Type                              | Default     | Meaning                                                                                                                                      |
| ------------ | --------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `rootLevel`  | Log level name                    | `INFO`      | The minimum level for every event that matches no mapping.                                                                                   |
| `mappings`   | Table of logger name to log level | Empty table | Minimum levels for logger names. Dot-separated names select the mapping. The matching rules are described in [Log Filter](./log-filter.md). |

## Log Level Names

Every level key accepts the names below. ZIO's `Config.logLevel` reader accepts them case-insensitively, so `info` and `INFO` both parse.

| Name    | Level            |
| ------- | ---------------- |
| `ALL`   | Every event      |
| `TRACE` | Trace            |
| `DEBUG` | Debug            |
| `INFO`  | Info             |
| `WARN`  | Warning          |
| `ERROR` | Error            |
| `FATAL` | Fatal            |
| `OFF`   | No events        |

The name `WARNING` is not accepted. Use `WARN`.

## Complete Example

The following HOCON configures the console logger with a custom level for one package. Only the keys in the tables above appear:

```
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

```
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
