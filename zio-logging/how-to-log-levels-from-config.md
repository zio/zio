# How to Set Log Levels Per Package From Config

> You want to change how much a running application logs, for example to turn on `DEBUG` output for one package in production while keeping `INFO` for everything else. If the levels are written into Scala code, every change needs a rebuild and a redeploy. ZIO Logging can instead read a root level and a map of logger names to levels from a configuration file, so you change the file and restart the application.

In this guide, we write the levels in a HOCON file (HOCON is a configuration format with braces, unquoted keys, and optional quotes), load the file with `TypesafeConfigProvider` from the `zio-config-typesafe` module, and build a console logger from it. A logger name is the package or class name that a log line came from, and a per-name level applies to every logger whose name starts with that prefix.

The steps are:

1. Understand the filter model in code.
2. Write the levels in a HOCON file.
3. Load the file in the `bootstrap` layer.
4. Check which lines are written.

## Prerequisites

Add the core module and the Typesafe config module to your build. The Typesafe config module belongs to ZIO Config, which has its own version numbers, so it does not take the ZIO Logging version:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging"            % "2.5.5",
  "dev.zio" %% "zio-config-typesafe"    % "4.1.2"
)
```

The examples use these imports:

```scala
import zio.{ ConfigProvider, ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppDefault, ZLayer, ZIOAppArgs, Config }
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ LogFilter, consoleLogger, loggerName }
```

## Understand the Filter Model

The filter is a `LogFilter.LogLevelByNameConfig`, which holds two things: a `rootLevel` that applies when no name matches, and a `mappings` map from a name prefix to a level. The following value sets `INFO` as the root level, and sets `DEBUG` for the payments package and `ERROR` for one noisy client:

```scala
LogFilter.LogLevelByNameConfig(
  LogLevel.Info,
  "com.example.payments"      -> LogLevel.Debug,
  "com.example.legacy.client" -> LogLevel.Error
)
// res0: LogFilter.LogLevelByNameConfig = LogLevelByNameConfig(
//   rootLevel = LogLevel(ordinal = 20000, label = "INFO", syslog = 6),
//   mappings = Map(
//     "com.example.payments" -> LogLevel(ordinal = 10000, label = "DEBUG", syslog = 7),
//     "com.example.legacy.client" -> LogLevel(
//       ordinal = 40000,
//       label = "ERROR",
//       syslog = 3
//     )
//   )
// )
```

When a log line is written, its logger name is matched against the mapping keys one dot-separated segment at a time, so `com.example.payments` matches `com.example.payments.PaymentService` but not `com.example.paymentsX`. The most specific matching key wins, and if no key matches, the root level applies. The logger name comes from a log annotation if one is set, and otherwise from the location in the code where the log call was made.

## Write the Levels in a Config File

We write the same values in HOCON under the `logger` key. The `filter` block holds the root level and the mappings. The mapping keys are quoted, because a package name contains dots that HOCON would otherwise treat as nesting. The file looks like this:

```hocon
logger {
  filter {
    rootLevel = INFO
    mappings {
      "com.example.payments" = DEBUG
      "com.example.legacy.client" = ERROR
    }
  }
}
```

Level names are the ZIO labels: `ALL`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`, and `OFF`. When `rootLevel` is missing, it defaults to `INFO`. When `mappings` is missing, no per-name level is set. For every key in the console and file configuration, see [Log Filter](./log-filter.md#configuration) and [Console Logger](./console-logger.md).

## Load the Config in the Bootstrap Layer

We pass the HOCON text to `TypesafeConfigProvider.fromHoconString`, and install it with `Runtime.setConfigProvider`. The `consoleLogger()` layer then reads the `logger` key from that provider. The layer's error type is `Config.Error`, so a missing or invalid value is reported as a configuration error when the program starts. The application installs the layer as follows:

```scala
object LevelsFromConfigApp extends ZIOAppDefault {

  val hocon: String =
    """logger {
      |  filter {
      |    rootLevel = INFO
      |    mappings {
      |      "com.example.payments" = DEBUG
      |      "com.example.legacy.client" = ERROR
      |    }
      |  }
      |}
      |""".stripMargin

  override val bootstrap: ZLayer[ZIOAppArgs, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>>
      Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconString(hocon)) >>>
      consoleLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    (for {
      _ <- ZIO.logDebug("Charging card") @@ loggerName("com.example.payments")
      _ <- ZIO.logDebug("Cache miss") @@ loggerName("com.example.catalog")
      _ <- ZIO.logInfo("Checkout finished") @@ loggerName("com.example.checkout")
    } yield ExitCode.success)
}
```

The `loggerName` aspect sets the logger name for a block of effects, so this example does not need to live in the package it is testing.

## Check Which Lines Are Written

With the config above, the program writes two lines and drops one:

- `Charging card` is written, because `com.example.payments` maps to `DEBUG`.
- `Cache miss` is dropped, because `com.example.catalog` matches no mapping, so the root level `INFO` applies and `DEBUG` is too low.
- `Checkout finished` is written, because `INFO` meets the root level.

To change the verbosity, edit the HOCON file and restart the application. The `bootstrap` layer runs once at startup, so the new levels take effect on the next start, not while the program runs.

:::warning
The same filter applies to every logger that the console or file logger writes, including logs from SLF4J and `java.util.logging` libraries when they are bridged. The bridges read their level from the `logger.filter` path by default. See [How to Send Java Library Logs Through ZIO Logging](./how-to-bridge-java-logs.md).
:::

## See also

- [Log Filter](./log-filter.md) for the full list of filter keys and the `LogFilter` API.
- [Console Logger](./console-logger.md) for the console logger keys and the `consoleLogger` layers.
- [How to Log JSON](./how-to-json-logs.md) for writing the filtered output as JSON.
- [How to Log to a File With Rotation](./how-to-file-logging-rotation.md) for applying the same configuration to a file.
