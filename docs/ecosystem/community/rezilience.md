---
id: rezilience
title: "Rezilience"
---

[Rezilience](https://github.com/svroonland/rezilience) is a ZIO-native library for making resilient distributed systems.

## Introduction

Rezilience is a ZIO-native fault tolerance library with a collection of policies for making asynchronous systems more resilient to failures inspired by Polly, Resilience4J, and Akka. It does not have external library dependencies other than ZIO.

It comprises these policies:
- **CircuitBreaker** — Temporarily prevent trying calls after too many failures
- **RateLimiter** — Limit the rate of calls to a system
- **Bulkhead** — Limit the number of in-flight calls to a system
- **Retry** — Try again after transient failures
- **Timeout** — Interrupt execution if a call does not complete in time

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "nl.vroste" %% "rezilience" % "<version>"
```

## Example

Let's try an example of writing _Circuit Breaker_ policy for calling an external API:

```scala
import nl.vroste.rezilience.CircuitBreaker.{CircuitBreakerCallError, State}
import nl.vroste.rezilience._
import zio._
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._

object CircuitBreakerExample extends zio.App {

  def callExternalSystem: ZIO[Console, String, Nothing] =
    putStrLn("External service called, but failed!").orDie *>
      ZIO.fail("External service failed!")

  val myApp: ZIO[Console with Clock, Nothing, Unit] =
    CircuitBreaker.withMaxFailures(
      maxFailures = 10,
      resetPolicy = Schedule.exponential(1.second),
      onStateChange = (state: State) =>
        ZIO(println(s"State changed to $state")).orDie
    ).use { cb =>
      for {
        _ <- ZIO.foreach_(1 to 10)(_ => cb(callExternalSystem).either)
        _ <- cb(callExternalSystem).catchAll(errorHandler)
        _ <- ZIO.sleep(2.seconds)
        _ <- cb(callExternalSystem).catchAll(errorHandler)
      } yield ()
    }

  def errorHandler: CircuitBreakerCallError[String] => URIO[Console, Unit] = {
    case CircuitBreaker.CircuitBreakerOpen =>
      putStrLn("Circuit breaker blocked the call to our external system").orDie
    case CircuitBreaker.WrappedError(error) =>
      putStrLn(s"External system threw an exception: $error").orDie
  }
  
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
