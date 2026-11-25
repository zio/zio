# How to Change Log Levels at Runtime

> You want to turn on `DEBUG` output for a running service while you chase a problem, and turn it off again afterwards, without a restart. ZIO Logging does this with `ReconfigurableLogger`, a logger that forwards every log call to an inner logger that can be swapped while the program runs.

In this guide, we cover the two ways to swap it. In the first, the logger polls a configuration file on a schedule and rebuilds itself when the file changes. In the second, your own code pushes a new configuration, for example from an admin endpoint. We finish with the cases where today's behaviour can surprise you.

The steps are:

1. Load the logger configuration from a file that can change.
2. Install a logger that polls that configuration.
3. Keep polling after a bad edit.
4. Push a new level from your own code instead.
5. Know what is not reloaded or closed.

## The Problem

A logger built in the `bootstrap` layer from a config file reads the file once, at startup. Consider a service that runs with the root level at `INFO`. A customer reports an error that you cannot reproduce, and the lines that would explain it are at `DEBUG`. With a logger that reads its configuration once, you have to edit the file and restart the service, which drops the in-flight requests and may make the problem go away before you see it. Then you restart again to turn `DEBUG` off, because leaving it on fills the disk.

What you want is to edit the level, see `DEBUG` lines within seconds, and edit it back, while the service keeps running.

## Prerequisites

Add the core module, and `zio-config-typesafe` to read HOCON files. The examples read a file from disk, so they target the JVM:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-logging"         % "2.5.5",
  "dev.zio" %% "zio-config-typesafe" % "4.1.2"
)
```

The examples use these imports:

```scala
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.{ ConsoleLoggerConfig, ReconfigurableLogger, makeConsoleLogger }
```

This guide assumes you know the `logger` configuration block from [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md).

## Load the Configuration From a File That Can Change

The reload works by running a load effect again and again, so that effect must read the source each time it runs. We use `TypesafeConfigProvider.fromHoconFilePathZIO`, which reads and parses the file inside the effect, and then load a `ConsoleLoggerConfig` from it with `ConsoleLoggerConfig.load`:

```scala
val loggerConfPath: String = "/etc/orders/logger.conf"

val loadLoggerConfig: ZIO[Any, Throwable, ConsoleLoggerConfig] =
  TypesafeConfigProvider
    .fromHoconFilePathZIO(loggerConfPath)
    .flatMap(provider => ConsoleLoggerConfig.load().withConfigProvider(provider))
```

The file holds the usual `logger` block:

```hocon
logger {
  filter {
    rootLevel = INFO
    mappings {
      "com.example.orders" = INFO
    }
  }
}
```

A config provider that is built once, outside the effect, would return the same values on every load. For example, `TypesafeConfigProvider.fromHoconFilePath` without the `ZIO` suffix parses the file when it is called, so wrapping its result in the load effect does not pick up later edits.

## Install a Logger That Polls the Configuration

`ReconfigurableLogger.make` takes three arguments: the load effect, a function that builds a logger from a loaded configuration, and the schedule for reloading. It loads the configuration and builds the first logger at once. Then it starts a background fiber that, on each step of the schedule, loads the configuration again and, if it differs from the current one, builds a new logger and swaps it in. We build console loggers with `makeConsoleLogger` and poll every 10 seconds, which is also the default schedule:

```scala
val reloadingLogger: ZLayer[Any, Throwable, Unit] =
  ReconfigurableLogger
    .make[Any, Throwable, String, Any, ConsoleLoggerConfig](
      loadLoggerConfig,
      (config, _) => makeConsoleLogger(config),
      Schedule.fixed(10.seconds)
    )
    .installUnscoped[Any]
```

The second argument of the build function is the logger that is being replaced: `None` on the first build, and `Some(previous)` on later ones. Ignore it unless your new logger needs something from the old one.

`ReconfigurableLogger.make` returns a scoped effect, and the `installUnscoped` syntax turns it into a layer that installs the logger and stops the reload fiber when the layer is released. See [Logger Layers](reference-logger-layers.md) for the other `install` variants and the other `make*` constructors. The application installs the layer in `bootstrap`, in place of the default logger:

```scala
object OrdersApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> reloadingLogger

  override def run: ZIO[Any, Any, Any] =
    (ZIO.logDebug("Polling the order queue") *> ZIO.logInfo("Orders processed"))
      .repeat(Schedule.spaced(2.seconds))
}
```

With `rootLevel = INFO`, only `Orders processed` lines appear. Change `rootLevel` to `DEBUG` and save the file; within one schedule step, about 10 seconds here, the `Polling the order queue` lines start to appear. Change it back to `INFO` and they stop. The first reload happens one schedule step after startup, not at startup.

The loaded configuration is compared with the current one through its `zio.prelude.Equal` instance, which is why `ReconfigurableLogger.make` needs a `C: Equal`. `ConsoleLoggerConfig` and `FileLoggerConfig` both provide one. When the two are equal, nothing is rebuilt, so polling an unchanged file costs one file read per step. One exception: the format part is compared with `==`, and a `format` key that contains `%timestamp{...}` with a pattern creates a new `DateTimeFormatter` on each load, which never equals the previous one. With such a key, the logger is rebuilt on every step. That is harmless for a console logger, but see the note on replaced loggers below.

## Keep Polling After a Bad Edit

The reload fiber stops at the first failed load. If someone saves the file with a HOCON syntax error or an unknown level name, the load fails, the background fiber ends, and nothing reports it. The logger keeps the last configuration it had, but no later edit is picked up until the service restarts.

A missing file is different: the Typesafe parser reads a missing file as an empty one by default, so the load succeeds with the default configuration (root level `INFO`, no mappings) and the logger switches to it.

To keep polling, make the load effect itself never fail: remember the last configuration that loaded, and return it when a load fails. The startup load still fails loudly, because a service that cannot read its logging configuration at startup should not start:

```scala
val resilientLogger: ZLayer[Any, Throwable, Unit] =
  ZLayer.scoped[Any] {
    for {
      lastGood <- loadLoggerConfig.flatMap(config => Ref.make(config))
      safeLoad  = loadLoggerConfig.tap(config => lastGood.set(config)).orElse(lastGood.get)
      logger   <- ReconfigurableLogger.make[Any, Throwable, String, Any, ConsoleLoggerConfig](
                    safeLoad,
                    (config, _) => makeConsoleLogger(config),
                    Schedule.fixed(10.seconds)
                  )
      _        <- ZIO.withLoggerScoped(logger)
    } yield ()
  }
```

A failed load now returns the configuration that is already in use, so the comparison finds no change and the loop goes on to the next step. The same rule applies to the build function: if it fails, the loop stops too. `makeConsoleLogger` cannot fail, so this example is not affected.

## Push a New Level From Your Own Code

Polling suits a level kept in a file. When the change comes from an operator instead, through an admin endpoint, a command, or a message, push it directly. `ReconfigurableLogger.apply` creates the logger from a configuration and a first logger, with no polling. `ReconfigurableLogger#get` returns the current configuration and logger, and `ReconfigurableLogger#set` replaces both.

We create the logger in a layer that both installs it and returns it, so the rest of the program can reach it as a service:

```scala
type AdminLogger = ReconfigurableLogger[String, Any, ConsoleLoggerConfig]

val adminLogger: ZLayer[Any, Nothing, AdminLogger] =
  ZLayer.scoped[Any] {
    for {
      initial <- makeConsoleLogger(ConsoleLoggerConfig.default)
      logger   = ReconfigurableLogger[String, Any, ConsoleLoggerConfig](ConsoleLoggerConfig.default, initial)
      _       <- ZIO.withLoggerScoped(logger)
    } yield logger
  }
```

The `setLevel` function is what an admin endpoint calls. It reads the current configuration, changes the root level or the level of one logger name, builds a logger for the new configuration, and swaps it in:

```scala
def setLevel(logger: AdminLogger, loggerName: Option[String], level: LogLevel): UIO[Unit] =
  ZIO.suspendSucceed {
    val (current, _) = logger.get
    val filter       = loggerName match {
      case Some(name) => current.filter.withMapping(name, level)
      case None       => current.filter.withRootLevel(level)
    }
    val updated      = current.copy(filter = filter)
    makeConsoleLogger(updated).map(newLogger => logger.set(updated, newLogger))
  }
```

The program gets the logger from the environment and calls `setLevel`. In a real service, the call sits in the handler of your HTTP route or command; here it runs between two log calls, so the effect is visible:

```scala
object AdminApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers

  val program: ZIO[AdminLogger, Nothing, Unit] =
    for {
      logger <- ZIO.service[AdminLogger]
      _      <- ZIO.logDebug("Not written: the root level is INFO")
      _      <- setLevel(logger, None, LogLevel.Debug)
      _      <- ZIO.logDebug("Written: the root level is now DEBUG")
    } yield ()

  override def run: ZIO[Any, Any, Any] =
    program.provideLayer(adminLogger)
}
```

`ReconfigurableLogger#get` and `ReconfigurableLogger#set` are separate steps, so two calls to `setLevel` at the same moment can lose one of the changes. If several operators can call it at once, run the calls one at a time, for example behind a `Semaphore` with one permit.

The [Reconfigurable Logger](reconfigurable-logger.md) page shows a complete example with ZIO HTTP endpoints, built on a filter that can be changed in place.

## Know What Is Not Reloaded or Closed

A few parts of today's behaviour matter when you rely on runtime changes:

- When a new logger is swapped in, the old one is dropped without being closed. For console loggers this does not matter. For a file logger, the old logger's file stays open, so each rebuild with a different configuration leaves one more open file until the program ends.
- Only the logger built by the build function changes. The SLF4J and JUL bridges read their own filter once, when the bridge starts, and keep it. Lowering the level of the ZIO logger to `DEBUG` does not let through Java `DEBUG` lines that the bridge filter drops; see [How to Send Java Library Logs Through ZIO Logging](how-to-bridge-java-logs.md).
- Every log call first reads the current inner logger from an atomic reference, which adds a small cost per call. The [Reconfigurable Logger](reconfigurable-logger.md) page links benchmarks if this matters for your service.

## See also

- [Reconfigurable Logger](reconfigurable-logger.md) for the `ReconfigurableLogger` API and complete example applications.
- [Logger Layers](reference-logger-layers.md) for `makeConsoleLogger`, the file logger constructors, and the `install` syntax.
- [How to Set Log Levels Per Package From Config](how-to-log-levels-from-config.md) for the `logger.filter` keys.
- [Log Filter](log-filter.md) for how logger names are matched against the mappings.
