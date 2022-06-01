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
  def run = ZIO.logInfo("Application started")
}
```

This will print the following message to the console:

```scala
timestamp=2022-06-01T09:43:08.848398Z level=INFO thread=#zio-fiber-6 message="Application started" location=zio.examples.MainApp.run file=MainApp.scala line=4
```

Based on the log level, we can use any of the following ZIO functions:

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

If we run the following code, only the `Info`, `Warning`, and `Error` and the `Fatal` log messages will be printed:

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
