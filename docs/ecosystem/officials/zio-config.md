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
libraryDependencies += "dev.zio" %% "zio-config" % "3.0.1" 
```

There are also some optional dependencies:
- **zio-config-magnolia** — Auto Derivation
- **zio-config-refined** — Integration with Refined Library
- **zio-config-typesafe** — HOCON/Json Support
- **zio-config-yaml** — Yaml Support
- **zio-config-gen** — Random Config Generation

## Example

Let's add these four lines to our `build.sbt` file as we are using these modules in our example:

```scala
libraryDependencies += "dev.zio" %% "zio-config"          % "3.0.1"
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % "3.0.1"
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % "3.0.1"
libraryDependencies += "dev.zio" %% "zio-config-refined"  % "3.0.1"
```

In this example we are reading from HOCON config format using type derivation:

```scala mdoc:compile-only
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.GreaterEqual
import zio._
import zio.config.magnolia.{describe, descriptor}
import zio.config.typesafe.TypesafeConfigSource

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

object ZIOConfigExample extends ZIOAppDefault {
  import zio.config._
  import zio.config.refined._

  val json =
    s"""
       |"Database" : {
       |  "port" : "1024",
       |  "host" : "localhost"
       |}
       |""".stripMargin

  def run =
    for {
      _ <- ZIO.unit
      source = TypesafeConfigSource.fromHoconString(json)
      desc = descriptor[DataSource] from source
      dataSource <- read(desc)
      // Printing Auto Generated Documentation of Application Config
      _ <- Console.printLine(
        generateDocs(desc).toTable.toGithubFlavouredMarkdown
      )
      _ <- dataSource match {
        case Database(host, port) =>
          ZIO.debug(s"Start connecting to the database: $host:$port")
        case Kafka(_, brokers) =>
          ZIO.debug(s"Start connecting to the kafka brokers: $brokers")
      }
    } yield ()

}
```

## Resources

- [Easy Config For Your App](https://www.youtube.com/watch?v=4SrSKluyyKo) by Afsal Thaj (December 2020) — Managing application configuration can be quite challenging: we often have to support multiple data sources with overrides, including HOCON, system properties, environment variables, and more. We have to document our configuration so it is clear to IT and DevOps how to configure our applications. We have to do strong validation with error accumulation to ensure bad data is rejected and good error messages are generated for end-users. In this presentation, Afsal Thaj, the author of ZIO Config, shows attendees how to solve all of these problems in a composable and testable way. By separating the description of configuration from what must be done with the configuration, ZIO Config provides all standard features—including multiple data sources, data source overrides, documentation, and validation with error accumulation—for free. Come learn how to make your applications configurable in an easy way that will delight IT and DevOps and make it easy to change your applications over time.

- Introduction to ZIO Config by Afsal Thaj
    - Part 1: [Start writing better Scala with zio-config](https://www.youtube.com/watch?v=l5CVQmSp7fY)
    - Part 2: [Maximise the use of Scala types (Option & Either in zio-config)](https://www.youtube.com/watch?v=SusCbrSK5eA&t=0s)
    - Part 3: [Intro to ADT, and scalable configuration management!](https://www.youtube.com/watch?v=LGo_g1GK6_k&t=0s)
    - Part 4: [Auto generate sample configurations of your application in Scala](https://www.youtube.com/watch?v=--mcs4HztJY&t=0s)
