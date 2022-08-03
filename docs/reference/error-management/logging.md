---
id: logging
title: "Logging"
---


ZIO has built-in logging functionality. This allows us to log within our application without adding new dependencies. ZIO logging doesn't require any services from the environment.

We can easily log inside our application using the `ZIO.log` function:

```scala mdoc:silent:nest
import zio._

ZIO.log("Application started!")
```

The output would be something like this:

```bash
[info] timestamp=2021-10-06T07:23:29.974297029Z level=INFO thread=#2 message="Application started!" file=ZIOLoggingExample.scala line=6 class=zio.examples.ZIOLoggingExample$ method=run
```

To log with a specific log-level, we can use the `ZIO.logLevel` combinator:

```scala mdoc:silent:nest
ZIO.logLevel(LogLevel.Warning) {
  ZIO.log("The response time exceeded its threshold!")
}
```

Or we can use the following functions directly:

- `ZIO.logDebug`
- `ZIO.logError`
- `ZIO.logFatal`
- `ZIO.logInfo`
- `ZIO.logWarning`

```scala mdoc:silent:nest
ZIO.logError("File does not exist: ~/var/www/favicon.ico")
```

It also supports logging spans:

```scala mdoc:silent:nest
ZIO.logSpan("myspan") {
  ZIO.sleep(1.second) *> ZIO.log("The job is finished!")
}
```

ZIO Logging calculates and records the running duration of the span and includes that in logging data:

```bash
[info] timestamp=2021-10-06T07:29:57.816775631Z level=INFO thread=#2 message="The job is done!" myspan=1013ms file=ZIOLoggingExample.scala line=8 class=zio.examples.ZIOLoggingExample$ method=run
```
