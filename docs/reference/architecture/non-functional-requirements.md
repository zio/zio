---
id: non-functional-requirements
title: "Non-functional Requirements"
---

## Introduction

Designing and architecting a software system is a complex task. We should consider both the functional and non-functional requirements of the system.

The functional requirements are the features of the system which are directly related to the business domain and its problems. They are the core of the system and the main reason why we are designing and building the application.

Non-functional requirements are characteristics of the system that are used to qualify it in terms of "what should the system be" rather than "what should the system do," e.g.:

  1. [Correctness](#1-correctness)
  2. [Testability](#2-testability)
  3. [Maintainability](#3-maintainability)
  4. [Low Latency](#4-low-latency)
  5. [High Throughput](#5-high-throughput)
  6. [Robustness](#6-robustness)
  7. [Resiliency](#7-resiliency)
  8. [Efficiency](#8-efficiency)
  9. [Developer Productivity](#9-developer-productivity)

In this article, from the perspective of application architecture, we are going to look at some design elements that we can apply to our ZIO applications to make them more ergonomic and maintainable.

## 1. Correctness

Correctness is the ability of a system to do what it is supposed to do. ZIO provides us correctness property through local reasoning because of referential transparency and its type-safety.

When we have referential transparency, we do not need to look at the whole program to understand the behavior of a piece of code. We can reason about the application behavior locally and then make sure that all components work together correctly, from button to top.

The type system of ZIO also prevents us to introduce common bugs at runtime. Here are two examples:

  1. **Resource Management**— When we have a ZIO effect that has a type of `ZIO[Scope, IOException, FileInputStream]`, we can be sure that this effect will open a resource, and we should care about closing it. So then by using `ZIO.scoped(effect)` we can be sure that the resource will be closed after the effect is executed and the type of effect will be changed to `ZIO[Any, IOException, FileInputStream`. To learn more about `ZIO.scoped` and resource management using `Scope`, please refer to the [Scope][11] of the [resource management][12].

  2. **Error Management**— In ZIO errors are typed, so we can describe all possible errors that can happen in our effect. And from the correctness perspective, the type system helps us to be sure we have handled all errors or not. For example, if we have an effect of type `ZIO[Any, IOException, FileInputStream]`, by looking at the effect type, we can be sure the effect is exceptional, and we should handle its error. To learn more about error management in ZIO, please refer to the [error management][13] section.

## 2. Testability

ZIO has a strong focus on testability which supports:

  1. Property-based Checking
  2. Testing Effectful and Asynchronous Codes
  3. Testing Passages of Time
  4. Sharing Layers Between Specs
  5. Resource Management While Testing
  6. Dynamic Test Generation
  7. Test Aspects (AOP)
  8. Non-flaky Tests

To learn more about testing in ZIO, please refer to the [testing][14] section.

## 3. Maintainability

When we use ZIO, we take advantage of both functional and object-oriented programming paradigms to make our code maintainable:

- By using functional programming we can make sure that our code is correct, readable, testable, and reusable.

- The object-oriented programming paradigm helps us to make our code well-organized and highly cohesive by using objects, packages, and modules.

The ZIO's support for type safety is another factor that makes our code maintainable, especially when we refactor our codes we can be sure that we are not breaking anything.

## 4. Low Latency

Latency is the time it takes for a request to be processed and a response to be returned. ZIO is designed to support low latency applications by providing various concurrency and parallelism tools such as `ZIO.foreachPar`, `Fiber`, `Promise`, `Ref`, `Queue`, etc. To learn more about concurrency and parallelism in ZIO, please refer to the [concurrency][15] section.

## 5. High Throughput

ZIO fibers are lightweight threads (green threads). They are very cheap to create and destroy. So we can potentially have thousands of fibers running in parallel on a single machine, which helps us to achieve high throughput:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def doWork(n: Int): ZIO[Any, Nothing, Unit] =  ??? 
  
  def run =
    ZIO
      .foreach(1 to 100000)(n => doWork(n).fork)
      .flatMap(f => Fiber.collectAll(f).join)
}
```

Other than low-level concurrency tools like `Fiber`, `Promise`, `Ref`, etc., ZIO Streams is a high-level abstraction for processing high-throughput data streams:

```scala mdoc:compile-only
import zio._
import zio.stream._

object MainApp extends ZIOAppDefault {
  def doWork(n: Int): ZIO[Any, Nothing, Unit] =  ??? 

  def run =
    ZStream
      .fromIterable(1 to 100000)
      .mapZIOParUnordered(Int.MaxValue)(doWork)
      .runDrain
}
```

:::note
The above examples are just for demonstration purposes. In real-world applications, depending on the nature of the problem to reach a better performance it may be better to control the level of parallelism instead of using unbounded parallelism.
:::

Another factor that helps us to achieve high throughput is the fact that we may have high workloads for some periods. In such cases, we can benefit from buffering the incoming requests instead of rejecting them and trying to process them later. We can use [`Queue`][16] for this purpose or the [`ZStream#buffer` operator][17].

To learn more about ZIO Streams, please refer to the [ZIO Streams][18] section.

## 6. Robustness

With the help of ZIO's error channel, we can write applications whose errors are fully specified and handled at the compile time. Having this feature helps us to make our applications more robust.

It also gives us the ability to lossless translation of errors from one domain to another. For example, when writing a web application, we can reliably translate errors inside the application to HTTP response codes. ZIO uses the compile to ensure that we have mapped all possible errors to HTTP response codes.

To learn more about error management in ZIO, please refer to the [error management][13] section.

## 7. Resiliency

For resiliency, we can use ZIO's retry operator along with the retry policy to make our application resilient to failures. `Schedule` is a powerful composable data type that helps us to compose multiple policies together and make a complex retry policy:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  sealed trait DownloadError    extends Throwable
  case object BandwidthExceeded extends DownloadError
  case object NetworkError      extends DownloadError

  // flaky api
  def download(id: String): ZIO[Any, DownloadError, Array[Byte]] = ???

  def isRecoverable(e: DownloadError): Boolean =
    e match {
      case BandwidthExceeded => false
      case NetworkError      => true
    }

  val policy =
    (Schedule.recurs(20) &&
      Schedule.exponential(100.millis))
      .whileInput(isRecoverable)

  def run = download("123").retry(policy)
}
```

To learn more about resiliency and scheduling in ZIO, please refer to the [resiliency][19] section.

## 8. Efficiency

ZIO is designed to be extraordinarily efficient. Let's take a look at some of the features that make ZIO efficient:

1. ZIO Streams are pull-based, so the source of the stream starts producing elements only when the stream is consumed. This lazy semantic helps us to avoid unnecessary work and save resources:

```scala
def downloadAsCsv(id: String): ZStream[Db, IOException, Byte] =
  jdbc
    .selectMany(sql"SELECT * FROM events WHERE userId = $id")
    .map(toCSV)
    .via(ZPipeline.utf8Encode)
    .via(ZPipeline.gzip)
```

In the above example, tries to consume a minimum amount of computation that is necessary. So if we use this workflow in a web application, when the client downloads half of the CSV file, only half of the data will be pulled from the database. So we can save resources and infrastructure costs.

2. ZIO is designed to be interruptible (unlike the `Future` in Scala). So we can cancel any running effect at any time. This feature enables us to have efficient high-level operators such as `ZIO#race` on top of the ZIO interruption model. With `race` we can run two different workflows in parallel and the loser of the workflow will be canceled:

```scala
val loaded = loadFromCache(productId).race(loadFromDb(productId))
```

Or if we do a bunch of things in parallel and one of those things fails, all the other ones which are currently running in parallel will be canceled automatically:

```scala
val aggregated =
  ZIO.foreach(account.statements) { statement =>
    downloadStatement(statement.s3Bucket) 
  }.map(aggregateStatements(_))
```

If we timeout a workflow in ZIO, once the timeout is reached, the workflow will be canceled automatically:

```scala
val timedOut = aggregated.timeout(10.seconds)
```

So in the above example, all running workflows will be simultaneously canceled once the timeout is reached and all resources will be released.

3. Another ZIO feature that helps us to have efficient workflows is its resource management. ZIO provides a great model for resource management with the help of the `Scope` data type. `Scope` is a contextual data type that whenever appears in the environment of an effect, denotes this effect will open one or more resources. Using `ZIO.scoped` we can ensure that all resources enclosed in this operator will be automatically released once the effect is completed or interrupted:

```scala mdoc:compile-only
import zio._
import scala.io.BufferedSource

def source(name: String): ZIO[Scope, Throwable, BufferedSource] =
  ZIO.acquireRelease(ZIO.attemptBlocking(scala.io.Source.fromFile(name)))(s => ZIO.succeedBlocking(s.close()))

val fileContent: ZIO[Any, Throwable, String] =
  ZIO.scoped {
    source("file.txt").map(_.getLines()).map(_.mkString("\n"))
  }
```

In the above example, if we use the `fileContent` effect, we can be sure that the file handler will be released regardless of whether the effect is completed or interrupted.

To learn more about resource management in ZIO, please refer to the [resource management][11] section.

## 9. Developer Productivity

Developer experience and productivity are very important for choosing a technology for any large-scala and long-running project. Let's take a look at some features that make ZIO a great fit for developer productivity:

  1. Referential Transparency and Purity
  2. Composable Data Types
  3. Type-safety and Compile time Error Checking
  4. Easy to Refactor
  5. Discoverability
      1. Dot completion when developing with IDEs
      2. Consistent naming conventions
  6. Concise and Expressive API with Minimal Boilerplate
  7. Expressive Compiler Errors
  8. Empowering Meta-programming and Macros
  9. [Maintainability](#3-maintainability)
  10. Observability
      - [Logging][1]
      - [Tracing][2]
      - [Metrics][3]
  11. [Debugging Facilities][4]
  12. [Compile-time Execution Tracing][5]
  13. [Automatic Dependency Graph Generation][6]
  14. [Testability][7]
  15. [Programming Without Type Classes][8]
  16. Rich Ecosystem
      - Massive Amount of Libraries and Tools on JVM
      - [ZIO Official libraries][9]
      - [ZIO community libraries][10]

[1]: ../../zio-logging/index.md
[2]: ../../zio-telemetry/index.md
[3]: ../observability/metrics/index.md
[4]: ../../guides/migrate/migration-guide.md#debugging
[5]: ../../guides/migrate/migration-guide.md#compile-time-execution-tracing
[6]: ../di/automatic-layer-construction.md
[7]: ../test/index.md
[8]: https://www.youtube.com/watch?v=QDleESXlZJw
[9]: ../../ecosystem/officials/index.md
[10]: ../../ecosystem/community/index.md
[11]: ../resource/scope.md
[12]: ../resource/index.md
[13]: ../error-management/index.md
[14]: ../test/index.md
[15]: ../concurrency/index.md
[16]: ../concurrency/queue.md
[17]: ../stream/zstream/operations.md#buffering
[18]: ../stream/index.md
[19]: ../schedule/index.md
