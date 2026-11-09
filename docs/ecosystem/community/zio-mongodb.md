---
id: zio-mongodb
title: "ZIO MongoDB"
---

[ZIO MongoDB](https://github.com/zeal18/zio-mongodb) is a ZIO wrapper around the official MongoDB Java driver.

## Introduction

The main goals of the project are:
- **First-Class ZIO Support** — The client, database, and collection are exposed as ZIO services and effects.
- **Compile-Time Codecs** — The runtime codec resolution of the Java driver is replaced by compile-time codec derivation, and Scala 3 is supported.
- **Type-Safe Query DSL** — Query building is split from results processing, and filters are built with a type-safe DSL.
- **Test Kit** — A `zio-test` integrated test kit runs the tests against an embedded MongoDB.

ZIO 1 is no longer supported; the latest version based on ZIO 1 is `0.5.1`.

## Installation

In order to use this library, we need to add the following lines to our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "io.github.zeal18" %% "zio-mongodb-bson"    % "0.11.5",
  "io.github.zeal18" %% "zio-mongodb-driver"  % "0.11.5",
  "io.github.zeal18" %% "zio-mongodb-testkit" % "0.11.5" % Test
)
```

## Example

First, we create the layers for the client and the database:

```scala
import io.github.zeal18.zio.mongodb.driver.MongoClient
import io.github.zeal18.zio.mongodb.driver.MongoDatabase
import zio.ZIO
import zio.ZLayer

val client: ZLayer[Any, Throwable, MongoClient] =
  MongoClient.live("mongodb://localhost:27017")

val database: ZLayer[Any, Throwable, MongoDatabase] =
  client >>> ZLayer.fromZIO(ZIO.serviceWith[MongoClient](_.getDatabase("database-name")))
```

Then we can write a data access layer on top of a typed collection:

```scala
import io.github.zeal18.zio.mongodb.bson.annotations.BsonId
import io.github.zeal18.zio.mongodb.driver.MongoDatabase
import io.github.zeal18.zio.mongodb.driver.filters
import zio.Task
import zio.ZLayer

case class User(@BsonId id: Int, name: String)

trait UsersDal {
  def insert(user: User): Task[Boolean]
  def get(id: Int): Task[Option[User]]
  def delete(id: Int): Task[Boolean]
}

object UsersDal {
  val live: ZLayer[MongoDatabase, Nothing, UsersDal] = ZLayer.fromZIO(for {
    mongo <- ZIO.service[MongoDatabase]

    coll =
      mongo
        .getCollection[User]("users")
        .withReadConcern(ReadConcern.MAJORITY)
        .withWriteConcern(WriteConcern.MAJORITY)
  } yield new UsersDal {
    override def insert(user: User): Task[Boolean] =
      coll.insertOne(user).map(_.wasAcknowledged())

    override def get(id: Int): Task[Option[User]] =
      coll.find(filters.eq(id)).runHead

    override def delete(id: Int): Task[Boolean] =
      coll.deleteOne(filters.eq(id)).map(_.getDeletedCount() > 0)
  })
}
```

## Testing

The test kit provides `MongoClientTest` and `MongoDatabaseTest`, which start an embedded MongoDB. Sharing the client layer avoids relaunching MongoDB for every test case, while a database with a random name isolates the test cases from each other:

```scala
import io.github.zeal18.zio.mongodb.testkit.MongoClientTest
import io.github.zeal18.zio.mongodb.testkit.MongoDatabaseTest
import zio.ZIO
import zio.test.*

object UsersDalSpec extends ZIOSpecDefault {
  override def spec = suite("UsersDalSpec")(
    test("insert user") {
      for {
        dal <- ZIO.service[UsersDal]

        inserted <- dal.insert(User(3, "John Doe"))
      } yield assertTrue(inserted)
    },
    test("get user") {
      for {
        dal <- ZIO.service[UsersDal]
        user = User(5, "John Doe")

        _      <- dal.insert(user)
        result <- dal.get(5)
      } yield assertTrue(result.get == user)
    },
  ).provideSomeLayer(MongoDatabaseTest.random >>> UsersDal.live)
    .provideLayerShared(MongoClientTest.live())
}
```
