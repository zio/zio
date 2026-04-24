# Introduction to ZIO DynamoDB

> Simple, type-safe, and efficient access to DynamoDB

Simple, type-safe, and efficient access to DynamoDB

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-dynamodb/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-dynamodb_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-dynamodb_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-dynamodb_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-dynamodb_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-dynamodb-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-dynamodb-docs_2.13) [![ZIO DynamoDB](https://img.shields.io/github/stars/zio/zio-dynamodb?style=social)](https://github.com/zio/zio-dynamodb)

## Introduction

ZIO DynamoDB is a library that is used for type-safe, efficient, and boilerplate free access to AWS's DynamoDB service. It provides a type-safe API for many query types and the number of type-safe APIs is expanding. ZIO DynamoDB will automatically batch queries and execute un-batchable queries in parallel.

Under the hood we use the excellent [ZIO AWS](https://zio.dev/zio-aws) library for type-safe DynamoDB access, and the awesome [ZIO Schema](https://zio.dev/zio-schema) library for schema derived codecs (see here for documentation on how to [customise these through annotations](guides/codec-customization.md)).

For an overview of the High Level API please see the [ZIO DynamoDB cheat sheet](guides/cheat-sheet.md).

## Installation

To use ZIO DynamoDB, we need to add the following lines to our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb" % "1.0.0-RC24"
)
```

### Cats Effect Interop

To use the Cats Effect 3 interop module, we need to also add the following line to our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-ce" % "1.0.0-RC24"
)
```

For CE interop examples please see [examples sbt module](https://github.com/zio/zio-dynamodb/blob/series/2.x/examples/src/main/scala/zio/dynamodb/examples/dynamodblocal/interop/CeInteropExample.scala).

### Future Interop

For projects that do not use effect libraries (e.g. Play applications) we can use the Future interop module.
We need to add the following line to our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-future" % "1.0.0-RC24"
)
```

For Future interop examples please see [examples sbt module](https://github.com/zio/zio-dynamodb/blob/series/2.x/examples/src/main/scala/zio/dynamodb/examples/dynamodblocal/interop/FutureInteropExample.scala).

### Read/write DynamoDB JSON
AWS tools like the CLI and Console read/write a special JSON representation of dynamoDB items. The new experimental optional `zio-dynamodb-json` module provides a way to read/write this form of JSON when working with both the High Level and Low Level API. To use this module, we need to also add the following line to our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-json" % "1.0.0-RC24"
)
```

## Example

For examples please see [examples sbt module](https://github.com/zio/zio-dynamodb/tree/series/2.x/examples/src/main/scala/zio/dynamodb/examples). Below is `Main.scala` from that module:

```scala

object Main extends ZIOAppDefault {

  final case class Person(id: Int, firstName: String)
  object Person {
    implicit lazy val schema: Schema.CaseClass2[Int, String, Person] = DeriveSchema.gen[Person]

    val (id, firstName) = ProjectionExpression.accessors[Person]
  }
  val examplePerson = Person(1, "avi")

  private val program = for {
    _      <- put("personTable", examplePerson).execute
    person <- get("personTable")(Person.id.partitionKey === 1).execute
    _      <- zio.Console.printLine(s"hello $person")
  } yield ()

  override def run =
    program.provide(
      netty.NettyHttpClient.default,
      config.AwsConfig.default, // uses real AWS dynamodb
      dynamodb.DynamoDb.live,
      DynamoDBExecutor.live
    )
}
```

For examples on how to use the DynamoDBLocal in memory database please see the [integration tests](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/it/scala/zio/dynamodb/TypeSafeApiCrudSpec.scala)
and [DynamoDBLocalMain](https://github.com/zio/zio-dynamodb/blob/series/2.x/examples/src/main/scala/zio/dynamodb/examples/dynamodblocal/DynamoDBLocalMain.scala) .
Note before you run these you must first run the DynamoDBLocal docker container using the provided docker-compose file:

```
docker compose -f docker/docker-compose.yml up -d
```

Don't forget to shut down the container after you have finished

```
docker compose -f docker/docker-compose.yml down
```

## Resources
- [Introducing ZIO DynamoDB by Avinder Bahra & Adam Johnson](https://www.youtube.com/watch?v=f68-69eA8Vc&t=33s) - DynamoDB powers many cloud-scale applications, with its robust horizontal scalability and uptime. Yet, interacting with the Java SDK is error-prone and tedious. In this presentation, Avinder Bahra presents ZIO DynamoDB, a new library by Avi and Adam Johnson designed to make interacting with DynamoDB easy, type-safe, testable, and productive.
- [Introducing The ZIO DynamoDB Type-Safe API by Avinder Bahra](https://www.youtube.com/watch?v=Qte4WUfHQ3g&t=10s) - Adam Johnson and Avinder released ZIO DynamoDB, a new Scala library that significantly reduces boilerplate when compared to working directly with AWS client libraries. However, there was still work to be done to improve type safety. In this talk, Avinder introduces a new type-safe API that can prevent many errors at compile time while remaining user-friendly.
