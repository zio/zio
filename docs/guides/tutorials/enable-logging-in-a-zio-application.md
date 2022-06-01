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
