---
id: logging 
title: "Introduction to Logging in ZIO"
sidebar_label: "Logging"
---

ZIO supports a lightweight built-in logging facade that standardizes the interface to logging functionality. So it doesn't replace existing logging libraries, but also we can plug it into one of the existing logging backends.

We can easily log using the `ZIO.log` function:

```scala mdoc:silent:nest
import zio._

val app = 
  for {
    _    <- ZIO.log("Application started!")
    name <- Console.readLine("Please enter your name: ")
    _    <- ZIO.log("User entered its name: $name")
    _    <- Console.printLine("Hello, $name")
  } yield ()
```

## Logging Levels

To log with a specific log-level, we can use the `ZIO.logLevel` combinator:

```scala mdoc:silent:nest
ZIO.logLevel(LogLevel.Warning) {
  ZIO.log("The response time exceeded its threshold!")
}
```

Or we can use the following functions directly:

* `ZIO.logDebug`
* `ZIO.logError`
* `ZIO.logFatal`
* `ZIO.logInfo`
* `ZIO.logWarning`

For example, for log with the error level, we can use `ZIO.logError` like this:

```scala mdoc:silent:nest
ZIO.logError("File does not exist: ~/var/www/favicon.ico")
```

## Spans

It also supports spans:

```scala mdoc:silent:nest
ZIO.logSpan("myspan") {
  ZIO.sleep(1.second) *> ZIO.log("The job is finished!")
}
```

ZIO Logging calculates the running duration of that span and includes that in the logging data corresponding to its span label.

## Further Reading

* [ZIO Logging](https://zio.dev/zio-logging)
* [How to Enable Logging in a ZIO Application](../../guides/tutorials/enable-logging-in-a-zio-application.md)
* [How to Create a Custom Logger for a ZIO Application?](../../guides/tutorials/create-custom-logger-for-a-zio-application.md)