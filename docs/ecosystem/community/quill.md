---
id: quill
title: "Quill"
---

[Quill](https://github.com/getquill/quill) is a Compile-time Language Integrated Queries for Scala.

## Introduction

Quill allows us to create SQL out of a Scala code during the **compile-time**. It provides the _Quoted Domain Specific Language (QDSL)_ to express queries in Scala and execute them in a target language.

- **Boilerplate-free mapping** — The database schema is mapped using simple case classes.
- **Quoted DSL** — Queries are defined inside a quote block. Quill parses each quoted block of code (quotation) at compile-time and translates them to an internal Abstract Syntax Tree (AST)
- **Compile-time query generation** — The `ctx.run` call reads the quotation’s AST and translates it to the target language at compile-time, emitting the query string as a compilation message. As the query string is known at compile-time, the runtime overhead is very low and similar to using the database driver directly.
- **Compile-time query validation** — If configured, the query is verified against the database at compile-time and the compilation fails if it is not valid. The query validation does not alter the database state.

## Installation

In order to use this library with ZIO, we need to add the following lines in our `build.sbt` file:

```scala
// Provides Quill contexts for ZIO.
libraryDependencies += "io.getquill" %% "quill-zio" % "3.9.0"

// Provides Quill context that execute MySQL, PostgreSQL, SQLite, H2, SQL Server and Oracle queries inside of ZIO.
libraryDependencies += "io.getquill" %% "quill-jdbc-zio" % "3.9.0" 

// Provides Quill context that executes Cassandra queries inside of ZIO.
libraryDependencies += "io.getquill" %% "quill-cassandra-zio" % "3.9.0"
```

## Example

First, to run this example, we should create the `Person` table at the database initialization. Let's put the following lines into the `h2-schema.sql` file at the`src/main/resources` path:

```sql
CREATE TABLE IF NOT EXISTS Person(
    name VARCHAR(255),
    age int
);
```

In this example, we use in-memory database as our data source. So we just put these lines into the `application.conf` at the `src/main/resources` path:

```hocon
myH2DB {
  dataSourceClassName = org.h2.jdbcx.JdbcDataSource
  dataSource {
    url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:h2-schema.sql'"
    user = sa
  }
}
```

Now we are ready to run the example below:

```scala
import io.getquill._
import io.getquill.context.ZioJdbc._
import zio.console.{Console, putStrLn}
import zio.{ExitCode, Has, URIO, ZIO}

import java.io.Closeable
import javax.sql

object QuillZIOExample extends zio.App {
  val ctx = new H2ZioJdbcContext(Literal)

  import ctx._

  case class Person(name: String, age: Int)

  val myApp: ZIO[Console with Has[sql.DataSource with Closeable], Exception, Unit] =
    for {
      _ <- ctx.run(
        quote {
          liftQuery(List(Person("Alex", 25), Person("Sarah", 23)))
            .foreach(r =>
              query[Person].insert(r)
            )
        }
      ).onDS
      result <- ctx.run(
        quote(query[Person].filter(p => p.name == "Sarah"))
      ).onDS
      _ <- putStrLn(result.toString)
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp
      .provideCustom(DataSourceLayer.fromPrefix("myH2DB"))
      .exitCode
}
