# How to Send Java Library Logs Through ZIO Logging

> You want the logs from third-party Java libraries to appear in the same output as your ZIO logs, with the same format and the same level filter. Many libraries log through SLF4J, the common Java logging facade, or through `java.util.logging` (JUL), the logging package that ships with the JDK. Without a bridge, those libraries write to their own backend, so you end up with two formats, two level settings, and logs that a shipper may not collect. ZIO Logging provides `Slf4jBridge` for SLF4J v2 and `JULBridge` for `java.util.logging`. Each one forwards the library's log calls to the ZIO logger and applies a `LogFilter`.

The steps are:

1. Add the bridge modules to your build.
2. Install the SLF4J bridge with a filter from config.
3. Install the SLF4J bridge with a filter in code.
4. Install the JUL bridge, and install both bridges together.
5. Check for conflicting logging modules.

## Prerequisites

Add the bridge modules. The `slf4j2-bridge` module is for SLF4J v2 (`slf4j-api` 2.x) only. For libraries on `slf4j-api` 1.7.x, use the [SLF4J v1 bridge](./slf4j1-bridge.md) module, `zio-logging-slf4j-bridge`, instead; its `Slf4jBridge` object has the same layers, so the steps below apply to it unchanged. All bridge modules require Java 17 or later. Add both bridge modules to your build:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.5",
  "dev.zio" %% "zio-logging-jul-bridge"    % "2.5.5"
)
```

:::warning
Do not use `zio-logging-slf4j2` together with the SLF4J bridge. The `zio-logging-slf4j2` module sends ZIO logs to an SLF4J provider, and the bridge sends SLF4J logs to ZIO. Using both creates a loop. Choose the direction you need. For the other direction, see [SLF4J v2](./slf4j2.md).
:::

The examples use these imports:

```scala
import zio.{ ExitCode, LogLevel, Runtime, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer }
import zio.logging.{ ConsoleLoggerConfig, LogFilter, consoleJsonLogger }
import zio.logging.jul.bridge.JULBridge
import zio.logging.slf4j.bridge.Slf4jBridge
```

## Install the SLF4J Bridge With a Filter From Config

We install `Slf4jBridge.init()` next to the console logger. The bridge reads its level from the `logger.filter` path by default, which is the same filter the console logger uses. The order of the layers matters: when the bridge's layer is built, it captures the ZIO runtime with the loggers installed at that moment, and library logs, including those from threads that run no ZIO fiber, go to those loggers. The `>>>` and `>+>` operators build their left side first, so the console logger must stand to the left of the bridge. The `Runtime.removeDefaultLoggers` call stops ZIO's default logger from writing the same lines a second time. The program looks like this:

```scala
object Slf4jFromConfigApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Service started").as(ExitCode.success)
}
```

`Slf4jBridge.init()` reads the filter from `logger.filter`, which is also available as the `Slf4jBridge.logFilterConfigPath` value. When the application keeps its logging settings under another key, pass the path as a `NonEmptyChunk[String]`. The following layer reads both the console logger's settings and the bridge's filter from under `app.logger`:

```scala
import zio.NonEmptyChunk

val appPathLogging =
  Runtime.removeDefaultLoggers >>>
    consoleJsonLogger(NonEmptyChunk("app", "logger")) >+>
    Slf4jBridge.init(NonEmptyChunk("app", "logger", "filter"))
```

The matching HOCON nests the usual `filter` block one level deeper:

```hocon
app {
  logger {
    filter {
      rootLevel = INFO
      mappings {
        "com.example.thirdparty" = WARN
      }
    }
  }
}
```

The config keys are the same as in [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md).

## Install the SLF4J Bridge With a Filter in Code

When the levels are fixed and do not need to change at runtime, pass the `LogFilter` directly. The following filter sets `INFO` as the root level and `WARN` for a noisy third-party package:

```scala
val thirdPartyFilter: LogFilter[Any] =
  LogFilter.logLevelByName[Any](LogLevel.Info, "com.example.thirdparty" -> LogLevel.Warning)
```

We pass this value to `Slf4jBridge.init`:

```scala
object Slf4jFromCodeApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init(thirdPartyFilter)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Service started").as(ExitCode.success)
}
```

The SLF4J library's own code still calls `org.slf4j.LoggerFactory.getLogger(...)`, and it does not change. Only the destination of its log calls changes.

## Install the JUL Bridge

The `JULBridge` layers follow the same pattern as `Slf4jBridge`. `JULBridge.init()` reads the filter from `logger.filter`, and `JULBridge.init(filter)` takes a `LogFilter` in code. The following program forwards a `java.util.logging` warning to the ZIO console:

```scala
object JulApp extends ZIOAppDefault {

  private val legacyLogger = java.util.logging.Logger.getLogger("com.example.legacy")

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> JULBridge.init()

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.succeed(legacyLogger.warning("Connection pool is almost full")).as(ExitCode.success)
}
```

Installing the JUL bridge resets the global JUL configuration. It calls `LogManager#readConfiguration` with the single setting `.level= ALL`, which removes every JUL handler and level configured before, including those from `logging.properties` and the default console handler, and then adds the bridge's handler to the root logger. From then on, JUL itself filters nothing: every JUL logger creates a record for every level, and the bridge's `LogFilter` decides alone what is kept. Any JUL handler added after the bridge receives records of every level. See [What Installing the Bridge Changes in JUL](./jul-bridge.md#what-installing-the-bridge-changes-in-jul) for details.

## Install Both Bridges Together

The two bridge layers can be chained, so that SLF4J and JUL logs go through the same ZIO logger:

```scala
object BothBridgesApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>>
      consoleJsonLogger() >+>
      Slf4jBridge.init(thirdPartyFilter) >+>
      JULBridge.init(thirdPartyFilter)

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Service started").as(ExitCode.success)
}
```

## Check for Conflicting Logging Modules

A bridge reverses the direction of a backend, and SLF4J allows only one provider, so some module combinations do not work together. Before you ship, check the runtime classpath for these cases:

- **The SLF4J backend and the SLF4J bridge together**: `zio-logging-slf4j2` with `zio-logging-slf4j2-bridge`, or `zio-logging-slf4j` with `zio-logging-slf4j-bridge`, send every line round in a loop.
- **Another SLF4J provider next to the bridge**, such as `logback-classic`, `slf4j-simple` or `log4j-slf4j2-impl`: if SLF4J picks the other provider, building the bridge layer fails at startup with a `ClassCastException` defect. Remove the other provider, or with SLF4J 2.0.9 and later, choose the bridge with `-Dslf4j.provider=zio.logging.slf4j.bridge.ZioSLF4JServiceProvider`.
- **The v1 bridge with `slf4j-api` 2.x**: SLF4J 2 ignores the v1 binding and discards library logs, although the bridge layer builds without an error. Use the v2 bridge.
- **The JPL backend and the JUL bridge together**: the JDK's `System.Logger` writes to JUL by default, so the [JPL backend](./jpl.md) and the JUL bridge send every line round in a loop.

## See also

- [SLF4J v2](./slf4j2.md) for sending ZIO logs to an SLF4J provider.
- [SLF4J v2 Bridge](./slf4j2-bridge.md) and [SLF4J v1 Bridge](./slf4j1-bridge.md) for the full reference of the SLF4J bridge layers.
- [JUL Bridge](./jul-bridge.md) for the full reference of the JUL bridge layers.
- [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md) for the filter keys that the bridges read.
- [How to Log JSON](./how-to-json-logs.md) for the JSON format these examples use.
