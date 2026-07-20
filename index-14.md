# Introduction to ZIO Direct Style

> ZIO Direct Style is a library that allows using directly-style i.e. imperative programming with ZIO effects which is based on the Monadless paradigm.

ZIO Direct Style is a library that allows using directly-style i.e. imperative programming with ZIO effects which is based on the Monadless paradigm.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-direct/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-direct_3.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-direct_3/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-direct_3.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-direct_3/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-direct-docs_3/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-direct-docs_3) [![ZIO Direct Style](https://img.shields.io/github/stars/zio/zio-direct?style=social)](https://github.com/zio/zio-direct)

## Installation

To use zio-direct, add the following to your `build.sbt` file.

```scala
libraryDependencies += "dev.zio" %% "zio-direct" % "1.0.0-RC7"
```

You can also use zio-direct with ZStream and ZPure by importing the following modules.
> Currently this is only supported in Scala 3.

```scala
// ZStream
libraryDependencies += "dev.zio" %% "zio-direct-streams" % "1.0.0-RC7"
// ZPure
libraryDependencies += "dev.zio" %% "zio-direct-pure" % "1.0.0-RC7"
```
See the [Other Supported Monads](other-supported-monads) section for more details.

## IDE Support

The preferred IDE to use with ZIO-Direct is Visual Studio Code + Metals. This is because Metals correctly reads the returns from `defer` calls directly from the Scala compiler which is not the case of IntelliJ. To remedy this issue, a Library Extension is provided for ZIO-Direct. See the [IntelliJ Support](intellij-support) section for more details.

## Introduction

Talk at Functional Scala 2022:
* Video - https://www.youtube.com/watch?v=DgqfLfzq5eo
* Slides - https://www.slideshare.net/deusaquilus/ziodirect-functional-scala-2022

ZIO-Direct allows direct style programming with ZIO. This library provides a *syntactic sugar* that is more powerful than for-comprehensions as well as more natural to use. Simply add the `.run` suffix to any ZIO effect in order to retrieve its value.

ZIO-Direct works by using macros to rewrite sequential code into flatMap-chains based on the [Monadless](https://github.com/monadless/monadless) paradigm. The values resulting in `.run` calls from the ZIO effects are not actually awaited. Instead, they are rolled-up into a chain of flatMaps.

For example, in imperative programming operations typically are done in a simple set of steps.
```scala
object FileOps:
  def read(file: File): String
  def write(file: File, content: String): Unit

val textA = read(fileA)
val textB = read(fileB)
write(fileC, textA + textB)
```

Using functional programming, the equivalent of this functionality is a set of nested flatMap-chains.
```scala
object FileOps
  def read(file: File): ZIO[Any, Throwable, String]
  def write(file: File, content: String): ZIO[Any, Throwable, Unit]

read(fileA).flatMap { textA =>
  read(fileB).flatMap { textB =>
    write(fileC, textA + textB)
  }
}
```

In order to avoid this complexity scala provides a for-comprehension syntactic sugar.
```scala
for {
  textA <- read(fileA)
  textB <- read(fileB)
  _ <- write(fileC, textA + textB)
} yield ()
```

Unfortunately this syntactic sugar is limited in many cases, for example, inserting a conditional value inside is impossible.
```scala
for {
  textA <- read(fileA)
  // Not a possible syntax
  if (fileA.contains("some string")) {
    textB <- read(fileB)
    _ <- write(fileC, textA + textB)
  }
} yield ()
```

ZIO-Direct offers an equivalent syntactic sugar that is more ergonomic and allows many constructs that for-comprehensions do not.
```scala
defer {
  val textA = read(fileA).run
  if (fileA.contains("some string")) {
    val textB = read(fileB).run
    write(fileC, textA + textB).run
  }
}
```

# ZIO-Tailored
ZIO-Direct is specifically tailored to ZIO capabilities as it supports Environment and Error composition in ZIO effects similar to the for-comprehension.

```scala
val out: ZIO[CustomerConfig & DistributorConfig, CustomerGetException | DistrubutorGetException, (Customer, Distributor)] =
  defer {
    // Get a customer-configuration object from the environment and extract its .url field
    val custUrl: String = ZIO.service[CustomerConfig].run.url
    // Get a distributor-configuration from the environment and extract its .url field
    val distUrl: String = ZIO.service[DistributorConfig].run.url
    (
      // Use the two configurations to make an HTTP-call
      parseCustomer(httpGetCustomer(custUrl).run),
      parseDistrubutor(httpGetDistributor(distUrl).run)
    )
  }
```

## Branching and Looping Support
Unlike the for-comprehension, ZIO-Direct supports branching and looping in the use of flatMaps composition.
Let's have a look at a another non-trivial example.

```scala
class Database:
  def nextRow(): ZIO[Any, Throwable, Row]
  def hasNextRow(): ZIO[Any, Throwable, Boolean]
  def lockNextRow(): ZIO[Any, Throwable, Boolean]
object Database:
  def open: ZIO[Any, Throwable, Database]

defer {
  // Open a database connection
  val db = Database.open().run
  // See if there is is a next-row
  while (db.hasNextRow().run) {
    // try to lock, if aquired continue
    if (db.lockNextRow().run)
      val nextRow = db.nextRow().run
      doSomethingWith(nextRow)
    else
      waitT()
  }
}
```
> NOTE: The above database-api is imaginary.

The above code needs to be translated into something like this:

```scala
Database.open.flatMap { db =>
  def whileFun(): ZIO[Any, Throwable, Unit] =
    db.hasNextRow().flatMap { hasNextRow =>
      if (hasNextRow)(
        db.lockNextRow().flatMap { lockNextRow =>
          if (!lockNextRow)
            db.nextRow().map(nextRow => doSomethingWith(nextRow))
          else
            ZIO.succeed(waitT())
        }
      ).flatMap(_ => whileFun())
      else
        ZIO.unit
    }
  whileFun()
}
```

Note that normally this is the exact code that would have to be written to capture such functionality For-comprehensions do not provide a way to do looping and branching so in such cases
a combination of flatMaps and recursion is necessary to avoid calling effects unnecessarily.

## Great for Refs and FiberRefs!

ZIO-direct makes it much easier to use ZIO mutable Ref and FiberRef variables. Since retrieveing and updating Ref and FiberRef variables requries a flatMap/for-comprehension call, it is typically very difficult to use them with branching/looping constructs. ZIO-direct makes these cases much easier.

```scala
class DatabaseApi {
  val connRef = FiberRef.make[Option[Connection]](None)
  def openConnection(): Connection = lowLevelDatabaseApi.openConnection()
  def transaction(action: Action) =
    defer {
      val conn = connRef.get.run
      if (conn == None) {
        connRef.set(Some(openConnection())).run
      }
      val conn1 = connRef.get.run
      conn.execute(action).run
    }
}
```

Instead of having to write the following code:
```scala
class DatabaseApi {
  val connRef = FiberRef.make[Option[Connection]](None)
  def openConnection(): Connection = lowLevelDatabaseApi.openConnection()
  def transaction(action: Action) =
    connRef.get.flatMap { conn =>
      (if (conn == None) {
        connRef.set(Some(openConnection()))
      } else {
        ZIO.unit
      }).flatMap(_ => connRef.get.flatMap { conn1 =>
        conn1.execute(action)
      })
    }

  // Note that for-comprehensions do not help very much in this use-case
  def transaction0(action: Action) =
    for {
      conn <- connRef.get
      _ <- if (conn == None) {
        connRef.set(Some(openConnection()))
      } else {
        ZIO.unit
      }
      conn1 <- connRef.get
      _ <- conn1.execute(action)
    }
}
```
