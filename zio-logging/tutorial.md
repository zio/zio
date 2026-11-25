# Tutorial: ZIO Logging Basics

> Welcome to the ZIO Logging tutorial! This tutorial is for developers who are new to ZIO Logging. You should know the basics of ZIO, such as effects (values of type `ZIO` that describe work to run), `for` comprehensions, and `ZIOAppDefault`. You don't need any experience with logging libraries.

## Learning Objectives

By the end of this tutorial, you will understand:

- How to add ZIO Logging to a project and install a console logger
- How the log levels `Debug`, `Info`, `Warning`, and `Error` affect what gets printed
- How to attach log annotations (extra key-value data) to a group of log messages
- How to switch the output from plain text to JSON

We learn these concepts through the following steps:

1. Add the Dependency
2. Print Your First Log Message
3. Choose a Log Level
4. Attach Log Annotations
5. Print JSON Output
6. Filter by Logger Name

We recommend reading from top to bottom. Each step changes the same program a little, so the steps build on each other.

Some terms come up often in this tutorial. A **logger** is the part of your program that writes messages to a destination, such as the terminal. A **fiber** is a lightweight thread that ZIO uses to run your effects. Every log line records the fiber that wrote it. A **bootstrap** is a layer that ZIO builds and runs before your main program starts. We use the bootstrap to install the logger. A **layer** (`ZLayer`) is a recipe that builds a service your program needs.

## 1. Add the Dependency

We start by adding the ZIO Logging library to an sbt project. Add this line to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "2.5.5"
```

The line tells sbt to download the `zio-logging` library. The library depends on ZIO, so the same line also brings ZIO into your project.

This tutorial runs on the JVM. The core module is also published for Scala.js and Scala Native; in such a project, write `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-logging" % "2.5.5"
```

## 2. Print Your First Log Message

We write a small program that logs two messages. The program uses `ZIO.logInfo` to write a message at the `Info` level. The `bootstrap` value installs the console logger before the program starts:

```scala
import zio._
import zio.logging.consoleLogger

object TutorialApp extends ZIOAppDefault {

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger()

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logInfo("Application started")
      _ <- ZIO.logInfo("Application finished")
    } yield ExitCode.success
}
```

The code above does the following:

- `import zio.logging.consoleLogger` brings the console logger constructor into scope.
- `Runtime.removeDefaultLoggers` removes ZIO's built-in logger, so the console logger is the only logger that prints.
- `>>> consoleLogger()` chains the removal into a layer that installs the console logger. The empty parentheses use the default configuration.
- `ZIO.logInfo("Application started")` writes one log message at the `Info` level.

When you run this program, each `ZIO.logInfo` call prints one line. The shape of each line looks like this. The timestamp and the fiber name change on every run, so this is an illustrative shape, not exact output:

```text
timestamp=<time> level=INFO thread=<fiber> message="Application started"
timestamp=<time> level=INFO thread=<fiber> message="Application finished"
```

Each line has four parts: a timestamp, the level, the fiber, and the message. This layout is the default format, `LogFormat.default`.

## 3. Choose a Log Level

A log level tells you how serious a message is. ZIO Logging has four levels that you use most often:

- `ZIO.logDebug` writes detailed messages for developers.
- `ZIO.logInfo` writes normal progress messages.
- `ZIO.logWarning` writes messages about a problem that the program can recover from.
- `ZIO.logError` writes messages about a failure.

The console logger prints only messages at or above its root level. The default root level is `Info`, so `ZIO.logDebug` messages are hidden by default. To see them, we build a `ConsoleLoggerConfig` with a root level of `Debug`. We use `LogFilter.LogLevelByNameConfig.default` and change its root level with `withRootLevel`:

```scala
import zio._
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, LogFilter, LogFormat }

object TutorialApp extends ZIOAppDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(
      LogFormat.default,
      LogFilter.LogLevelByNameConfig.default.withRootLevel(LogLevel.Debug)
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(config)

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- ZIO.logDebug("Loading settings")
      _ <- ZIO.logInfo("Application started")
      _ <- ZIO.logWarning("Cache is almost full")
      _ <- ZIO.logError("Payment service is unreachable")
    } yield ExitCode.success
}
```

The new `config` value replaces the empty parentheses from step 2. The `consoleLogger(config)` call accepts a `ConsoleLoggerConfig` directly. The four log calls cover all four levels. Because the root level is `Debug`, all four messages are printed. The output has one line per call, with the level changed on each line:

```text
timestamp=<time> level=DEBUG thread=<fiber> message="Loading settings"
timestamp=<time> level=INFO thread=<fiber> message="Application started"
timestamp=<time> level=WARN thread=<fiber> message="Cache is almost full"
timestamp=<time> level=ERROR thread=<fiber> message="Payment service is unreachable"
```

:::note
Set the root level to `Info` again and the `DEBUG` line disappears. The other three lines stay. The level is a threshold, not a switch for one message at a time.
:::

## 4. Attach Log Annotations

A log annotation is extra data that you attach to log messages. An annotation is a key and a value, such as `user_id` and `user-42`. Annotations are useful when many messages belong to one request, because each message carries the same context. We attach annotations with the `@@` operator. `LogAnnotation.UserId` is a built-in annotation for user ids. `ZIOAspect.annotated` creates an annotation from any string key and value.

The program below handles one request. Both messages inside `handleRequest` get the two annotations. The last message in `run` does not:

```scala
import zio._
import zio.logging.{ consoleLogger, ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat }

object TutorialApp extends ZIOAppDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(
      LogFormat.default |-| LogFormat.allAnnotations(Set.empty[String]),
      LogFilter.LogLevelByNameConfig.default.withRootLevel(LogLevel.Debug)
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleLogger(config)

  private def handleRequest(userId: String): UIO[Unit] =
    (ZIO.logInfo("Request received") *> ZIO.logInfo("Request finished")) @@
      LogAnnotation.UserId(userId) @@
      ZIOAspect.annotated("request_id", "req-7")

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- handleRequest("user-42")
      _ <- ZIO.logInfo("Application finished")
    } yield ExitCode.success
}
```

The code above does the following:

- `LogFormat.allAnnotations(Set.empty[String])` adds the annotations to each line. The default format does not print them, so we append this format with `|-|`. The empty set means no annotation key is hidden.
- `handleRequest` uses `*>` to run two log calls one after the other. The `@@` operator then applies the annotations to that whole group.
- `LogAnnotation.UserId(userId)` attaches the user id. `ZIOAspect.annotated("request_id", "req-7")` attaches a request id.
- The last log call in `run` is outside `handleRequest`, so it has no annotations.

The output shows the annotations after the message on the lines from `handleRequest`. The spacing and the exact order of fields are illustrative. The rest of each line follows the step 3 pattern:

```text
timestamp=<time> level=INFO thread=<fiber> message="Request received" user_id=user-42 request_id=req-7
timestamp=<time> level=INFO thread=<fiber> message="Request finished" user_id=user-42 request_id=req-7
timestamp=<time> level=INFO thread=<fiber> message="Application finished"
```

Annotations stop at the end of the effect they wrap. This is why the last line has none.

## 5. Print JSON Output

Many log systems read JSON, a text format for structured data. Instead of `consoleLogger`, we use `consoleJsonLogger`. It takes the same `ConsoleLoggerConfig` and prints each line as a JSON object. We change only the bootstrap line and keep the rest of the program from step 4:

```scala
import zio._
import zio.logging.{ consoleJsonLogger, ConsoleLoggerConfig, LogAnnotation, LogFilter, LogFormat }

object TutorialApp extends ZIOAppDefault {

  private val config: ConsoleLoggerConfig =
    ConsoleLoggerConfig(
      LogFormat.default |-| LogFormat.allAnnotations(Set.empty[String]),
      LogFilter.LogLevelByNameConfig.default.withRootLevel(LogLevel.Debug)
    )

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(config)

  private def handleRequest(userId: String): UIO[Unit] =
    (ZIO.logInfo("Request received") *> ZIO.logInfo("Request finished")) @@
      LogAnnotation.UserId(userId) @@
      ZIOAspect.annotated("request_id", "req-7")

  override def run: ZIO[Scope, Any, ExitCode] =
    for {
      _ <- handleRequest("user-42")
      _ <- ZIO.logInfo("Application finished")
    } yield ExitCode.success
}
```

The only change is `consoleJsonLogger(config)` in the bootstrap. Each log line is now one JSON object. The fields come from the same format as the text output. The exact key layout is illustrative, so the sample below shows only the shape:

```text
{"timestamp":"<time>","level":"INFO","thread":"<fiber>","message":"Request received","user_id":"user-42","request_id":"req-7"}
```

:::tip
JSON output works well with log collectors because each line is one complete record. If you need a different layout, change the `LogFormat` value. You don't need to change the logger.
:::

## 6. Filter by Logger Name

Each log line has a logger name. ZIO Logging takes the name from the code location, or you can set it with the `loggerName` aspect. A filter can then set a different level for each name. A name matches its own name and any name below it, with dots as separators. For example, `"payments" -> LogLevel.Debug` prints debug messages from `payments` and `payments.checkout`, while the root level still applies to every other name.

The snippet below sets the root level to `Info` and sets `payments` to `Debug`:

```scala
import zio.LogLevel
import zio.logging.{ ConsoleLoggerConfig, LogFilter, LogFormat }

val filteredConfig: ConsoleLoggerConfig =
  ConsoleLoggerConfig(
    LogFormat.default,
    LogFilter.LogLevelByNameConfig(LogLevel.Info, "payments" -> LogLevel.Debug)
  )
```

To learn the full set of filter options, read the [Log Filter](./log-filter.md) page.

## What You've Learned

In this tutorial, you learned:

- How to add `zio-logging` to an sbt project and install a console logger in the bootstrap
- How the four log levels work, and how the root level hides messages below it
- How to attach annotations to a group of log messages with `@@`
- How to print JSON output with `consoleJsonLogger`
- How to set a different log level for one logger name

You now have a foundation for logging in a ZIO application. The next step is to change the output format and the destination to suit your project.

## Next Steps

- **Change the console output:** Read [Console Logger](./console-logger.md) for every option of the console logger.
- **Shape the log lines:** Read [Formatting Log Records](./formatting-log-records.md) to build your own format.
- **Control which messages print:** Read [Log Filter](./log-filter.md) for the full filter API.
- **Learn about annotations in depth:** Read [Logger Context and Annotations](./logger-context-and-annotations.md).
- **Send SLF4J logs through ZIO Logging:** Read [SLF4J 2](./slf4j2.md) if your project uses SLF4J.
