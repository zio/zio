---
id: enable-logging-in-a-zio-application
title: "Tutorial: How to Enable Logging in a ZIO Application"
sidebar_label: "Enable Logging in a ZIO Application"
---

## Introduction

ZIO has built-in support for logging. This tutorial will show you how to enable logging for a ZIO application.

## Default Logger

ZIO has a default logger that prints any log messages that are equal, or above the `Level.Info` level:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.log("Application started")
}
```

This will print the following message to the console:

```scala
timestamp=2022-06-01T09:43:08.848398Z level=INFO thread=#zio-fiber-6 message="Application started" location=zio.examples.MainApp.run file=MainApp.scala line=4
```

If we want to include the `Cause` in the log message, we can use the `ZIO.logCause` which takes the message and also the cause:

```scala modc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def run =
    ZIO
      .dieMessage("Boom!")
      .foldCauseZIO(
        cause => ZIO.logErrorCause("application stopped working due to an unexpected error", cause),
        _ => ZIO.unit
      )
}
```

Here is the output which includes the cause:

```scala
timestamp=2022-06-02T05:18:04.131876Z level=ERROR thread=#zio-fiber-6 message="application stopped working due to an unexpected error" cause="Exception in thread "zio-fiber-6" java.lang.RuntimeException: Boom!
at zio.examples.MainApp.run(MainApp.scala:9)
at zio.examples.MainApp.run(MainApp.scala:10)" location=zio.examples.MainApp.run file=MainApp.scala line=11
```

## Log Levels Supported by ZIO

To distinguish importance of log messages from each other, ZIO supports the following log levels. The default log level is `Info`, so when we use the `ZIO.log` or `ZIO.logCause` methods, the log message will be logged at the `Info` level. For other log levels, we can use any of the `ZIO.log*` or `ZIO.log*Cause` methods:

| LogLevel |        Value | Log Message    | Log with Cause
|----------|--------------|----------------|---------------------|
| All      | Int.MinValue |                |                     |
| Fatal    |        50000 | ZIO.logFatal   | ZIO.logFatalCause   |
| Error    |        40000 | ZIO.logError   | ZIO.logErrorCause   |
| Warning  |        30000 | ZIO.logWarning | ZIO.logWarningCause |
| Info     |        20000 | ZIO.logInfo    | ZIO.logInfoCause    |
| Debug    |        10000 | ZIO.logDebug   | ZIO.logDebugCause   |
| Trace    |            0 | ZIO.logTrace   | ZIO.logTraceCause   |
| None     | Int.MaxValue |                |                     |

As we said earlier, the default logger prints log messages equal or above the `Info` level. So, if we run the following code, only the `Info`, `Warning`, and `Error` and the `Fatal` log messages will be printed:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      _ <- ZIO.logFatal("Fatal")
      _ <- ZIO.logError("Error")
      _ <- ZIO.logWarning("Warning")
      _ <- ZIO.logInfo("Info")
      _ <- ZIO.logDebug("Debug")
      _ <- ZIO.logTrace("Trace")
    } yield ()
}
```

The output will look like the following:

```
timestamp=2022-06-01T10:16:26.623633Z level=FATAL thread=#zio-fiber-6 message="Fatal" location=zio.examples.MainApp.run file=MainApp.scala line=6
timestamp=2022-06-01T10:16:26.638771Z level=ERROR thread=#zio-fiber-6 message="Error" location=zio.examples.MainApp.run file=MainApp.scala line=7
timestamp=2022-06-01T10:16:26.640827Z level=WARN thread=#zio-fiber-6 message="Warning" location=zio.examples.MainApp.run file=MainApp.scala line=8
timestamp=2022-06-01T10:16:26.642260Z level=INFO thread=#zio-fiber-6 message="Info" location=zio.examples.MainApp.run file=MainApp.scala line=9
```

## Overriding Log Levels

As we mentioned, the default log level for ZIO.log and ZIO.logCause is `Info`. We can use these two methods to log various part of our workflow, and then finally we can wrap the whole workflow with our desired log level:

```scala
import zio._

ZIO.logLevel(LogLevel.Debug) {
  for {
    _ <- ZIO.log("Workflow Started.")
    _ <- ZIO.log("Running task 1.")
    _ <- ZIO.log("Running task 2.")
    _ <- ZIO.log("Workflow Completed.")
  } yield ()
}
```

## Logging Spans

ZIO supports logging spans. A span is a logical unit of work that is composed of a start and end time. The start time is when the span is created and the end time is when the span is completed. The span is useful for measuring the time it takes to execute a piece of code. To create a new span, we can use the `ZIO.logSpan` function as follows:

```scala
import zio._

ZIO.logSpan("span name") {
  // do some work
  // log inside the span
  // do some more work
  // another log inside the span
}
```

For example, assume we have a function that takes the `username` and returns the profile picture of the user. We can wrap the whole function in a new span called "get-profile-picture" and log inside the span:

```scala mdoc:compile-only
import zio._

case class User(id: String, name: String, profileImage: String)

def getProfilePicture(username: String) =
  ZIO.logSpan("get-profile-picture") {
    for {
      _    <- ZIO.log(s"Getting information of $username from the UserService")
      user <- ZIO.succeed(User("1", "john", "john.png"))
      _    <- ZIO.log(s"Downloading profile image ${user.profileImage}")
      img  <- ZIO.succeed(Array[Byte](1, 2, 3))
      _    <- ZIO.log("Profile image downloaded")
    } yield img
  }
```

If we run this code with `getProfilePicture("john")`, the output will look like the following:

```scala
timestamp=2022-06-01T13:59:40.779263Z level=INFO thread=#zio-fiber-6 message="Getting information of john from the UserService" get-profile-picture=6ms location=zio.examples.MainApp.getProfilePicture file=MainApp.scala line=11
timestamp=2022-06-01T13:59:40.793804Z level=INFO thread=#zio-fiber-6 message="Downloading profile image john.png" get-profile-picture=20ms location=zio.examples.MainApp.getProfilePicture file=MainApp.scala line=13
timestamp=2022-06-01T13:59:40.795677Z level=INFO thread=#zio-fiber-6 message="Profile image downloaded" get-profile-picture=22ms location=zio.examples.MainApp.getProfilePicture file=MainApp.scala line=15
```

Any logs inside the span region will be logged with the span name and the duration from the start of the span.

## Multiple Spans

We can also create multiple spans and log inside them:

```scala mdoc:compile-only
import zio._

ZIO.logSpan("span1") {
  for {
    _ <- ZIO.log("log inside span1")
    _ <- ZIO.logSpan("span2") {
      ZIO.log("log inside span1 and span2")
    }
    _ <- ZIO.log("log inside span1")
  } yield ()
}
```

## Annotating Logs

The default log message is a string, containing a series of key-value pairs. It contains the following information:
- `timestamp`: the time when the log is created
- `level`: the log level
- `thread`: the thread name (fiber name)
- `message`: the log message
- `cause`: the cause of the log
- `location`: the location of the source code where the log is created (filename)
- `line`: the line number of the source code where the log is created

Other than these key-value pairs, we can also annotate the log message with other customized information using the `ZIO.logAnnotate` function. This is especially useful when we are writing multiple services and we want to correlate logs between them using the `CorrelationId`.

For example, when we are handling an HTTP request, we can annotate the log message with the user's id:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  // We use random delay to simulate interleaving of operations in real world
  def randomDelay = Random.nextIntBounded(1000).flatMap(t => ZIO.sleep(t.millis))

  def run =
    ZIO.foreachParDiscard(Chunk("UserA", "UserB", "UserC")) { user =>
      ZIO.logAnnotate("user-id", user) {
        for {
          _ <- randomDelay
          _ <- ZIO.log("fetching user from database")
          _ <- randomDelay
          _ <- ZIO.log("downloading user's profile picture")
        } yield ()
      }
    }
}
```

The output will look as follows:

```scala
timestamp=2022-06-01T16:31:00.058151Z level=INFO thread=#zio-fiber-9 message="fetching user from database" location=zio.examples.MainApp.run file=MainApp.scala line=14 user-id=UserC
timestamp=2022-06-01T16:31:00.413569Z level=INFO thread=#zio-fiber-7 message="fetching user from database" location=zio.examples.MainApp.run file=MainApp.scala line=14 user-id=UserA
timestamp=2022-06-01T16:31:00.525170Z level=INFO thread=#zio-fiber-9 message="downloading user's profile picture" location=zio.examples.MainApp.run file=MainApp.scala line=16 user-id=UserC
timestamp=2022-06-01T16:31:00.807873Z level=INFO thread=#zio-fiber-8 message="fetching user from database" location=zio.examples.MainApp.run file=MainApp.scala line=14 user-id=UserB
timestamp=2022-06-01T16:31:00.830572Z level=INFO thread=#zio-fiber-7 message="downloading user's profile picture" location=zio.examples.MainApp.run file=MainApp.scala line=16 user-id=UserA
timestamp=2022-06-01T16:31:00.839411Z level=INFO thread=#zio-fiber-8 message="downloading user's profile picture" location=zio.examples.MainApp.run file=MainApp.scala line=16 user-id=UserB
```

As we can see, the `user-id` with its value is annotated to each log message. 
