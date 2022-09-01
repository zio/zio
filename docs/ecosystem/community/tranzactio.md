---
id: tranzactio
title: "TranzactIO"
---

[TranzactIO](https://github.com/gaelrenoux/tranzactio) is a ZIO wrapper for some Scala database access libraries, currently for [Doobie](https://github.com/tpolecat/doobie) and [Anorm](https://github.com/playframework/anorm).

## Introduction

Using functional effect database access libraries like _Doobie_ enforces us to use their specialized monads like `ConnectionIO` for _Doobie_. The goal of _TranzactIO_ is to provide seamless integration with these libraries to help us to stay in the `ZIO` world.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "io.github.gaelrenoux" %% "tranzactio" % "2.1.0"
```

In addition, we need to declare the database access library we are using. For example, for the next example we need to add following dependencies for _Doobie_ integration:

```scala
libraryDependencies += "org.tpolecat" %% "doobie-core" % "0.13.4"
libraryDependencies += "org.tpolecat" %% "doobie-h2"   % "0.13.4"
```

## Example

Let's try an example of simple _Doobie_ program:

```scala
import doobie.implicits._
import io.github.gaelrenoux.tranzactio.doobie
import io.github.gaelrenoux.tranzactio.doobie.{Connection, Database, TranzactIO, tzio}
import org.h2.jdbcx.JdbcDataSource
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.{ExitCode, Has, URIO, ZIO, ZLayer, blocking}

import javax.sql.DataSource

object TranzactIOExample extends zio.App {

  val query: ZIO[Connection with Console, Throwable, Unit] = for {
    _ <- PersonQuery.setup
    _ <- PersonQuery.insert(Person("William", "Stewart"))
    _ <- PersonQuery.insert(Person("Michelle", "Streeter"))
    _ <- PersonQuery.insert(Person("Johnathon", "Martinez"))
    users <- PersonQuery.list
    _ <- putStrLn(users.toString)
  } yield ()

  val myApp: ZIO[zio.ZEnv, Throwable, Unit] =
    Database.transactionOrWidenR(query).provideCustom(services.database)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}

case class Person(firstName: String, lastName: String)

object PersonQuery {
  def list: TranzactIO[List[Person]] = tzio {
    sql"""SELECT first_name, last_name FROM person""".query[Person].to[List]
  }

  def setup: TranzactIO[Unit] = tzio {
    sql"""
        CREATE TABLE person (
          first_name VARCHAR NOT NULL,
          last_name VARCHAR NOT NULL
        )
        """.update.run.map(_ => ())
  }

  def insert(p: Person): TranzactIO[Unit] = tzio {
    sql"""INSERT INTO person (first_name, last_name) VALUES (${p.firstName}, ${p.lastName})""".update.run
      .map(_ => ())
  }
}

object services {
  val datasource: ZLayer[Blocking, Throwable, Has[DataSource]] =
    ZLayer.fromEffect(
      blocking.effectBlocking {
        val ds = new JdbcDataSource
        ds.setURL(s"jdbc:h2:mem:mydb;DB_CLOSE_DELAY=10")
        ds.setUser("sa")
        ds.setPassword("sa")
        ds
      }
    )

  val database: ZLayer[Any, Throwable, doobie.Database.Database] =
    (Blocking.live >>> datasource ++ Blocking.live ++ Clock.live) >>> Database.fromDatasource
}
```
