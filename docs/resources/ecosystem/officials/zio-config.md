---
id: zio-config
title: "ZIO Config"
---

[ZIO Config](https://zio.github.io/zio-config/) is a ZIO-based library for loading and parsing configuration sources.

## Introduction
In the real world, config retrieval is the first to develop applications. We mostly have some application config that should be loaded and parsed through our application. Doing such things manually is always boring and error-prone and also has lots of boilerplates.

The ZIO Config has a lot of features, and it is more than just a config parsing library. Let's enumerate some key features of this library:

- **Support for Various Sources** — It can read/write flat or nested configurations from/to various formats and sources.

- **Composable sources** — ZIO Config can compose sources of configuration, so we can have, e.g. environmental or command-line overrides.

- **Automatic Document Generation** — It can auto-generate documentation of configurations. So developers or DevOps engineers know how to configure the application.

- **Report generation** — It has a report generation that shows where each piece of configuration data came from.

- **Automatic Derivation** — It has built-in support for automatic derivation of readers and writers for case classes and sealed traits.

- **Type-level Constraints and Automatic Validation** — because it supports _Refined_ types, we can write type-level predicates which constrain the set of values described for data types.

- **Descriptive Errors** — It accumulates all errors and reports all of them to the user rather than failing fast.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-config" % <version>
```

There are also some optional dependencies:
- **zio-config-mangolia** — Auto Derivation
- **zio-config-refined** — Integration with Refined Library
- **zio-config-typesafe** — HOCON/Json Support
- **zio-config-yaml** — Yaml Support
- **zio-config-gen** — Random Config Generation

## Example

Let's add these four lines to our `build.sbt` file as we are using these modules in our example:

```scala
libraryDependencies += "dev.zio" %% "zio-config"          % "1.0.6"
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % "1.0.6"
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "1.0.6"
libraryDependencies += "dev.zio" %% "zio-config-refined"  % "1.0.6"
```

In this example we are reading from HOCON config format using type derivation:

```scala
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.GreaterEqual
import zio.config.magnolia.{describe, descriptor}
import zio.config.typesafe.TypesafeConfigSource
import zio.console.putStrLn
import zio.{ExitCode, URIO, ZIO}

sealed trait DataSource

final case class Database(
    @describe("Database Host Name")
    host: Refined[String, NonEmpty],
    @describe("Database Port")
    port: Refined[Int, GreaterEqual[W.`1024`.T]]
) extends DataSource

final case class Kafka(
    @describe("Kafka Topics")
    topicName: String,
    @describe("Kafka Brokers")
    brokers: List[String]
) extends DataSource

object ZIOConfigExample extends zio.App {
  import zio.config._
  import zio.config.refined._

  val json =
    s"""
       |"Database" : {
       |  "port" : "1024",
       |  "host" : "localhost"
       |}
       |""".stripMargin

  val myApp =
    for {
      source <- ZIO.fromEither(TypesafeConfigSource.fromHoconString(json))
      desc = descriptor[DataSource] from source
      dataSource <- ZIO.fromEither(read(desc))
      // Printing Auto Generated Documentation of Application Config
      _ <- putStrLn(generateDocs(desc).toTable.toGithubFlavouredMarkdown)
      _ <- dataSource match {
        case Database(host, port) =>
          putStrLn(s"Start connecting to the database: $host:$port")
        case Kafka(_, brokers) =>
          putStrLn(s"Start connecting to the kafka brokers: $brokers")
      }
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
