---
id: zio-logging
title: "ZIO Logging"
---

[ZIO Logging](https://github.com/zio/zio-logging) is simple logging for ZIO apps, with correlation, context, and pluggable backends out of the box.

## Introduction

When we are writing our applications using ZIO effects, to log easy way we need a ZIO native solution for logging. ZIO Logging is an environmental effect for adding logging into our ZIO applications.

Key features of ZIO Logging:

- **ZIO Native** — Other than it is a type-safe and purely functional solution, it leverages ZIO's features.
- **Multi-Platform** - It supports both JVM and JS platforms.
- **Composable** — Loggers are composable together via contraMap.
- **Pluggable Backends** — Support multiple backends like ZIO Console, SLF4j, JS Console, JS HTTP endpoint.
- **Logger Context** — It has a first citizen _Logger Context_ implemented on top of `FiberRef`. The Logger Context maintains information like logger name, filters, correlation id, and so forth across different fibers. It supports _Mapped Diagnostic Context (MDC)_ which manages contextual information across fibers in a concurrent environment.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-logging" % "0.5.14" 
```

There are also some optional dependencies:
- **zio-logging-slf4j** — SLF4j integration
- **zio-logging-slf4j-bridge** — Using ZIO Logging for SLF4j loggers, usually third-party non-ZIO libraries
- **zio-logging-jsconsole** — Scala.js console integration
- **zio-logging-jshttp** — Scala.js HTTP Logger which sends logs to a backend via Ajax POST

## Example

Let's try an example of ZIO Logging which demonstrates a simple application of ZIO logging along with its _Logger Context_ feature:

```scala
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging._
import zio.random.Random
import zio.{ExitCode, NonEmptyChunk, ZIO}

object ZIOLoggingExample extends zio.App {

  val myApp: ZIO[Logging with Clock with Random, Nothing, Unit] =
    for {
      _ <- log.info("Hello from ZIO logger")
      _ <-
        ZIO.foreachPar(NonEmptyChunk("UserA", "UserB", "UserC")) { user =>
          log.locally(UserId(Some(user))) {
            for {
              _ <- log.info("User validation")
              _ <- zio.random
                .nextIntBounded(1000)
                .flatMap(t => ZIO.sleep(t.millis))
              _ <- log.info("Connecting to the database")
              _ <- zio.random
                .nextIntBounded(100)
                .flatMap(t => ZIO.sleep(t.millis))
              _ <- log.info("Releasing resources.")
            } yield ()
          }

        }
    } yield ()

  type UserId = String
  def UserId: LogAnnotation[Option[UserId]] = LogAnnotation[Option[UserId]](
    name = "user-id",
    initialValue = None,
    combine = (_, r) => r,
    render = _.map(userId => s"[user-id: $userId]")
      .getOrElse("undefined-user-id")
  )

  val env =
    Logging.console(
      logLevel = LogLevel.Info,
      format =
        LogFormat.ColoredLogFormat((ctx, line) => s"${ctx(UserId)} $line")
    ) >>> Logging.withRootLoggerName("MyZIOApp")

  override def run(args: List[String]) =
    myApp.provideCustom(env).as(ExitCode.success)
}
```

The output should be something like this:

```
2021-07-09 00:14:47.457+0000  info [MyZIOApp] undefined-user-id Hello from ZIO logger
2021-07-09 00:14:47.807+0000  info [MyZIOApp] [user-id: UserA] User validation
2021-07-09 00:14:47.808+0000  info [MyZIOApp] [user-id: UserC] User validation
2021-07-09 00:14:47.818+0000  info [MyZIOApp] [user-id: UserB] User validation
2021-07-09 00:14:48.290+0000  info [MyZIOApp] [user-id: UserC] Connecting to the database
2021-07-09 00:14:48.299+0000  info [MyZIOApp] [user-id: UserA] Connecting to the database
2021-07-09 00:14:48.321+0000  info [MyZIOApp] [user-id: UserA] Releasing resources.
2021-07-09 00:14:48.352+0000  info [MyZIOApp] [user-id: UserC] Releasing resources.
2021-07-09 00:14:48.820+0000  info [MyZIOApp] [user-id: UserB] Connecting to the database
2021-07-09 00:14:48.882+0000  info [MyZIOApp] [user-id: UserB] Releasing resources.
```
