# How to Send Java Library Logs Through ZIO Logging

> You want the logs from third-party Java libraries to appear in the same output as your ZIO logs, with the same format and the same level filter. Many libraries log through SLF4J, the common Java logging facade, or through `java.util.logging` (JUL), the logging package that ships with the JDK. Without a bridge, those libraries write to their own backend, so you end up with two formats, two level settings, and logs that a shipper may not collect. ZIO Logging provides `Slf4jBridge` for SLF4J v2 and `JULBridge` for `java.util.logging`. Each one forwards the library's log calls to the ZIO logger and applies a `LogFilter`.

The steps are:

1. Add the bridge modules to your build.
2. Install the SLF4J bridge with a filter from config.
3. Install the SLF4J bridge with a filter in code.
4. Install the JUL bridge, and install both bridges together.

## Prerequisites

Add the bridge modules. The `slf4j2-bridge` module is for SLF4J v2 only. A separate v1 bridge module exists for SLF4J v1, and this guide does not cover it. Add both bridge modules to your build:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging-slf4j2-bridge" % "2.5.4",
  "dev.zio" %% "zio-logging-jul-bridge"    % "2.5.4"
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

We install `Slf4jBridge.init()` next to the console logger. The bridge reads its level from the `logger.filter` path by default, which is the same filter the console logger uses. The `>+>` operator adds the bridge layer while keeping the logger layer available to it. The `Runtime.removeDefaultLoggers` call stops ZIO's default logger from writing the same lines a second time. The program looks like this:

```scala
object Slf4jFromConfigApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger() >+> Slf4jBridge.init()

  override def run: ZIO[Scope, Any, ExitCode] =
    ZIO.logInfo("Service started").as(ExitCode.success)
}
```

`Slf4jBridge.init()` reads the filter from `logger.filter`. To read it from another path, pass the path as a `NonEmptyChunk[String]`, for example `Slf4jBridge.init(Slf4jBridge.logFilterConfigPath)`. The default path is available as the `Slf4jBridge.logFilterConfigPath` value. The config keys are the same as in [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md).

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

The JUL bridge does its own level filtering. JUL normally filters at the logger level, so the bridge turns that off and applies the `LogFilter` itself. Other JUL handlers that are attached to the same logger may therefore receive more lines than before, and that can cost performance.

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

## See also

- [SLF4J v2](./slf4j2.md) for sending ZIO logs to an SLF4J provider.
- [SLF4J v2 Bridge](./slf4j2-bridge.md) for the full reference of the SLF4J bridge layers.
- [JUL Bridge](./jul-bridge.md) for the full reference of the JUL bridge layers.
- [How to Set Log Levels Per Package From Config](./how-to-log-levels-from-config.md) for the filter keys that the bridges read.
- [How to Log JSON](./how-to-json-logs.md) for the JSON format these examples use.
